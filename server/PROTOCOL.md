# SAssist protocol (v1)

Two transports on one port:

- **REST (HTTP/JSON)** — auth, profile, @usernames, premium, media upload/download.
- **WebSocket (JSON frames)** — chat. One JSON object per frame, field `type` discriminates.

All durable state (users, messages, reactions) lives in SQLite under `DATA_DIR`.

## REST

| Method & path | Auth | Body / params | Response |
|---|---|---|---|
| `GET /health` | — | — | `SAssist server ok` |
| `POST /auth/request` | — | `{method: "email"\|"phone", identifier}` | `{ok, delivered, devCode?}` — `devCode` is returned only when no delivery channel (SMTP/Twilio) is configured |
| `POST /auth/verify` | — | `{method, identifier, code, username?}` | `{ok, token, user}` — token is valid 30 days |
| `GET /handle/check?handle=x` | — | — | `{valid, available, premiumOnly, reason?}` |
| `POST /handle/claim` | Bearer | `{handle}` | `{ok, user}` / `409 {error}` — handles ≤4 chars are Premium-only |
| `GET /profile` | Bearer | — | `{ok, user}` |
| `POST /profile` | Bearer | `{displayName?, bio?, color?}` | `{ok, user}` |
| `POST /premium/claim` | Bearer | `{code}` | `{ok, user}` / `402 {error}` — requires `PREMIUM_CODE` env set on the server |
| `POST /upload` | Bearer | `{dataBase64, mime, name, kind: "image"\|"video"\|"file", width?, height?}` (max 30 MB) | `{ok, media: MediaRef, url}` |
| `GET /media/:id` | — | — | raw bytes with original `Content-Type` |

Bearer auth: `Authorization: Bearer <token>` header (or `token` field in the JSON body).

## WebSocket

### client → server
| type | fields | meaning |
|---|---|---|
| `join` | `token` | authenticate the socket; server replies `welcome` + `history` for `#general` |
| `send` | `channel, text, clientId?, media?, replyTo?, secret?, ttl?` | post a message |
| `switchChannel` | `channel` | change active channel, receive its history |
| `history` | `channel, since?, limit?` | incremental sync: messages with `ts >= since` (ascending, cap 500). Without `since`: last 100 |
| `listChannels` | — | request channel list |
| `typing` | `channel` | typing indicator (broadcast to others) |
| `react` | `channel, messageId, emoji` | toggle own reaction |

### server → client
| type | fields |
|---|---|
| `welcome` | `user: PublicUser, userId, username, channels[]` |
| `message` | `message: ChatMessage` |
| `reaction` | `channel, messageId, reactions{emoji: userId[]}` |
| `presence` | `channel, users: PublicUser[]` |
| `typing` | `channel, user: PublicUser` |
| `history` | `channel, messages[], since?` (`since` echoed on sync responses) |
| `channels` | `channels[]` |
| `error` | `reason` |

### clientId semantics (offline queue)

- A client MAY attach an opaque `clientId` (≤64 chars, e.g. a UUID) to `send`.
- The server stores it and **echoes it only in the copy of the `message` frame
  delivered to the sending socket** — other clients never see it, and it never
  appears in `history`.
- `(userId, clientId)` is unique. Re-sending the same `clientId` (offline-queue
  retry after a lost echo) does **not** duplicate the message: the server
  re-echoes the already-stored copy to the sender only.
- Flow: insert an optimistic local row keyed by `clientId` → send → on echo,
  replace the local row with the server copy.

### Secret messages

`send` with `secret: true` (optionally `ttl` seconds) is delivered live to the
channel but **never stored** — it does not appear in history or survive a
restart. The `clientId` echo still works for secret messages.

### Types

```ts
PublicUser  { id, displayName, handle, premium, color, bio? }
MediaRef    { id, kind: "image"|"video"|"file", mime, name, size, width?, height? }
ChatMessage { id, channel, userId, username, handle, premium, color, text, ts,
              media?, replyTo?, secret?, ttl?, reactions?, clientId? }
```

Default channels: `general`, `code-help`, `showtime`.

Note: clients may end-to-end encrypt `text` (the reference Android client uses
AES-256-GCM, wire format `v1:<salt>:<iv>:<ct>`); the server treats text as an
opaque string. Media is **not** E2EE.
