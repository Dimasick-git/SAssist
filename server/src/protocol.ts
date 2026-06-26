// SAssist wire protocol — JSON over WebSocket.
// Intentionally tiny + language-agnostic so a future Switch (C++/libnx) client
// can speak it with zero ceremony.

export const DEFAULT_CHANNELS = ["general", "code-help", "showtime"] as const;

export interface ChatMessage {
  id: string;
  channel: string;
  username: string;
  text: string;     // raw text; may contain markdown / fenced code blocks
  ts: number;       // epoch ms
}

// ---- client -> server ----
export type ClientMsg =
  | { type: "join"; username: string }
  | { type: "send"; channel: string; text: string }
  | { type: "switchChannel"; channel: string }
  | { type: "listChannels" };

// ---- server -> client ----
export type ServerMsg =
  | { type: "welcome"; userId: string; username: string; channels: string[] }
  | { type: "message"; message: ChatMessage }
  | { type: "presence"; channel: string; users: string[] }
  | { type: "history"; channel: string; messages: ChatMessage[] }
  | { type: "channels"; channels: string[] }
  | { type: "error"; reason: string };

export function parseClientMsg(raw: string): ClientMsg | null {
  try {
    const o = JSON.parse(raw);
    if (o && typeof o.type === "string") return o as ClientMsg;
  } catch { /* ignore */ }
  return null;
}
