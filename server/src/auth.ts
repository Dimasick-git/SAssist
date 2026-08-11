import crypto from "crypto";
import { loadOrCreateSecret, upsertUser, loadAllUsers, importUsersJsonIfPresent } from "./db";

export interface User {
  id: string;
  method: string;
  identifier: string;
  displayName: string;
  handle: string;          // unique @username, lowercase, may be "" until claimed
  premium: boolean;
  color: string;
  bio: string;
  avatar: string;
  createdAt: number;
}

export interface PublicUser {
  id: string; displayName: string; handle: string; premium: boolean; color: string; bio?: string; avatar?: string;
}

interface Otp { hash: string; salt: string; expires: number; tries: number; }

// Env AUTH_SECRET wins; otherwise a secret is generated once and persisted in
// DATA_DIR so tokens survive restarts with zero configuration.
const SECRET = loadOrCreateSecret();
// Premium is opt-in: without PREMIUM_CODE in the environment nobody can claim it.
const PREMIUM_CODE = (process.env.PREMIUM_CODE || "").trim();
const OTP_TTL = 5 * 60 * 1000;
const TOKEN_TTL = 30 * 24 * 60 * 60 * 1000;
const MAX_TRIES = 5;
const COLORS = ["5865F2", "229ED9", "23A55A", "F23F43", "FAA61A", "EB459E", "9B59B6", "1ABC9C"];

const otps = new Map<string, Otp>();
const users = new Map<string, User>();
const handles = new Map<string, string>(); // handle(lowercase) -> userId  (reservation index)

function load() {
  const imported = importUsersJsonIfPresent();
  if (imported) console.log("Imported " + imported + " user(s) from legacy users.json");
  for (const u of loadAllUsers()) {
    users.set(u.id, u);
    if (u.handle) handles.set(u.handle.toLowerCase(), u.id);
  }
}
function persist(u: User) { upsertUser(u); }
load();

function b64url(buf: Buffer): string { return buf.toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, ""); }
function hashCode(code: string, salt: string): string { return crypto.createHash("sha256").update(salt + ":" + code).digest("hex"); }
function safeEq(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  try { return crypto.timingSafeEqual(Buffer.from(a), Buffer.from(b)); } catch (e) { return false; }
}

export function toPublic(u: User): PublicUser {
  return { id: u.id, displayName: u.displayName, handle: u.handle, premium: u.premium, color: u.color, bio: u.bio, avatar: u.avatar };
}

export function genCode(): string { return "" + Math.floor(100000 + Math.random() * 900000); }

export function requestOtp(identifier: string): string {
  const code = genCode();
  const salt = crypto.randomBytes(8).toString("hex");
  otps.set(identifier, { hash: hashCode(code, salt), salt, expires: Date.now() + OTP_TTL, tries: 0 });
  return code;
}

export function verifyOtp(identifier: string, code: string): boolean {
  const o = otps.get(identifier);
  if (!o) return false;
  if (Date.now() > o.expires) { otps.delete(identifier); return false; }
  o.tries++;
  if (o.tries > MAX_TRIES) { otps.delete(identifier); return false; }
  if (!safeEq(hashCode(code, o.salt), o.hash)) return false;
  otps.delete(identifier);
  return true;
}

// ---- handle rules ----
export function normalizeHandle(raw: string): string { return ("" + raw).trim().toLowerCase().replace(/^@/, ""); }

export function handleStatus(raw: string): { valid: boolean; available: boolean; premiumOnly: boolean; reason?: string } {
  const h = normalizeHandle(raw);
  if (!/^[a-z][a-z0-9_]{2,19}$/.test(h)) {
    return { valid: false, available: false, premiumOnly: false, reason: "3-20 chars, start with a letter, only a-z 0-9 _" };
  }
  const taken = handles.has(h);
  return { valid: true, available: !taken, premiumOnly: false, reason: taken ? "already taken" : undefined };
}

export function login(method: string, identifier: string, displayName: string): User {
  for (const u of users.values()) {
    if (u.identifier === identifier) {
      if (displayName && displayName !== u.displayName) { u.displayName = displayName; persist(u); }
      return u;
    }
  }
  const id = "u_" + crypto.randomBytes(6).toString("hex");
  const user: User = {
    id, method, identifier,
    displayName: displayName || ("user" + id.slice(2, 6)),
    handle: "", premium: false,
    color: COLORS[Math.floor(Math.random() * COLORS.length)],
    bio: "", avatar: "", createdAt: Date.now()
  };
  users.set(id, user); persist(user); return user;
}

export function claimHandle(userId: string, raw: string): { ok: boolean; error?: string; user?: User } {
  const u = users.get(userId);
  if (!u) return { ok: false, error: "no user" };
  const h = normalizeHandle(raw);
  const st = handleStatus(h);
  if (!st.valid) return { ok: false, error: st.reason };
  const owner = handles.get(h);
  if (owner && owner !== userId) return { ok: false, error: "username already taken" };
  if (u.handle) handles.delete(u.handle.toLowerCase());  // release old
  u.handle = h; handles.set(h, userId); persist(u);
  return { ok: true, user: u };
}

export function updateProfile(userId: string, patch: { displayName?: string; bio?: string; color?: string; avatar?: string }): { ok: boolean; user?: User } {
  const u = users.get(userId);
  if (!u) return { ok: false };
  if (typeof patch.displayName === "string" && patch.displayName.trim()) u.displayName = patch.displayName.trim().slice(0, 40);
  if (typeof patch.bio === "string") u.bio = patch.bio.slice(0, 200);
  if (typeof patch.color === "string" && /^[0-9A-Fa-f]{6}$/.test(patch.color)) u.color = patch.color.toUpperCase();
  if (typeof patch.avatar === "string" && (/^md_[A-Za-z0-9_-]{3,120}$/.test(patch.avatar) || patch.avatar === "")) u.avatar = patch.avatar;
  persist(u); return { ok: true, user: u };
}

export function claimPremium(userId: string, code: string): { ok: boolean; error?: string; user?: User } {
  const u = users.get(userId);
  if (!u) return { ok: false, error: "no user" };
  if (!PREMIUM_CODE) return { ok: false, error: "premium codes are not enabled on this server" };
  if (("" + code).trim() !== PREMIUM_CODE) return { ok: false, error: "invalid premium code" };
  u.premium = true; persist(u);
  return { ok: true, user: u };
}

export function getUser(userId: string): User | undefined { return users.get(userId); }

export function signToken(userId: string): string {
  const payload = b64url(Buffer.from(JSON.stringify({ uid: userId, iat: Date.now(), exp: Date.now() + TOKEN_TTL })));
  const sig = b64url(crypto.createHmac("sha256", SECRET).update(payload).digest());
  return payload + "." + sig;
}

export function userForToken(token: string): User | undefined {
  if (!token || token.indexOf(".") < 0) return undefined;
  const dot = token.indexOf(".");
  const body = token.slice(0, dot);
  const sig = token.slice(dot + 1);
  const expect = b64url(crypto.createHmac("sha256", SECRET).update(body).digest());
  if (!safeEq(sig, expect)) return undefined;
  let payload: any;
  try { payload = JSON.parse(Buffer.from(body.replace(/-/g, "+").replace(/_/g, "/"), "base64").toString()); }
  catch (e) { return undefined; }
  if (!payload || typeof payload.exp !== "number" || Date.now() > payload.exp) return undefined;
  return users.get(payload.uid);
}
