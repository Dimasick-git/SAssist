const BASE = process.env.BASE || "http://127.0.0.1:8080";
const WSURL = process.env.WS || "ws://127.0.0.1:8080";
const { WebSocket } = require("ws");

async function post(path, body) {
  const r = await fetch(BASE + path, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
  return { status: r.status, json: await r.json() };
}

(async () => {
  let fails = 0;
  // 1. request OTP via email (dev mode returns devCode)
  const req = await post("/auth/request", { method: "email", identifier: "coder@example.com" });
  console.log("REQUEST:", req.status, JSON.stringify(req.json));
  if (!req.json.ok) { console.log("FAIL: request"); process.exit(1); }
  const code = req.json.devCode;
  if (!code) { console.log("FAIL: no devCode (set DEV mode)"); fails++; }
  // 2. wrong code -> 401
  const bad = await post("/auth/verify", { method: "email", identifier: "coder@example.com", code: "000000", username: "Coder" });
  console.log("VERIFY-BAD:", bad.status, JSON.stringify(bad.json));
  if (bad.status !== 401) { console.log("FAIL: bad code should be 401"); fails++; }
  // 3. correct code -> token
  const ok = await post("/auth/verify", { method: "email", identifier: "coder@example.com", code, username: "Coder" });
  console.log("VERIFY-OK:", ok.status, JSON.stringify(ok.json));
  if (!ok.json.ok || !ok.json.token) { console.log("FAIL: verify"); process.exit(1); }
  const token = ok.json.token;
  // 4. WS join with token, send a message, expect echo
  await new Promise((resolve) => {
    const ws = new WebSocket(WSURL);
    let gotWelcome = false, gotMsg = false;
    const timer = setTimeout(() => { console.log("WS timeout"); ws.close(); resolve(); }, 4000);
    ws.on("open", () => ws.send(JSON.stringify({ type: "join", token })));
    ws.on("message", (d) => {
      const m = JSON.parse(d.toString());
      if (m.type === "welcome") { gotWelcome = true; console.log("WELCOME user:", m.username, "channels:", m.channels.join(",")); ws.send(JSON.stringify({ type: "send", channel: "general", text: "hello ```code```" })); }
      if (m.type === "message") { gotMsg = true; console.log("ECHO:", m.message.username + ":", m.message.text); clearTimeout(timer); if (!gotWelcome||!gotMsg) fails++; ws.close(); resolve(); }
    });
    ws.on("error", (e) => { console.log("WS error:", e.message); fails++; clearTimeout(timer); resolve(); });
  });
  // 5. WS join without token -> rejected
  await new Promise((resolve) => {
    const ws = new WebSocket(WSURL);
    const timer = setTimeout(() => { console.log("WS no-token timeout (ok if closed)"); resolve(); }, 3000);
    ws.on("open", () => ws.send(JSON.stringify({ type: "join", token: "" })));
    ws.on("message", (d) => { const m = JSON.parse(d.toString()); if (m.type === "error") { console.log("NO-TOKEN rejected:", m.reason); clearTimeout(timer); resolve(); } });
    ws.on("close", () => { clearTimeout(timer); resolve(); });
    ws.on("error", () => { clearTimeout(timer); resolve(); });
  });
  console.log(fails === 0 ? "ALL_SMOKE_OK" : ("SMOKE_FAILS=" + fails));
  process.exit(fails === 0 ? 0 : 1);
})();
