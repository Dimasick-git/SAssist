// SAssist smoke test.
//   node smoke.js                       -- full pass (auth, ws, clientId echo, dedupe)
//   SMOKE_PHASE=2 SMOKE_TOKEN=<t> node smoke.js
//       -- run after a server restart with the same DATA_DIR: verifies the
//          phase-1 marker message survived and the old token still validates
//          (i.e. messages + AUTH_SECRET are truly persistent).
const BASE = process.env.BASE || "http://127.0.0.1:8080";
const WSURL = process.env.WS || "ws://127.0.0.1:8080";
const PHASE = process.env.SMOKE_PHASE === "2" ? 2 : 1;
const MARKER = process.env.SMOKE_MARKER || "persist-check-marker";
const { WebSocket } = require("ws");

async function post(path, body, token) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers.Authorization = "Bearer " + token;
  const r = await fetch(BASE + path, { method: "POST", headers, body: JSON.stringify(body) });
  return { status: r.status, json: await r.json() };
}

async function loginFresh(identifier, username) {
  const req = await post("/auth/request", { method: "email", identifier });
  console.log("REQUEST:", req.status, JSON.stringify(req.json));
  if (!req.json.ok || !req.json.devCode) { console.log("FAIL: no devCode"); process.exit(1); }
  const ok = await post("/auth/verify", { method: "email", identifier, code: req.json.devCode, username });
  console.log("VERIFY-OK:", ok.status, JSON.stringify(ok.json));
  if (!ok.json.ok || !ok.json.token) { console.log("FAIL: verify"); process.exit(1); }
  return ok.json.token;
}

function wsSession(token, handler) {
  return new Promise((resolve) => {
    const ws = new WebSocket(WSURL);
    const timer = setTimeout(() => { console.log("WS timeout"); ws.close(); resolve(false); }, 6000);
    const done = (ok) => { clearTimeout(timer); try { ws.close(); } catch (e) {} resolve(ok); };
    ws.on("open", () => ws.send(JSON.stringify({ type: "join", token })));
    ws.on("message", (d) => handler(JSON.parse(d.toString()), ws, done));
    ws.on("error", (e) => { console.log("WS error:", e.message); done(false); });
  });
}

async function phase1() {
  let fails = 0;
  // wrong code -> 401
  const req = await post("/auth/request", { method: "email", identifier: "coder@example.com" });
  if (!req.json.ok || !req.json.devCode) { console.log("FAIL: request/devCode"); process.exit(1); }
  const bad = await post("/auth/verify", { method: "email", identifier: "coder@example.com", code: "000000", username: "Coder" });
  console.log("VERIFY-BAD:", bad.status);
  if (bad.status !== 401) { console.log("FAIL: bad code should be 401"); fails++; }
  const ok = await post("/auth/verify", { method: "email", identifier: "coder@example.com", code: req.json.devCode, username: "Coder" });
  if (!ok.json.ok || !ok.json.token) { console.log("FAIL: verify"); process.exit(1); }
  const token = ok.json.token;

  // Profile: avatar survives a GET, and short valid @username remains open to all.
  const avatar = "md_smoke_avatar";
  const profile = await post("/profile", { displayName: "Coder", bio: "profile smoke", color: "5865F2", avatar }, token);
  if (!profile.json.ok || profile.json.user.avatar !== avatar) { console.log("FAIL: profile avatar update"); fails++; }
  const shortHandle = "bot";
  const handle = await post("/handle/claim", { handle: shortHandle }, token);
  if (!handle.json.ok || handle.json.user.handle !== shortHandle) { console.log("FAIL: short @username should be open to all"); fails++; }
  const profileRead = await fetch(BASE + "/profile", { headers: { Authorization: "Bearer " + token } });
  const profileReadJson = await profileRead.json();
  if (!profileReadJson.ok || profileReadJson.user.avatar !== avatar || profileReadJson.user.handle !== shortHandle) {
    console.log("FAIL: persisted profile response"); fails++;
  }

  // WS: welcome -> send with clientId -> expect echo with clientId -> re-send same clientId -> expect echo, no dup
  const cid = "smoke-c1-" + Date.now().toString(36);
  let echoCount = 0;
  const wsOk = await wsSession(token, (m, ws, done) => {
    if (m.type === "welcome") {
      console.log("WELCOME user:", m.username, "channels:", m.channels.join(","));
      ws.send(JSON.stringify({ type: "send", channel: "general", text: MARKER, clientId: cid }));
    }
    if (m.type === "message" && m.message.text === MARKER) {
      echoCount++;
      if (m.message.clientId !== cid) { console.log("FAIL: echo missing clientId (got " + m.message.clientId + ")"); done(false); return; }
      console.log("ECHO#" + echoCount + " clientId:", m.message.clientId);
      if (echoCount === 1) {
        ws.send(JSON.stringify({ type: "send", channel: "general", text: MARKER, clientId: cid })); // duplicate re-send
      } else {
        ws.send(JSON.stringify({ type: "history", channel: "general" }));
      }
    }
    if (m.type === "history" && echoCount >= 2) {
      const hits = m.messages.filter((x) => x.text === MARKER && x.clientId === undefined).length;
      console.log("HISTORY marker count:", hits);
      done(hits === 1); // dedupe: stored exactly once; clientId never leaks into history
    }
  });
  if (!wsOk) { console.log("FAIL: ws clientId/dedupe"); fails++; }

  // WS join without token -> rejected
  await new Promise((resolve) => {
    const ws = new WebSocket(WSURL);
    const timer = setTimeout(() => { console.log("WS no-token timeout (ok if closed)"); resolve(); }, 3000);
    ws.on("open", () => ws.send(JSON.stringify({ type: "join", token: "" })));
    ws.on("message", (d) => { const m = JSON.parse(d.toString()); if (m.type === "error") { console.log("NO-TOKEN rejected:", m.reason); clearTimeout(timer); resolve(); } });
    ws.on("close", () => { clearTimeout(timer); resolve(); });
    ws.on("error", () => { clearTimeout(timer); resolve(); });
  });

  console.log("SMOKE_TOKEN=" + token); // for phase 2 after a restart
  console.log(fails === 0 ? "ALL_SMOKE_OK" : ("SMOKE_FAILS=" + fails));
  process.exit(fails === 0 ? 0 : 1);
}

async function phase2() {
  const oldToken = process.env.SMOKE_TOKEN || "";
  if (!oldToken) { console.log("FAIL: SMOKE_TOKEN required for phase 2"); process.exit(1); }
  // Old token must still validate after restart (persisted AUTH_SECRET) and
  // the phase-1 marker must be in history (persisted messages).
  const found = await wsSession(oldToken, (m, ws, done) => {
    if (m.type === "error") { console.log("FAIL: old token rejected:", m.reason); done(false); }
    if (m.type === "history" && m.channel === "general") {
      const hit = m.messages.some((x) => x.text === MARKER);
      console.log("history has marker:", hit);
      done(hit);
    }
  });
  console.log(found ? "PERSIST_OK" : "PERSIST_FAIL");
  process.exit(found ? 0 : 1);
}

(PHASE === 2 ? phase2() : phase1());
