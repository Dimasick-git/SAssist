# SAssist Android app

Jetpack Compose messenger client with an embedded JavaScript script console
(Rhino). Talks to the SAssist server over WebSocket + REST.

## Build
```bash
./gradlew :app:assembleDebug     # needs Android SDK 34
```
CI (`.github/workflows/android-build.yml`) builds a signed release APK.

## First run
1. Deploy the backend to Koyeb's free web service as app `sassist` under org `dimasick-git` (root directory `server`, Dockerfile `Dockerfile`, port `8080`). Koyeb gives it the public URL `https://sassist-dimasick-git.koyeb.app`.
2. The Android app defaults to `wss://sassist-dimasick-git.koyeb.app`, so after that deploy you can sign in without changing Server settings. If your Koyeb org/app name is different, open **Sign in → Server settings** and paste the `wss://...koyeb.app` URL shown by Koyeb.
3. Local fallback: from the repo root run `docker compose up -d --build`, then use `ws://10.0.2.2:8080` on the standard Android emulator or `ws://192.168.x.x:8080` from a physical phone.
4. Enter e-mail/phone. If the server has no SMTP/Twilio configured, the
   one-time code is shown right on the code screen.

## Offline support
- All messages live in a local Room DB — history is readable offline.
- Messages sent offline are queued (`pending`, clock icon), then delivered
  automatically when connectivity returns:
  - in the foreground via an immediate reconnect (network-callback driven,
    exponential backoff);
  - in the background via WorkManager (`SendQueueWorker`).
- Delivery is exactly-once: every send carries a `clientId`, the server
  dedupes re-sends and the echo replaces the optimistic local row.
- After reconnect the app requests `history since <last local ts>` per
  channel, so gaps from the offline period are filled.
- Failed messages (after 5 attempts) show **"Not sent — tap to retry"**.

## E2EE
- Text messages are encrypted with AES-256-GCM (PBKDF2 passphrase per room).
- Tap the **E2EE** badge in a chat to set the room passphrase. Until you set
  one, a default key is used (amber badge = anyone on the server could read).
  Share passphrases off-band; changing a key re-syncs channel history.
- **Media is not E2EE** — photos/videos/files are uploaded as-is.

## Features
Channels, replies, emoji reactions (long-press a message), photo/video/file
attachments, typing indicators, presence, markdown + syntax-highlighted code
blocks, profile (display name, bio, name color, unique @username, premium),
script console (`sa.send()`, `sa.log()`, `sa.lastMessage()`).

## Future work
- FCM push notifications (needs a Google project; the server would also need
  to store device tokens).
- Message edit/delete, DMs, channel management.
