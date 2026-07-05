# SAssist

Self-hostable coder messenger: Android (Jetpack Compose) client + tiny
Node.js/TypeScript server (WebSocket + REST, SQLite).

- **Works out of the box** — `docker compose up -d --build` gives a fully
  working server: durable users/messages/media, login without any external
  services (OTP codes are shown in the app until you configure SMTP/Twilio).
- **Offline-first client** — local Room cache, offline send queue with
  exactly-once delivery, background flush via WorkManager, incremental
  history sync after reconnect.
- **E2EE text** (AES-256-GCM, per-room passphrases), media attachments,
  replies, reactions, typing/presence, premium @usernames, profiles, and an
  embedded JavaScript script console.

| Part | Docs |
|---|---|
| Server (run, deploy, env vars) | [server/README.md](server/README.md) |
| Wire protocol v1 | [server/PROTOCOL.md](server/PROTOCOL.md) |
| Android app (build, offline, E2EE) | [android/README.md](android/README.md) |

## Quick start
```bash
# 1. server
docker compose up -d --build          # -> ws://<host>:8080

# 2. app
cd android && ./gradlew :app:assembleDebug
# install the APK, then: Sign in -> Server settings -> ws://<host>:8080
```

Deploy configs for **Fly.io** (`fly.toml`, persistent volume) and **Render**
(`render.yaml`, persistent disk) are included. CI builds the APK and runs the
server smoke tests (including a restart-persistence check) on every push.
