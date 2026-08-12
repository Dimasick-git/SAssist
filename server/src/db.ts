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

CREATE TABLE IF NOT EXISTS direct_channels (
  channel TEXT PRIMARY KEY,
  memberA TEXT NOT NULL,
  memberB TEXT NOT NULL,
  createdAt INTEGER NOT NULL,
  CHECK(memberA < memberB)
);
CREATE INDEX IF NOT EXISTS idx_direct_channels_a ON direct_channels(memberA);
CREATE INDEX IF NOT EXISTS idx_direct_channels_b ON direct_channels(memberB);

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

CREATE TABLE IF NOT EXISTS device_tokens (
  userId TEXT NOT NULL,
  token TEXT NOT NULL,
  updatedAt INTEGER NOT NULL,
  PRIMARY KEY (userId, token)
);
CREATE INDEX IF NOT EXISTS idx_device_tokens_user ON device_tokens(userId);
`);

// Migrate SQLite files that were created before profile avatars existed.
const userColumns = db.prepare("PRAGMA table_info(users)").all() as { name: string }[];
if (!userColumns.some((column) => column.name === "avatar")) {
  db.exec("ALTER TABLE users ADD COLUMN avatar TEXT NOT NULL DEFAULT ''");
}
if (!userColumns.some((column) => column.name === "banner")) {
  db.exec("ALTER TABLE users ADD COLUMN banner TEXT NOT NULL DEFAULT ''");
}

export function saveDeviceToken(userId: string, token: string) {
  if (!userId || !token) return;
  db.prepare("INSERT OR REPLACE INTO device_tokens (userId, token, updatedAt) VALUES (?, ?, ?)").run(userId, token, Date.now());
}

export function getDeviceTokensForUsers(userIds: string[]): string[] {
  if (!userIds || userIds.length === 0) return [];
  const placeholders = userIds.map(() => "?").join(",");
  const rows = db.prepare("SELECT DISTINCT token FROM device_tokens WHERE userId IN (" + placeholders + ")").all(...userIds) as { token: string }[];
  return rows.map((r) => r.token);
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

export function saveUser(u: User) {
	  db.prepare(`
	    INSERT OR REPLACE INTO users (id, method, identifier, displayName, handle, premium, color, bio, avatar, banner, createdAt)
	    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	  `).run(u.id, u.method, u.identifier, u.displayName, u.handle || "", u.premium ? 1 : 0, u.color, u.bio || "", u.avatar || "", u.banner || "", u.createdAt);
	}
	export const upsertUser = saveUser;
	export function loadAllUsers(): User[] {
	  const rows = db.prepare("SELECT * FROM users").all() as any[];
	  return rows.map(r => ({ ...r, premium: !!r.premium }));
	}
	export function importUsersJsonIfPresent(): number { return 0; }

export function getUserById(id: string): User | undefined {
  const row = db.prepare("SELECT * FROM users WHERE id = ?").get(id) as any;
  if (!row) return undefined;
  return { ...row, premium: !!row.premium };
}

export function getUserByMethodAndIdentifier(method: string, identifier: string): User | undefined {
  const row = db.prepare("SELECT * FROM users WHERE method = ? AND identifier = ?").get(method, identifier) as any;
  if (!row) return undefined;
  return { ...row, premium: !!row.premium };
}

export function getUserByHandle(handle: string): User | undefined {
  const row = db.prepare("SELECT * FROM users WHERE handle = ?").get(handle) as any;
  if (!row) return undefined;
  return { ...row, premium: !!row.premium };
}

export function saveMessage(m: ChatMessage, clientId?: string): "inserted" | "duplicate" {
  if (clientId) {
    const existing = db.prepare("SELECT * FROM messages WHERE userId = ? AND clientId = ?").get(m.userId, clientId);
    if (existing) return "duplicate";
  }
  db.prepare(`
    INSERT INTO messages (id, channel, userId, username, handle, premium, color, text, ts, media, replyTo, reactions, clientId)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).run(
    m.id,
    m.channel,
    m.userId,
    m.username,
    m.handle || "",
    m.premium ? 1 : 0,
    m.color || "5865F2",
    m.text,
    m.ts,
    m.media ? JSON.stringify(m.media) : null,
    m.replyTo || null,
    m.reactions ? JSON.stringify(m.reactions) : null,
    clientId || null
  );
  return "inserted";
}

export function findByClientId(userId: string, clientId: string): ChatMessage | undefined {
  const row = db.prepare("SELECT * FROM messages WHERE userId = ? AND clientId = ?").get(userId, clientId) as any;
  if (!row) return undefined;
  return rowToMessage(row);
}

export function getMessageById(id: string): ChatMessage | undefined {
  const row = db.prepare("SELECT * FROM messages WHERE id = ?").get(id) as any;
  if (!row) return undefined;
  return rowToMessage(row);
}

export function updateReactions(messageId: string, reactions: Record<string, string[]>) {
  db.prepare("UPDATE messages SET reactions = ? WHERE id = ?").run(JSON.stringify(reactions), messageId);
}

export function markRead(messageId: string, userId: string): boolean {
  try {
    db.prepare("INSERT OR IGNORE INTO reads (messageId, userId, ts) VALUES (?, ?, ?)").run(messageId, userId, Date.now());
    return true;
  } catch (e) {
    return false;
  }
}

export function getHistory(channel: string, limit = 100, since?: number): ChatMessage[] {
  let rows: any[];
  if (since !== undefined) {
    rows = db.prepare("SELECT * FROM messages WHERE channel = ? AND ts > ? ORDER BY ts ASC LIMIT ?").all(channel, since, limit);
  } else {
    rows = db.prepare("SELECT * FROM messages WHERE channel = ? ORDER BY ts DESC LIMIT ?").all(channel, limit);
    rows.reverse();
  }
  return rows.map(rowToMessage);
}

export function createDirectChannel(memberA: string, memberB: string): string {
  const [a, b] = memberA < memberB ? [memberA, memberB] : [memberB, memberA];
  const channel = "dm:" + a + ":" + b;
  db.prepare("INSERT OR IGNORE INTO direct_channels (channel, memberA, memberB, createdAt) VALUES (?, ?, ?, ?)").run(channel, a, b, Date.now());
  return channel;
}

export function getDirectChannel(channel: string): { memberA: string; memberB: string } | undefined {
  return db.prepare("SELECT memberA, memberB FROM direct_channels WHERE channel = ?").get(channel) as any;
}

export function isDirectChannelMember(channel: string, userId: string): boolean {
  if (!channel.startsWith("dm:")) return false;
  const row = db.prepare("SELECT 1 FROM direct_channels WHERE channel = ? AND (memberA = ? OR memberB = ?)").get(channel, userId, userId);
  return !!row;
}

export function directChannelsForUser(userId: string): string[] {
  const rows = db.prepare("SELECT channel FROM direct_channels WHERE memberA = ? OR memberB = ?").all(userId, userId) as { channel: string }[];
  return rows.map(r => r.channel);
}

function rowToMessage(row: any): ChatMessage {
  return {
    id: row.id,
    channel: row.channel,
    userId: row.userId,
    username: row.username,
    handle: row.handle || "",
    premium: !!row.premium,
    color: row.color,
    text: row.text,
    ts: row.ts,
    media: row.media ? JSON.parse(row.media) : undefined,
    replyTo: row.replyTo || undefined,
    reactions: row.reactions ? JSON.parse(row.reactions) : undefined
  };
}
