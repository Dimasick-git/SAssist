import { WebSocket } from "ws";

const WSURL = process.env.URL || "ws://127.0.0.1:8080";
const ws = new WebSocket(WSURL);
let got = 0;

ws.on("open", () => {
  console.log("[smoke] connected ->", WSURL);
  ws.send(JSON.stringify({ type: "join", username: "smoke-bot" }));
  setTimeout(() => {
    ws.send(JSON.stringify({ type: "send", channel: "general", text: "hello from smoke" }));
  }, 200);
});

ws.on("message", (raw) => {
  const m = JSON.parse(raw.toString());
  console.log("[smoke] recv:", JSON.stringify(m).slice(0, 200));
  if (m.type === "message" && m.message.username === "smoke-bot") {
    console.log("[smoke] ROUND-TRIP OK");
    got = 1;
    ws.close();
  }
});

ws.on("close", () => { console.log("[smoke] closed"); process.exit(got ? 0 : 1); });
ws.on("error", (e) => { console.error("[smoke] error", e); process.exit(1); });
setTimeout(() => { console.error("[smoke] TIMEOUT"); process.exit(1); }, 5000);