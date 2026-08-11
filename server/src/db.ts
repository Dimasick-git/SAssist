// SQLite persistence layer. Everything durable lives in ${DATA_DIR}/sassist.db:
// users (accounts, handles) and messages (full chat history). Synchronous
// better-sqlite3 API -- the server is single-threaded and this keeps call
// sites as simple as the old in-memory Maps.
import Database from "better-sqlite3";
import crypto from "crypto";
import fs from "fs";
import path from "path";
import type { ChatMessage, MediaRef } from "./protocol";
import type { User } from "./auth";

const DATA_DIR = process.env.DATA_DIR || path.join(process.cwd(), "data");
fs.mkdirSync(DATA_DIR, { recursive: true });

const db = new Database(path.join(DATA_DIR, "sassist.db"));
db.pragma("journal_mode = WAL");
db.pragma("busy_timeout = 5000");

db.exec(`
CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  method TEXT NOT NULL,
  identifier TEXT NOT NULL UNIQUE,
  displayName TEXT NOT NULL,
  handle TEXT NOT NULL DEFAULT '',
  premium INTEGER NOT NULL DEFAULT 0,
  color TEXT NOT NULL,
  bio TEXT NOT NULL DEFAULT '',
  avatar TEXT NOT NULL DEFAULT '',
  banner TEXT NOT NULL DEFAULT '',
  createdAt INTEGER NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_handle ON users(handle) WHERE handle != '';

CREATE TABLE IF NOT EXISTS messages (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  id TEXT NOT NULL UNIQUE,
  channel TEXT NOT NULL,
  userId TEXT NOT NULL,
  username TEXT NOT NULL,
  handle TEXT NOT NULL DEFAULT '',
  premium INTEGER NOT NULL DEFAULT 0,
  color TEXT NOT NULL DEFAULT '5865F2',
  text TEXT NOT NULL,
  ts INTEGER NOT NULL,
  media TEXT,
  replyTo TEXT,
  reactions TEXT,
  clientId TEXT
);
CREATE INDEX IF NOT EXISTS idx_msgs_channel_ts ON messages(channel, ts);
CREATE UNIQUE INDEX IF NOT EXISTS idx_msgs_client ON messages(userId, clientId) WHERE clientId IS NOT NULL;

CREATE TABLE IF NOT EXISTS reads (
  messageId TEXT NOT NULL,
  userId TEXT NOT NULL,
  ts INTEGER NOT NULL,
  PRIMARY KEY (messageId, userId)
);
CREATE INDEX IF NOT EXISTS idx_reads_msg ON reads(messageId);
`);

// Migrate SQLite files that were created before profile avatars existed.
const userColumns = db.prepare("PRAGMA table_info(users)").all() as { name: string }[];
if (!userColumns.some((column) => column.name === "avatar")) {
  db.exec("ALTER TABLE users ADD COLUMN avatar TEXT NOT NULL DEFAULT ''");
}
if (!userColumns.some((column) => column.name === "banner")) {
  db.exec("ALTER TABLE users ADD COLUMN banner TEXT NOT NULL DEFAULT ''");
}

// ---- auth secret: env wins, otherwise generate once and keep in DATA_DIR ----
export function loadOrCreateSecret(): string {
  const env = process.env.AUTH_SECRET;
  if (env && env.trim()) return env.trim();
  const file = path.join(DATA_DIR, "auth_secret");
  try {
    const existing = fs.readFileSync(file, "utf8").trim();
    if (existing) return existing;
  } catch (e) { /* first boot */ }
  const secret = crypto.randomBytes(32).toString("hex");
  fs.writeFileSync(file, secret, { mode: 0o600 });
  return secret;
}

// ---- users ----
const upsertUserStmt = db.prepare(`
  INSERT INTO users (id, method, identifier, displayName, handle, premium, color, bio, avatar, banner, createdAt)
  VALUES (@id, @method, @identifier, @displayName, @handle, @premium, @color, @bio, @avatar, @banner, @createdAt)
  ON CONFLICT(id) DO UPDATE SET
    displayName = excluded.displayName, handle = excluded.handle,
    premium = excluded.premium, color = excluded.color, bio = excluded.bio, avatar = excluded.avatar, banner = excluded.banner
`);

export function upsertUser(u: User): void {
  upsertUserStmt.run({ ...u, premium: u.premium ? 1 : 0 });
}

export function loadAllUsers(): User[] {
  const rows = db.prepare("SELECT * FROM users").all() as any[];
  return rows.map((r) => ({ ...r, premium: !!r.premium, avatar: r.avatar || "", banner: r.banner || "" }));
}

// One-time migration from the old users.json store; renames the file so the
// import never runs twice.
export function importUsersJsonIfPresent(): number {
  const file = path.join(DATA_DIR, "users.json");
  let raw: string;
  try { raw = fs.readFileSync(file, "utf8"); } catch (e) { return 0; }
  let count = 0;
  try {
    for (const u of JSON.parse(raw) as any[]) {
      if (!u || !u.id || !u.identifier) continue;
      upsertUser({
        id: u.id, method: u.method || "email", identifier: u.identifier,
        displayName: u.displayName || u.username || ("user" + String(u.id).slice(2, 6)),
        handle: u.handle || "", premium: !!u.premium,
        color: u.color || "5865F2", bio: u.bio || "", avatar: u.avatar || "", banner: u.banner || "",
        createdAt: u.createdAt || Date.now(),
      });
      count++;
    }
    fs.renameSync(file, file + ".imported");
  } catch (e) { console.error("users.json import failed", e); }
  return count;
}

