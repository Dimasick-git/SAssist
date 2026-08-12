// Run against a temporary server: BASE=http://127.0.0.1:8092 node media-transport-smoke.js
const BASE = process.env.BASE || "http://127.0.0.1:8092";
const WebSocket = require("ws");

const fail = (message) => { console.error("MEDIA_TRANSPORT_SMOKE_FAIL:", message); process.exit(1); };

async function postJson(path, body) {
  const response = await fetch(BASE + path, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
  return { status: response.status, json: await response.json() };
}

function rejectMissingAttachment(token) {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(BASE.replace(/^http/, "ws"));
    const timeout = setTimeout(() => {
      socket.terminate();
      reject(new Error("missing-attachment WebSocket test timed out"));
    }, 5000);
    socket.on("open", () => socket.send(JSON.stringify({ type: "join", token })));
    socket.on("message", (raw) => {
      const frame = JSON.parse(raw.toString());
      if (frame.type === "welcome") {
        socket.send(JSON.stringify({
          type: "send", channel: "general", text: "",
          media: { id: "does_not_exist", kind: "image", mime: "image/jpeg", name: "missing.jpg", size: 10 }
        }));
      }
      if (frame.type === "error") {
        clearTimeout(timeout);
        socket.close();
        if (frame.reason !== "attachment unavailable; upload it again") {
          reject(new Error("unexpected missing-attachment error: " + frame.reason));
        } else resolve();
      }
    });
    socket.on("error", (error) => { clearTimeout(timeout); reject(error); });
  });
}

async function main() {
  const tag = Date.now().toString(36);
  const request = await postJson("/auth/request", { method: "email", identifier: `media-${tag}@example.com` });
  if (!request.json.ok || !request.json.devCode) fail("OTP request failed");
  const login = await postJson("/auth/verify", { method: "email", identifier: `media-${tag}@example.com`, code: request.json.devCode, username: "Media test" });
  if (!login.json.ok || !login.json.token) fail("OTP verification failed");

  const bytes = Buffer.alloc(128 * 1024, 0x5a);
  const upload = await fetch(BASE + "/upload/raw?mime=application%2Foctet-stream&name=range-test.bin&kind=file", {
    method: "POST", headers: { Authorization: `Bearer ${login.json.token}`, "Content-Type": "application/octet-stream", "Content-Length": String(bytes.length) }, body: bytes
  });
  const uploaded = await upload.json();
  if (!upload.ok || !uploaded.ok || !uploaded.media?.id || uploaded.media.size !== bytes.length) fail("raw upload failed");

  const ranged = await fetch(BASE + "/media/" + uploaded.media.id, { headers: { Range: "bytes=0-1023" } });
  const rangedBytes = Buffer.from(await ranged.arrayBuffer());
  if (ranged.status !== 206 || rangedBytes.length !== 1024 || ranged.headers.get("accept-ranges") !== "bytes") fail("range response failed");

  await rejectMissingAttachment(login.json.token);
  console.log("MEDIA_TRANSPORT_SMOKE_OK");
}

main().catch((error) => fail(error.message));
