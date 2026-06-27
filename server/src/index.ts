import http from "http";
import fs from "fs";
import path from "path";
import { WebSocketServer, WebSocket } from "ws";
import { DEFAULT_CHANNELS, ChatMessage, ServerMsg, PublicUser, MediaRef, parseClientMsg } from "./protocol";
import { requestOtp, verifyOtp, login, signToken, userForToken, claimHandle, handleStatus, updateProfile, claimPremium, getUser, toPublic } from "./auth";
import { sendCode } from "./notify";

const PORT = Number(process.env.PORT) || 8080;
const HISTORY_LIMIT = 100;
const DATA_DIR = process.env.DATA_DIR || path.join(process.cwd(), "data");
const MEDIA_DIR = path.join(DATA_DIR, "media");
const MEDIA_MAX = 30 * 1024 * 1024; // 30 MB
try { fs.mkdirSync(MEDIA_DIR, { recursive: true }); } catch (e) { /* ignore */ }

interface Client { id: string; ws: WebSocket; channel: string; }
const clients = new Map<WebSocket, Client>();
const channels = new Set<string>(DEFAULT_CHANNELS);
const history = new Map<string, ChatMessage[]>();
for (const ch of channels) history.set(ch, []);

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
  if (req.method === "POST" && url === "/profile") {
    const b = await readBody(req);
    const u = userForToken(tokenFrom(req, b));
    if (!u) { sendJson(res, 401, { ok: false, error: "auth required" }); return; }
    const r = updateProfile(u.id, { displayName: b.displayName, bio: b.bio, color: b.color });
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
    const kind = (b.kind === "video" || b.kind === "file") ? b.kind : "image";
    const mime = ("" + (b.mime || "application/octet-stream")).slice(0, 100);
    const name = ("" + (b.name || "file")).slice(0, 120);
    const id = newId("md");
    fs.writeFileSync(path.join(MEDIA_DIR, id + ".bin"), buf);
    fs.writeFileSync(path.join(MEDIA_DIR, id + ".json"), JSON.stringify({ id, kind, mime, name, size: buf.length, owner: u.id, ts: Date.now() }));
    const media: MediaRef = { id, kind, mime, name, size: buf.length, width: b.width, height: b.height };
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
        send(ws, { type: "welcome", user: pub, userId: pub.id, username: pub.displayName, channels: [...channels] });
        send(ws, { type: "history", channel: c.channel, messages: history.get(c.channel) || [] });
        broadcastPresence(c.channel);
        break;
      }
      case "listChannels": { send(ws, { type: "channels", channels: [...channels] }); break; }
      case "switchChannel": {
        if (!client) { send(ws, { type: "error", reason: "join first" }); break; }
        const target = (msg as any).channel;
        if (!channels.has(target)) { send(ws, { type: "error", reason: "no such channel" }); break; }
        const prev = client.channel; client.channel = target;
        send(ws, { type: "history", channel: target, messages: history.get(target) || [] });
        broadcastPresence(prev); broadcastPresence(target);
        break;
      }
      case "typing": {
        if (!client) break;
        const channel = (msg as any).channel || client.channel;
        broadcastChannel(channel, { type: "typing", channel, user: publicOf(client.id) }, ws);
        break;
      }
      case "react": {
        if (!client) break;
        const channel = (msg as any).channel || client.channel;
        const buf = history.get(channel); if (!buf) break;
        const m = buf.find((x) => x.id === (msg as any).messageId); if (!m) break;
        const emoji = ("" + (msg as any).emoji).slice(0, 8);
        if (!m.reactions) m.reactions = {};
        const arr = m.reactions[emoji] || [];
        const i = arr.indexOf(client.id);
        if (i >= 0) arr.splice(i, 1); else arr.push(client.id);
        if (arr.length) m.reactions[emoji] = arr; else delete m.reactions[emoji];
        broadcastChannel(channel, { type: "reaction", channel, messageId: m.id, reactions: m.reactions });
        break;
      }
      case "send": {
        if (!client) { send(ws, { type: "error", reason: "join first" }); break; }
        const channel = (msg as any).channel || client.channel;
        if (!channels.has(channel)) { send(ws, { type: "error", reason: "no such channel" }); break; }
        const text = ((msg as any).text || "").slice(0, 8000);
        const media: MediaRef | undefined = (msg as any).media;
        if (!text.trim() && !media) break;
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
          const buf = history.get(channel)!;
          buf.push(message);
          if (buf.length > HISTORY_LIMIT) buf.splice(0, buf.length - HISTORY_LIMIT);
        }
        broadcastChannel(channel, { type: "message", message });
        break;
      }
    }
  });
  ws.on("close", () => { const c = clients.get(ws); clients.delete(ws); if (c) broadcastPresence(c.channel); });
});

server.listen(PORT, () => { console.log("SAssist server listening on :" + PORT + " (channels: " + [...channels].join(", ") + ")"); });
