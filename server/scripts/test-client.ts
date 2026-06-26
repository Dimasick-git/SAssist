import { WebSocket } from "ws";

const BASE = process.env.BASE || "http://127.0.0.1:8080";
const WSURL = process.env.WSURL || "ws://127.0.0.1:8080";

async function main() {
  const f: any = (globalThis as any).fetch;
  const r1 = await f(BASE + "/auth/request", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ method: "email", identifier: "smoke@test.dev" }) });
  const j1 = await r1.json();
  console.log("[smoke] request:", JSON.stringify(j1));
  const code = j1.devCode;
  const r2 = await f(BASE + "/auth/verify", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ method: "email", identifier: "smoke@test.dev", code, username: "smoke-bot" }) });
  const j2 = await r2.json();
  console.log("[smoke] verify:", JSON.stringify(j2));
  const token = j2.token;
  if (!token) { console.error("[smoke] NO TOKEN"); process.exit(1); }
  const ws = new WebSocket(WSURL);
  let got = 0;
  ws.on("open", () => {
    ws.send(JSON.stringify({ type: "join", token }));
    setTimeout(() => ws.send(JSON.stringify({ type: "send", channel: "general", text: "hello from smoke" })), 200);
  });
  ws.on("message", (raw) => {
    const m = JSON.parse(raw.toString());
    console.log("[smoke] recv:", JSON.stringify(m).slice(0, 160));
    if (m.type === "message" && m.message.username === "smoke-bot") { console.log("[smoke] ROUND-TRIP OK"); got = 1; ws.close(); }
  });
  ws.on("close", () => { console.log("[smoke] closed"); process.exit(got ? 0 : 1); });
  ws.on("error", (e) => { console.error("[smoke] error", e); process.exit(1); });
  setTimeout(() => { console.error("[smoke] TIMEOUT"); process.exit(1); }, 6000);
}
main();
