// Private DM integration smoke test.
// Run against a temporary server: BASE=http://127.0.0.1:8080 WS=ws://127.0.0.1:8080 node dm-smoke.js
const BASE = process.env.BASE || "http://127.0.0.1:8080";
const WS_URL = process.env.WS || "ws://127.0.0.1:8080";
const { WebSocket } = require("ws");

const fail = (message) => { console.error("DM_SMOKE_FAIL:", message); process.exit(1); };

async function post(path, body, token) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await fetch(BASE + path, { method: "POST", headers, body: JSON.stringify(body) });
  return { status: response.status, json: await response.json() };
}

async function login(identifier, username) {
  const requested = await post("/auth/request", { method: "email", identifier });
  if (!requested.json.ok || !requested.json.devCode) fail("OTP request did not return devCode");
  const verified = await post("/auth/verify", { method: "email", identifier, code: requested.json.devCode, username });
  if (!verified.json.ok || !verified.json.token || !verified.json.user?.id) fail("OTP verification failed");
  return { token: verified.json.token, user: verified.json.user };
}

function openSocket(token) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(WS_URL);
    const listeners = [];
    const session = {
      ws,
      waitFor(predicate, timeout = 5000) {
        return new Promise((resolveMessage, rejectMessage) => {
          const listener = (message) => {
            if (predicate(message)) {
              clearTimeout(timer);
              listeners.splice(listeners.indexOf(listener), 1);
              resolveMessage(message);
            }
          };
          const timer = setTimeout(() => {
            const index = listeners.indexOf(listener);
            if (index >= 0) listeners.splice(index, 1);
            rejectMessage(new Error("timed out waiting for WebSocket event"));
          }, timeout);
          listeners.push(listener);
        });
      },
      close() { try { ws.close(); } catch (_) {} }
    };
    ws.on("open", () => ws.send(JSON.stringify({ type: "join", token })));
    ws.on("message", (raw) => {
      const message = JSON.parse(raw.toString());
      if (message.type === "welcome") resolve(session);
      for (const listener of [...listeners]) listener(message);
    });
    ws.on("error", reject);
  });
}

async function main() {
  const tag = Date.now().toString(36);
  const alice = await login(`alice-${tag}@example.com`, "Alice");
  const bob = await login(`bob-${tag}@example.com`, "Bob");
  const mallory = await login(`mallory-${tag}@example.com`, "Mallory");
  const a = await openSocket(alice.token);
  const b = await openSocket(bob.token);
  const m = await openSocket(mallory.token);
  try {
    const bSawChannel = b.waitFor((event) => event.type === "channels" && event.channels.some((channel) => channel.startsWith("dm:")));
    const dmStarted = a.waitFor((event) => event.type === "dmStarted");
    a.ws.send(JSON.stringify({ type: "startDm", userId: bob.user.id }));
    const dm = await dmStarted;
    const channel = dm.channel;
    if (!channel.includes(alice.user.id) || !channel.includes(bob.user.id)) fail("DM channel membership is malformed");
    await bSawChannel;

    const bHistory = b.waitFor((event) => event.type === "history" && event.channel === channel);
    b.ws.send(JSON.stringify({ type: "switchChannel", channel }));
    await bHistory;
    const aHistory = a.waitFor((event) => event.type === "history" && event.channel === channel);
    a.ws.send(JSON.stringify({ type: "switchChannel", channel }));
    await aHistory;

    const encryptedText = "e2ee:v1:smoke-ciphertext";
    const gotMessage = b.waitFor((event) => event.type === "message" && event.message.channel === channel && event.message.text === encryptedText);
    a.ws.send(JSON.stringify({ type: "send", channel, text: encryptedText, clientId: `dm-${tag}` }));
    await gotMessage;

    const encryptedOffer = "v1:call-offer-ciphertext";
    const gotOffer = b.waitFor((event) => event.type === "callSignal" && event.channel === channel && event.payload === encryptedOffer);
    a.ws.send(JSON.stringify({ type: "callSignal", channel, payload: encryptedOffer }));
    await gotOffer;

    const gotCallEnd = b.waitFor((event) => event.type === "callEnd" && event.channel === channel);
    a.ws.send(JSON.stringify({ type: "callEnd", channel }));
    await gotCallEnd;

    const forbiddenCall = m.waitFor((event) => event.type === "error" && event.reason === "private call channel required");
    m.ws.send(JSON.stringify({ type: "callSignal", channel, payload: encryptedOffer }));
    await forbiddenCall;

    const forbidden = m.waitFor((event) => event.type === "error" && event.reason === "no such channel");
    m.ws.send(JSON.stringify({ type: "switchChannel", channel }));
    await forbidden;
    console.log("DM_SMOKE_OK");
  } finally {
    a.close(); b.close(); m.close();
  }
}

main().catch((error) => fail(error.message));