// ---- messages ----
interface MsgRow {
  id: string; channel: string; userId: string; username: string; handle: string;
  premium: number; color: string; text: string; ts: number;
  media: string | null; replyTo: string | null; reactions: string | null; clientId: string | null;
}

function rowToMessage(r: MsgRow): ChatMessage {
  const m: ChatMessage = {
    id: r.id, channel: r.channel, userId: r.userId, username: r.username,
    handle: r.handle, premium: !!r.premium, color: r.color, text: r.text, ts: r.ts,
  };
  if (r.media) m.media = JSON.parse(r.media) as MediaRef;
  if (r.replyTo) m.replyTo = r.replyTo;
  if (r.reactions) m.reactions = JSON.parse(r.reactions);
  return m;
}

const insertMsgStmt = db.prepare(`
  INSERT INTO messages (id, channel, userId, username, handle, premium, color, text, ts, media, replyTo, reactions, clientId)
  VALUES (@id, @channel, @userId, @username, @handle, @premium, @color, @text, @ts, @media, @replyTo, @reactions, @clientId)
`);

export function saveMessage(m: ChatMessage, clientId?: string): "inserted" | "duplicate" {
  try {
    insertMsgStmt.run({
      id: m.id, channel: m.channel, userId: m.userId, username: m.username,
      handle: m.handle, premium: m.premium ? 1 : 0, color: m.color,
      text: m.text, ts: m.ts,
      media: m.media ? JSON.stringify(m.media) : null,
      replyTo: m.replyTo || null,
      reactions: m.reactions ? JSON.stringify(m.reactions) : null,
      clientId: clientId || null,
    });
    return "inserted";
  } catch (e: any) {
    if (e && String(e.code).startsWith("SQLITE_CONSTRAINT")) return "duplicate";
    throw e;
  }
}

export function findByClientId(userId: string, clientId: string): ChatMessage | undefined {
  const r = db.prepare("SELECT * FROM messages WHERE userId = ? AND clientId = ?").get(userId, clientId) as MsgRow | undefined;
  return r ? rowToMessage(r) : undefined;
}

export function getMessageById(id: string): ChatMessage | undefined {
  const r = db.prepare("SELECT * FROM messages WHERE id = ?").get(id) as MsgRow | undefined;
  return r ? rowToMessage(r) : undefined;
}

// Attach the readBy list to each message in one extra query (avoids N+1).
function attachReads(msgs: ChatMessage[]): ChatMessage[] {
  if (!msgs.length) return msgs;
  const ids = msgs.map((m) => m.id);
  const placeholders = ids.map(() => "?").join(",");
  const rows = db.prepare(
    "SELECT messageId, userId FROM reads WHERE messageId IN (" + placeholders + ")"
  ).all(...ids) as { messageId: string; userId: string }[];
  const byMsg = new Map<string, string[]>();
  for (const r of rows) {
    const arr = byMsg.get(r.messageId) || [];
    arr.push(r.userId); byMsg.set(r.messageId, arr);
  }
  for (const m of msgs) { const rb = byMsg.get(m.id); if (rb && rb.length) m.readBy = rb; }
  return msgs;
}

// Without `since`: the most recent `limit` messages in channel order.
// With `since`: everything at ts >= since (inclusive -- the client dedupes
// the overlap by id), capped at `limit`.
export function getHistory(channel: string, limit = 100, since?: number): ChatMessage[] {
  const cap = Math.max(1, Math.min(limit, 500));
  if (since !== undefined) {
    const rows = db.prepare(
      "SELECT * FROM messages WHERE channel = ? AND ts >= ? ORDER BY ts ASC, seq ASC LIMIT ?"
    ).all(channel, since, cap) as MsgRow[];
    return attachReads(rows.map(rowToMessage));
  }
  const rows = db.prepare(
    "SELECT * FROM messages WHERE channel = ? ORDER BY ts DESC, seq DESC LIMIT ?"
  ).all(channel, cap) as MsgRow[];
  return attachReads(rows.reverse().map(rowToMessage));
}

// Record that `userId` read `messageId` (no-op if they authored it or already
// recorded). Returns true if this was a new read (so the caller broadcasts).
export function markRead(messageId: string, userId: string): boolean {
  const m = db.prepare("SELECT userId FROM messages WHERE id = ?").get(messageId) as { userId: string } | undefined;
  if (!m || m.userId === userId) return false;
  const r = db.prepare("INSERT OR IGNORE INTO reads (messageId, userId, ts) VALUES (?, ?, ?)").run(messageId, userId, Date.now());
  return r.changes > 0;
}

export function getReaders(messageId: string): string[] {
  const rows = db.prepare("SELECT userId FROM reads WHERE messageId = ?").all(messageId) as { userId: string }[];
  return rows.map((r) => r.userId);
}

export function updateReactions(messageId: string, reactions: Record<string, string[]>): void {
  db.prepare("UPDATE messages SET reactions = ? WHERE id = ?").run(
    Object.keys(reactions).length ? JSON.stringify(reactions) : null, messageId
  );
}
