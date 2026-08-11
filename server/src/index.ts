import http from "http";
import fs from "fs";
import path from "path";
import { WebSocketServer, WebSocket } from "ws";
import { DEFAULT_CHANNELS, ChatMessage, ServerMsg, PublicUser, MediaRef, parseClientMsg } from "./protocol";
import { requestOtp, verifyOtp, login, signToken, userForToken, claimHandle, handleStatus, updateProfile, claimPremium, getUser, toPublic } from "./auth";
import { sendCode } from "./notify";
import * as db from "./db";

const PORT = Number(process.env.PORT) || 8080;
// Some hosts (e.g. alwaysdata) require binding to a specific IP rather than
// all interfaces. HOST unset -> listen on 0.0.0.0 (Docker, VPS, local).
const HOST = process.env.HOST || process.env.ALWAYSDATA_HTTPD_IP || undefined;
const HISTORY_LIMIT = 100;
const DATA_DIR = process.env.DATA_DIR || path.join(process.cwd(), "data");
const MEDIA_DIR = path.join(DATA_DIR, "media");
const MEDIA_MAX = 30 * 1024 * 1024; // 30 MB
try { fs.mkdirSync(MEDIA_DIR, { recursive: true }); } catch (e) { /* ignore */ }

interface Client { id: string; ws: WebSocket; channel: string; }
const clients = new Map<WebSocket, Client>();
const channels = new Set<string>(DEFAULT_CHANNELS);

let seq = 0;
function newId(p: string): string { return p + "_" + Date.now().toString(36) + "_" + (seq++).toString(36); }

const rl = new Map<string, { count: number; ts: number }>();
function rateLimit(key: string, max: number, windowMs: number): boolean {
  const now = Date.now();
  const e = rl.get(key);
  if (!e || now - e.ts > windowMs) { rl.set(key, { count: 1, ts: now }); return true; }
  e.count++; return e.count <= max;
}
function clientIp(req: http.IncomingMessage): string {
  const xf = req.headers["x-forwarded-for"];
  if (typeof xf === "string" && xf.length) return xf.split(",")[0].trim();
  return req.socket.remoteAddress || "unknown";
}

function send(ws: WebSocket, msg: ServerMsg) { if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(msg)); }
function publicOf(id: string): PublicUser {
  const u = getUser(id);
  if (u) return toPublic(u);
  return { id, displayName: "user", handle: "", premium: false, color: "5865F2" };
}
function usersIn(channel: string): PublicUser[] {
  const seen = new Set<string>(); const out: PublicUser[] = [];
  for (const c of clients.values()) if (c.channel === channel && !seen.has(c.id)) { seen.add(c.id); out.push(publicOf(c.id)); }
  return out.sort((a, b) => a.displayName.localeCompare(b.displayName));
}
function availableChannels(userId: string): string[] { return [...channels, ...db.directChannelsForUser(userId)]; }
function canAccessChannel(userId: string, channel: string): boolean { return channels.has(channel) || db.isDirectChannelMember(channel, userId); }
function sendChannelList(userId: string) {
  const payload: ServerMsg = { type: "channels", channels: availableChannels(userId) };
  for (const c of clients.values()) if (c.id === userId) send(c.ws, payload);
}
function broadcastChannel(channel: string, msg: ServerMsg, exceptWs?: WebSocket) {
  for (const c of clients.values()) if (c.channel === channel && c.ws !== exceptWs) send(c.ws, msg);
}
function broadcastPresence(channel: string) { broadcastChannel(channel, { type: "presence", channel, users: usersIn(channel) }); }

function readBody(req: http.IncomingMessage, cap = 1e6): Promise<any> {
  return new Promise((resolve) => {
    let data = ""; let size = 0;
    req.on("data", (c) => { size += c.length; if (size > cap) { req.destroy(); return; } data += c; });
    req.on("end", () => { try { resolve(JSON.parse(data || "{}")); } catch (e) { resolve({}); } });
  });
}
function secHeaders(res: http.ServerResponse) {
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.setHeader("X-Frame-Options", "DENY");
  res.setHeader("Referrer-Policy", "no-referrer");
  res.setHeader("Access-Control-Allow-Origin", "*");
}
function sendJson(res: http.ServerResponse, code: number, obj: any) {
  secHeaders(res); res.writeHead(code, { "Content-Type": "application/json" }); res.end(JSON.stringify(obj));
}
function tokenFrom(req: http.IncomingMessage, b: any): string {
  const h = req.headers["authorization"];
  if (typeof h === "string" && h.startsWith("Bearer ")) return h.slice(7);
  return ("" + ((b && b.token) || "")).trim();
}

