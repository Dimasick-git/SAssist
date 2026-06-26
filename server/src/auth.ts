import crypto from "crypto";
import fs from "fs";
import path from "path";

export interface User { id: string; method: string; identifier: string; username: string; createdAt: number; }
interface Otp { hash: string; salt: string; expires: number; tries: number; }

const DATA_DIR = path.join(process.cwd(), "data");
const USERS_FILE = path.join(DATA_DIR, "users.json");
const SECRET = process.env.AUTH_SECRET || crypto.randomBytes(32).toString("hex");
const OTP_TTL = 5 * 60 * 1000;
const TOKEN_TTL = 30 * 24 * 60 * 60 * 1000;
const MAX_TRIES = 5;

const otps = new Map<string, Otp>();
const users = new Map<string, User>();

function load() {
  try { const raw = fs.readFileSync(USERS_FILE, "utf8"); for (const u of JSON.parse(raw) as User[]) users.set(u.id, u); }
  catch (e) { /* no file yet */ }
}
function persist() {
  try { fs.mkdirSync(DATA_DIR, { recursive: true }); fs.writeFileSync(USERS_FILE, JSON.stringify([...users.values()], null, 2)); }
  catch (e) { /* ignore */ }
}
load();

function b64url(buf: Buffer): string { return buf.toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, ""); }
function hashCode(code: string, salt: string): string { return crypto.createHash("sha256").update(salt + ":" + code).digest("hex"); }
function safeEq(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  try { return crypto.timingSafeEqual(Buffer.from(a), Buffer.from(b)); } catch (e) { return false; }
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

export function upsertUser(method: string, identifier: string, username: string): User {
  for (const u of users.values()) {
    if (u.identifier === identifier) { if (username && username !== u.username) { u.username = username; persist(); } return u; }
  }
  const id = "u_" + crypto.randomBytes(6).toString("hex");
  const user: User = { id, method, identifier, username: username || identifier, createdAt: Date.now() };
  users.set(id, user); persist(); return user;
}

// HMAC-signed, stateless, expiring token (JWT-like).
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
