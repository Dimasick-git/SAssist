import http from "http";
import { WebSocketServer, WebSocket } from "ws";
import { DEFAULT_CHANNELS, ChatMessage, ServerMsg, parseClientMsg } from "./protocol";
const PORT = Number(process.env.PORT) || 8080;
const HISTORY_LIMIT = 100;

interface Client { id: string; username: string; channel: string; ws: WebSocket; }

const clients = new Map<WebSocket, Client>();
const channels = new Set<string>(DEFAULT_CHANNELS);
const history = new Map<string, ChatMessage[]>();
for (const ch of channels) history.set(ch, []);

let seq = 0;
const newId = (p: string) => `${p}_${Date.now().toString(36)}_${(seq++).toString(36)}`;

function send(ws: WebSocket, msg: ServerMsg) {
  if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(msg));
}
function usersIn(channel: string): string[] {
  const out: string[] = [];
  for (const c of clients.values()) if (c.channel === channel) out.push(c.username);
  return out.sort();
}
function broadcastChannel(channel: string, msg: ServerMsg) {
  for (const c of clients.values()) if (c.channel === channel) send(c.ws, msg);
}
function broadcastPresence(channel: string) {
  broadcastChannel(channel, { type: "presence", channel, users: usersIn(channel) });
}

const server = http.createServer((req, res) => {
  if (req.method === "GET" && (req.url === "/" || req.url === "/health")) {
    res.writeHead(200, { "Content-Type": "text/plain" });
    res.end("SAssist server ok");
    return;
  }
  res.writeHead(404); res.end();
});

const wss = new WebSocketServer({ server });

wss.on("connection", (ws) => {
  ws.on("message", (raw) => {
    const msg = parseClientMsg(raw.toString());
    if (!msg) { send(ws, { type: "error", reason: "bad message" }); return; }
    const client = clients.get(ws);
    switch (msg.type) {
      case "join": {
        const username = (msg.username || "anon").slice(0, 32).trim() || "anon";
        const c: Client = { id: newId("u"), username, channel: "general", ws };
        clients.set(ws, c);
        send(ws, { type: "welcome", userId: c.id, username: c.username, channels: [...channels] });
        send(ws, { type: "history", channel: c.channel, messages: history.get(c.channel) || [] });
        broadcastPresence(c.channel);
        break;
      }
      case "listChannels": {
        send(ws, { type: "channels", channels: [...channels] });
        break;
      }
      case "switchChannel": {
        if (!client) { send(ws, { type: "error", reason: "join first" }); break; }
        const target = msg.channel;
        if (!channels.has(target)) { send(ws, { type: "error", reason: "no such channel" }); break; }
        const prev = client.channel;
        client.channel = target;
        send(ws, { type: "history", channel: target, messages: history.get(target) || [] });
        broadcastPresence(prev);
        broadcastPresence(target);
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
  ws.on("close", () => {
    const c = clients.get(ws);
    clients.delete(ws);
    if (c) broadcastPresence(c.channel);
  });
});

server.listen(PORT, () => {
  console.log(`SAssist server listening on :${PORT} (channels: ${[...channels].join(", ")})`);
});