const server = http.createServer(async (req, res) => {
  const url = (req.url || "").split("?")[0];
  if (req.method === "OPTIONS") {
    secHeaders(res);
    res.writeHead(204, { "Access-Control-Allow-Methods": "POST, GET, OPTIONS", "Access-Control-Allow-Headers": "Content-Type, Authorization" });
    res.end(); return;
  }
  if (req.method === "GET" && (url === "/" || url === "/health")) {
    secHeaders(res); res.writeHead(200, { "Content-Type": "text/plain" }); res.end("SAssist server ok"); return;
  }

  // ---- auth ----
  if (req.method === "POST" && url === "/auth/request") {
    const b = await readBody(req);
    const method = b.method === "phone" ? "phone" : "email";
    const identifier = ("" + (b.identifier || "")).trim().toLowerCase();
    if (!identifier) { sendJson(res, 400, { ok: false, error: "identifier required" }); return; }
    if (!rateLimit("req:" + clientIp(req) + ":" + identifier, 5, 10 * 60 * 1000)) { sendJson(res, 429, { ok: false, error: "too many requests, try later" }); return; }
    const code = requestOtp(identifier);
    const r = await sendCode(method, identifier, code);
    sendJson(res, 200, { ok: true, delivered: r.delivered, devCode: r.devCode });
    return;
  }
  if (req.method === "POST" && url === "/auth/verify") {
    const b = await readBody(req);
    const method = b.method === "phone" ? "phone" : "email";
    const identifier = ("" + (b.identifier || "")).trim().toLowerCase();
    const code = ("" + (b.code || "")).trim();
    const displayName = ("" + (b.displayName || b.username || "")).trim();
    if (!rateLimit("vrf:" + clientIp(req) + ":" + identifier, 10, 10 * 60 * 1000)) { sendJson(res, 429, { ok: false, error: "too many attempts, try later" }); return; }
    if (!verifyOtp(identifier, code)) { sendJson(res, 401, { ok: false, error: "invalid or expired code" }); return; }
    const user = login(method, identifier, displayName);
    const token = signToken(user.id);
    const pub = toPublic(user);
    sendJson(res, 200, { ok: true, token, user: { ...pub, username: pub.displayName, identifier: user.identifier } });
    return;
  }

  // ---- handle (premium @username) ----
  if (req.method === "GET" && url === "/handle/check") {
    const q = new URLSearchParams((req.url || "").split("?")[1] || "");
    sendJson(res, 200, { ...handleStatus(q.get("handle") || "") });
    return;
  }
  if (req.method === "POST" && url === "/handle/claim") {
    const b = await readBody(req);
    const u = userForToken(tokenFrom(req, b));
    if (!u) { sendJson(res, 401, { ok: false, error: "auth required" }); return; }
    const r = claimHandle(u.id, "" + (b.handle || ""));
    if (!r.ok) { sendJson(res, 409, { ok: false, error: r.error }); return; }
    sendJson(res, 200, { ok: true, user: toPublic(r.user!) });
    return;
  }

  // ---- profile ----
  if (req.method === "GET" && url === "/profile") {
    const q = new URLSearchParams((req.url || "").split("?")[1] || "");
    const u = userForToken(q.get("token") || tokenFrom(req, {}));
    if (!u) { sendJson(res, 401, { ok: false, error: "auth required" }); return; }
    sendJson(res, 200, { ok: true, user: toPublic(u) });
    return;
  }
  if (req.method === "GET" && url.startsWith("/users/")) {
    const requester = userForToken(tokenFrom(req, {}));
    if (!requester) { sendJson(res, 401, { ok: false, error: "auth required" }); return; }
    const id = url.slice("/users/".length).replace(/[^A-Za-z0-9_]/g, "");
    const target = getUser(id);
    if (!target) { sendJson(res, 404, { ok: false, error: "user not found" }); return; }
    sendJson(res, 200, { ok: true, user: toPublic(target) });
    return;
  }
  if (req.method === "POST" && url === "/profile") {
    const b = await readBody(req);
    const u = userForToken(tokenFrom(req, b));
    if (!u) { sendJson(res, 401, { ok: false, error: "auth required" }); return; }
    const r = updateProfile(u.id, { displayName: b.displayName, bio: b.bio, color: b.color, avatar: b.avatar, banner: b.banner });
    sendJson(res, 200, { ok: true, user: toPublic(r.user!) });
    return;
  }
  if (req.method === "POST" && url === "/premium/claim") {
    const b = await readBody(req);
    const u = userForToken(tokenFrom(req, b));
    if (!u) { sendJson(res, 401, { ok: false, error: "auth required" }); return; }
    const r = claimPremium(u.id, "" + (b.code || ""));
    if (!r.ok) { sendJson(res, 402, { ok: false, error: r.error }); return; }
    sendJson(res, 200, { ok: true, user: toPublic(r.user!) });
    return;
  }

  // ---- media upload / download (photos, videos, files) ----
  if (req.method === "POST" && url === "/upload") {
    const b = await readBody(req, MEDIA_MAX + 2 * 1024 * 1024);
    const u = userForToken(tokenFrom(req, b));
    if (!u) { sendJson(res, 401, { ok: false, error: "auth required" }); return; }
    const data = "" + (b.dataBase64 || "");
    if (!data) { sendJson(res, 400, { ok: false, error: "no data" }); return; }
    let buf: Buffer;
    try { buf = Buffer.from(data, "base64"); } catch (e) { sendJson(res, 400, { ok: false, error: "bad base64" }); return; }
    if (buf.length > MEDIA_MAX) { sendJson(res, 413, { ok: false, error: "file too large (max 30MB)" }); return; }
    const kind = (b.kind === "video" || b.kind === "audio" || b.kind === "file") ? b.kind : "image";
    const mime = ("" + (b.mime || "application/octet-stream")).slice(0, 100);
    const name = ("" + (b.name || "file")).slice(0, 120);
    const durationMs = Number(b.durationMs) > 0 ? Number(b.durationMs) : undefined;
    const id = newId("md");
    fs.writeFileSync(path.join(MEDIA_DIR, id + ".bin"), buf);
    fs.writeFileSync(path.join(MEDIA_DIR, id + ".json"), JSON.stringify({ id, kind, mime, name, size: buf.length, owner: u.id, ts: Date.now() }));
    const media: MediaRef = { id, kind, mime, name, size: buf.length, width: b.width, height: b.height, durationMs };
    sendJson(res, 200, { ok: true, media, url: "/media/" + id });
    return;
  }
  if (req.method === "GET" && url.startsWith("/media/")) {
    const id = url.slice("/media/".length).replace(/[^a-zA-Z0-9_]/g, "");
    const metaPath = path.join(MEDIA_DIR, id + ".json");
    const binPath = path.join(MEDIA_DIR, id + ".bin");
    if (!fs.existsSync(metaPath) || !fs.existsSync(binPath)) { secHeaders(res); res.writeHead(404); res.end(); return; }
    const meta = JSON.parse(fs.readFileSync(metaPath, "utf8"));
    secHeaders(res);
    res.writeHead(200, { "Content-Type": meta.mime || "application/octet-stream", "Content-Length": meta.size, "Cache-Control": "public, max-age=31536000" });
    fs.createReadStream(binPath).pipe(res);
    return;
  }

  secHeaders(res); res.writeHead(404); res.end();
});

