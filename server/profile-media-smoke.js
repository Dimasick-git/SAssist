// Profile media smoke test for a deployed SAssist backend.
// BASE=https://sassist-labs.onrender.com node profile-media-smoke.js
const BASE = (process.env.BASE || "http://127.0.0.1:8080").replace(/\/$/, "");

const fail = (message) => { console.error("PROFILE_MEDIA_SMOKE_FAIL:", message); process.exit(1); };

async function request(path, options = {}) {
  const response = await fetch(BASE + path, options);
  const text = await response.text();
  let json = null;
  try { json = JSON.parse(text); } catch (_) {}
  return { response, json, text };
}

async function main() {
  const tag = Date.now().toString(36);
  const identifier = `profile-media-${tag}@example.com`;
  const otp = await request("/auth/request", {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ method: "email", identifier }),
  });
  if (!otp.json?.ok || !otp.json.devCode) fail(`OTP request failed (status=${otp.response.status}, body=${JSON.stringify(otp.json || otp.text)})`);
  const login = await request("/auth/verify", {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ method: "email", identifier, code: otp.json.devCode, username: "Profile Media" }),
  });
  const token = login.json?.token;
  const userId = login.json?.user?.id;
  if (!token || !userId) fail("OTP verify did not return user/token");
  const headers = { "Content-Type": "application/json", Authorization: `Bearer ${token}` };

  async function upload(name, bytes) {
    const result = await request("/upload", {
      method: "POST", headers,
      body: JSON.stringify({ dataBase64: Buffer.from(bytes).toString("base64"), mime: "image/png", name, kind: "image" }),
    });
    if (!result.json?.ok || !result.json.media?.id) fail(`upload ${name} failed`);
    const blob = await request(`/media/${encodeURIComponent(result.json.media.id)}`);
    if (!blob.response.ok || blob.text !== bytes) {
      fail(`media ${name} could not be read back (status=${blob.response.status}, received=${JSON.stringify(blob.text)})`);
    }
    return result.json.media.id;
  }

  const avatar = await upload("avatar.png", "sassist-avatar-smoke");
  const banner = await upload("banner.png", "sassist-banner-smoke");
  const saved = await request("/profile", {
    method: "POST", headers,
    body: JSON.stringify({ displayName: "Profile Media", bio: "avatar/banner smoke", color: "5865F2", avatar, banner }),
  });
  if (!saved.json?.ok || saved.json.user?.avatar !== avatar || saved.json.user?.banner !== banner) fail("profile did not retain avatar/banner");
  const self = await request("/profile", { headers: { Authorization: `Bearer ${token}` } });
  const publicProfile = await request(`/users/${encodeURIComponent(userId)}`, { headers: { Authorization: `Bearer ${token}` } });
  if (self.json?.user?.avatar !== avatar || self.json?.user?.banner !== banner) fail("self profile lost media references");
  if (publicProfile.json?.user?.avatar !== avatar || publicProfile.json?.user?.banner !== banner) fail("public profile lost media references");
  console.log("PROFILE_MEDIA_SMOKE_OK", JSON.stringify({ userId, avatar, banner }));
}

main().catch((error) => fail(error.message));
