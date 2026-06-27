// SAssist wire protocol -- JSON over WebSocket.
export const DEFAULT_CHANNELS = ["general", "code-help", "showtime"] as const;

export interface PublicUser {
  id: string;
  displayName: string;
  handle: string;        // @username (unique, may be empty until claimed)
  premium: boolean;
  color: string;
  bio?: string;
}

export interface MediaRef {
  id: string;
  kind: "image" | "video" | "file";
  mime: string;
  name: string;
  size: number;
  width?: number;
  height?: number;
}

export interface ChatMessage {
  id: string;
  channel: string;
  userId: string;
  username: string;      // display name (back-compat)
  handle: string;
  premium: boolean;
  color: string;
  text: string;
  ts: number;
  media?: MediaRef;
  replyTo?: string;
  secret?: boolean;      // ephemeral / self-destruct
  ttl?: number;          // seconds to live after read (secret messages)
  reactions?: Record<string, string[]>;
}

export type ClientMsg =
  | { type: "join"; token: string }
  | { type: "send"; channel: string; text: string; media?: MediaRef; replyTo?: string; secret?: boolean; ttl?: number }
  | { type: "switchChannel"; channel: string }
  | { type: "listChannels" }
  | { type: "typing"; channel: string }
  | { type: "react"; channel: string; messageId: string; emoji: string };

export type ServerMsg =
  | { type: "welcome"; user: PublicUser; userId: string; username: string; channels: string[] }
  | { type: "message"; message: ChatMessage }
  | { type: "reaction"; channel: string; messageId: string; reactions: Record<string, string[]> }
  | { type: "presence"; channel: string; users: PublicUser[] }
  | { type: "typing"; channel: string; user: PublicUser }
  | { type: "history"; channel: string; messages: ChatMessage[] }
  | { type: "channels"; channels: string[] }
  | { type: "error"; reason: string };

export function parseClientMsg(raw: string): ClientMsg | null {
  try { const o = JSON.parse(raw); if (o && typeof o.type === "string") return o as ClientMsg; }
  catch (e) { /* ignore */ }
  return null;
}