const wss = new WebSocketServer({ server });
wss.on("connection", (ws) => {
  ws.on("message", (raw) => {
    const msg = parseClientMsg(raw.toString());
    if (!msg) { send(ws, { type: "error", reason: "bad message" }); return; }
    const client = clients.get(ws);
    switch (msg.type) {
      case "join": {
        const user = userForToken((msg as any).token || "");
        if (!user) { send(ws, { type: "error", reason: "auth required" }); ws.close(); break; }
        const c: Client = { id: user.id, ws, channel: "general" };
        clients.set(ws, c);
        const pub = toPublic(user);
        send(ws, { type: "welcome", user: pub, userId: pub.id, username: pub.displayName, channels: availableChannels(user.id) });
        send(ws, { type: "history", channel: c.channel, messages: db.getHistory(c.channel, HISTORY_LIMIT) });
        broadcastPresence(c.channel);
        break;
      }
      case "listChannels": { if (client) send(ws, { type: "channels", channels: availableChannels(client.id) }); break; }
      case "switchChannel": {
        if (!client) { send(ws, { type: "error", reason: "join first" }); break; }
        const target = (msg as any).channel;
        if (!canAccessChannel(client.id, target)) { send(ws, { type: "error", reason: "no such channel" }); break; }
        const prev = client.channel; client.channel = target;
        send(ws, { type: "history", channel: target, messages: db.getHistory(target, HISTORY_LIMIT) });
        broadcastPresence(prev); broadcastPresence(target);
        break;
      }
      case "history": {
        if (!client) { send(ws, { type: "error", reason: "join first" }); break; }
        const channel = "" + ((msg as any).channel || client.channel);
        if (!canAccessChannel(client.id, channel)) { send(ws, { type: "error", reason: "no such channel" }); break; }
        const sinceRaw = Number((msg as any).since);
        const since = Number.isFinite(sinceRaw) && sinceRaw > 0 ? sinceRaw : undefined;
        const limitRaw = Number((msg as any).limit);
        const limit = Number.isFinite(limitRaw) && limitRaw > 0 ? limitRaw : (since !== undefined ? 200 : HISTORY_LIMIT);
        send(ws, { type: "history", channel, messages: db.getHistory(channel, limit, since), since });
        break;
      }
      case "typing": {
        if (!client) break;
        const channel = (msg as any).channel || client.channel;
        if (!canAccessChannel(client.id, channel)) break;
        broadcastChannel(channel, { type: "typing", channel, user: publicOf(client.id) }, ws);
        break;
      }
      case "react": {
        if (!client) break;
        const channel = (msg as any).channel || client.channel;
        if (!canAccessChannel(client.id, channel)) break;
        const m = db.getMessageById("" + (msg as any).messageId);
        if (!m || m.channel !== channel) break;
        const emoji = ("" + (msg as any).emoji).slice(0, 8);
        const reactions = m.reactions || {};
        const arr = reactions[emoji] || [];
        const i = arr.indexOf(client.id);
        if (i >= 0) arr.splice(i, 1); else arr.push(client.id);
        if (arr.length) reactions[emoji] = arr; else delete reactions[emoji];
        db.updateReactions(m.id, reactions);
        broadcastChannel(channel, { type: "reaction", channel, messageId: m.id, reactions });
        break;
      }
      case "send": {
        if (!client) { send(ws, { type: "error", reason: "join first" }); break; }
        const channel = (msg as any).channel || client.channel;
        if (!canAccessChannel(client.id, channel)) { send(ws, { type: "error", reason: "no such channel" }); break; }
        const text = ((msg as any).text || "").slice(0, 8000);
        const media: MediaRef | undefined = (msg as any).media;
        if (!text.trim() && !media) break;
        const clientId = ("" + ((msg as any).clientId || "")).slice(0, 64) || undefined;
        const pub = publicOf(client.id);
        const message: ChatMessage = {
          id: newId("m"), channel, userId: pub.id, username: pub.displayName,
          handle: pub.handle, premium: pub.premium, color: pub.color,
          text, ts: Date.now(),
          media, replyTo: (msg as any).replyTo,
          secret: !!(msg as any).secret, ttl: (msg as any).ttl
        };
        // Secret messages are ephemeral: delivered live, never stored.
        if (!message.secret) {
          if (db.saveMessage(message, clientId) === "duplicate") {
            // Offline-queue re-send whose original echo was lost: don't store
            // or re-broadcast, just re-echo the stored copy to the sender so
            // it can reconcile its pending row.
            const existing = clientId && db.findByClientId(client.id, clientId);
            if (existing) send(ws, { type: "message", message: { ...existing, clientId } });
            break;
          }
        }
        // Every socket owned by the sender (this app + its background worker +
        // any other device signed in as the same user) gets the clientId so it
        // can reconcile its optimistic copy; everyone else gets it plain. This
        // is what prevents duplicate bubbles across multiple sockets.
        for (const c of clients.values()) {
          if (c.channel !== channel) continue;
          const own = c.id === message.userId;
          send(c.ws, { type: "message", message: own && clientId ? { ...message, clientId } : message });
        }
        break;
      }
      case "read": {
        if (!client) break;
        const channel = (msg as any).channel || client.channel;
        if (!canAccessChannel(client.id, channel)) break;
        const ids: string[] = Array.isArray((msg as any).messageIds) ? (msg as any).messageIds.slice(0, 500) : [];
        for (const mid of ids) {
          if (db.markRead("" + mid, client.id)) {
            broadcastChannel(channel, { type: "read", channel, messageId: "" + mid, userId: client.id, user: publicOf(client.id) });
          }
        }
        break;
      }
      case "startDm": {
        if (!client) { send(ws, { type: "error", reason: "join first" }); break; }
        const peerId = ("" + (msg as any).userId).replace(/[^A-Za-z0-9_]/g, "");
        const peer = getUser(peerId);
        if (!peer || peerId === client.id) { send(ws, { type: "error", reason: "user not found" }); break; }
        const channel = db.createDirectChannel(client.id, peerId);
        sendChannelList(client.id);
        sendChannelList(peerId);
        send(ws, { type: "dmStarted", channel, user: toPublic(peer) });
        break;
      }
    }
  });
  ws.on("close", () => { const c = clients.get(ws); clients.delete(ws); if (c) broadcastPresence(c.channel); });
});

const onListen = () => console.log("SAssist server listening on " + (HOST || "0.0.0.0") + ":" + PORT + " (channels: " + [...channels].join(", ") + ")");
if (HOST) server.listen(PORT, HOST, onListen); else server.listen(PORT, onListen);
