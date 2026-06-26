import http from "http";
import { WebSocketServer, WebSocket } from "ws";
import { DEFAULT_CHANNELS, ChatMessage, ServerMsg, parseClientMsg } from "./protocol";
import { requestOtp, verifyOtp, upsertUser, signToken, userForToken } from "./auth";
import { sendCode } from "./notify";

const PORT = Number(process.env.PORT) || 8080;
const HISTORY_LIMIT = 100;

interface Client { id: string; username: string; channel: string; ws: WebSocket; }
const clients = new Map<WebSocket, Client>();
const channels = new Set<string>(DEFAULT_CHANNELS);
const history = new Map<string, ChatMessage[]>();
for (const ch of channels) history.set(ch, []);

let seq = 0;
function newId(p: string): string { return p + "_" + Date.now().toString(36) + "_" + (seq++).toString(36); }

// ---- naive in-memory rate limiter (anti brute-force) ----
const rl = new Map<string, { count: number; ts: number }>();
function rateLimit(key: string, max: number, windowMs: number): boolean {
  const now = Date.now();
  const e = rl.get(key);
  if (!e || now - e.ts > windowMs) { rl.set(key, { count: 1, ts: now }); return true; }
  e.count++;
  return e.count <= max;
}
function clientIp(req: http.IncomingMessage): string {
  const xf = req.headers["x-forwarded-for"];
  if (typeof xf === "string" && xf.length) return xf.split(",")[0].trim();
  return req.socket.remoteAddress || "unknown";
}

function send(ws: WebSocket, msg: ServerMsg) { if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(msg)); }
function usersIn(channel: string): string[] {
  const out: string[] = [];
  for (const c of clients.values()) if (c.channel === channel) out.push(c.username);
  return out.sort();
}
function broadcastChannel(channel: string, msg: ServerMsg) {
  for (const c of clients.values()) if (c.channel === channel) send(c.ws, msg);
}
function broadcastPresence(channel: string) { broadcastChannel(channel, { type: "presence", channel, users: usersIn(channel) }); }

function readBody(req: http.IncomingMessage): Promise<any> {
  return new Promise((resolve) => {
    let data = "";
    req.on("data", (c) => { data += c; if (data.length > 1e6) req.destroy(); });
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
  secHeaders(res);
  res.writeHead(code, { "Content-Type": "application/json" });
  res.end(JSON.stringify(obj));
}

const server = http.createServer(async (req, res) => {
  if (req.method === "OPTIONS") {
    secHeaders(res);
    res.writeHead(204, { "Access-Control-Allow-Methods": "POST, GET, OPTIONS", "Access-Control-Allow-Headers": "Content-Type" });
    res.end(); return;
  }
  if (req.method === "GET" && (req.url === "/" || req.url === "/health")) {
    secHeaders(res); res.writeHead(200, { "Content-Type": "text/plain" }); res.end("SAssist server ok"); return;
  }
  if (req.method === "POST" && req.url === "/auth/request") {
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
  if (req.method === "POST" && req.url === "/auth/verify") {
    const b = await readBody(req);
    const method = b.method === "phone" ? "phone" : "email";
    const identifier = ("" + (b.identifier || "")).trim().toLowerCase();
    const code = ("" + (b.code || "")).trim();
    const username = ("" + (b.username || "")).trim();
    if (!rateLimit("vrf:" + clientIp(req) + ":" + identifier, 10, 10 * 60 * 1000)) { sendJson(res, 429, { ok: false, error: "too many attempts, try later" }); return; }
    if (!verifyOtp(identifier, code)) { sendJson(res, 401, { ok: false, error: "invalid or expired code" }); return; }
    const user = upsertUser(method, identifier, username);
    const token = signToken(user.id);
    sendJson(res, 200, { ok: true, token, user: { id: user.id, username: user.username, identifier: user.identifier } });
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
        const c: Client = { id: user.id, username: user.username, channel: "general", ws };
        clients.set(ws, c);
        send(ws, { type: "welcome", userId: c.id, username: c.username, channels: [...channels] });
        send(ws, { type: "history", channel: c.channel, messages: history.get(c.channel) || [] });
        broadcastPresence(c.channel);
        break;
      }
      case "listChannels": { send(ws, { type: "channels", channels: [...channels] }); break; }
      case "switchChannel": {
        if (!client) { send(ws, { type: "error", reason: "join first" }); break; }
        const target = msg.channel;
        if (!channels.has(target)) { send(ws, { type: "error", reason: "no such channel" }); break; }
        const prev = client.channel; client.channel = target;
        send(ws, { type: "history", channel: target, messages: history.get(target) || [] });
        broadcastPresence(prev); broadcastPresence(target);
        break;
      }
      case "send": {
        if (!client) { send(ws, { type: "error", reason: "join first" }); break; }
        const channel = msg.channel || client.channel;
        if (!channels.has(channel)) { send(ws, { type: "error", reason: "no such channel" }); break; }
        const text = (msg.text || "").slice(0, 8000);
        if (!text.trim()) break;
        const message: ChatMessage = { id: newId("m"), channel, username: client.username, text, ts: Date.now() };
        const buf = history.get(channel)!;
        buf.push(message);
        if (buf.length > HISTORY_LIMIT) buf.splice(0, buf.length - HISTORY_LIMIT);
        broadcastChannel(channel, { type: "message", message });
        break;
      }
    }
  });
  ws.on("close", () => { const c = clients.get(ws); clients.delete(ws); if (c) broadcastPresence(c.channel); });
});

server.listen(PORT, () => { console.log("SAssist server listening on :" + PORT + " (channels: " + [...channels].join(", ") + ")"); });
