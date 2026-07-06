# SAssist server

Coder-focused chat backend. **WebSocket + JSON** for chat, small REST surface
for auth/profile/media. All durable state (users, messages, reactions, auth
secret) lives in SQLite under `DATA_DIR` — restarts and redeploys lose nothing.

## Run locally
```bash
npm install
npm run dev      # hot-reload on :8080
# or
npm run build && npm start
```

Health check: `GET http://localhost:8080/health` → `SAssist server ok`

## Self-host with Docker (recommended)
```bash
docker compose up -d --build     # from the repo root
```
Server on `:8080`, data in the `sassist_data` volume. Use `ws://<host>:8080`
as the server URL in the app.

**Login works out of the box**: with no SMTP/Twilio configured, the one-time
code is returned to the app and shown on the code screen. Configure delivery
(below) to send real emails/SMS instead.

## Environment variables

| Var | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | listen port |
| `HOST` | *(all interfaces)* | bind address. Set only if your host requires a specific IP (alwaysdata sets this automatically) |
| `DATA_DIR` | `./data` (`/data` in Docker) | SQLite DB, media files, auth secret |
| `AUTH_SECRET` | auto | token signing key. If unset, generated once and persisted to `DATA_DIR/auth_secret` — tokens survive restarts either way |
| `PREMIUM_CODE` | *(off)* | set to enable Premium claims (`POST /premium/claim`). Unset = nobody can claim premium |
| `SMTP_HOST` `SMTP_PORT` `SMTP_USER` `SMTP_PASS` `SMTP_FROM` `SMTP_SECURE` | *(off)* | real e-mail OTP delivery |
| `TWILIO_SID` `TWILIO_TOKEN` `TWILIO_FROM` | *(off)* | real SMS OTP delivery |
| `DISABLE_DEV_CODE` | `0` | set `1` to refuse login when no delivery channel is configured (never return codes to clients) |

Notes:
- Configuring SMTP/Twilio **automatically disables** the returned `devCode`.
- A configured-but-failing channel never falls back to `devCode`.
- Legacy `users.json` (pre-SQLite) is imported automatically on first boot.
- Backup = copy `DATA_DIR` (`sassist.db*`, `media/`, `auth_secret`).

## Cloud deploys
- **Koyeb Free Instance (default Android target)**: create a Web Service from this repo, choose root directory `server`, Dockerfile `Dockerfile`, port `8080`, and the free instance type. If the app is named `sassist` and the org is `dimasick-git`, Koyeb exposes `https://sassist-dimasick-git.koyeb.app`; the Android app uses `wss://sassist-dimasick-git.koyeb.app` by default. Free instances are good for hobby/testing use, have limited CPU/RAM/disk, and can sleep when idle.
- **Fly.io**: `fly.toml` included, persistent volume at `/data`. `fly launch --no-deploy --copy-config`, `fly volumes create sassist_data --size 1 --region fra`, `fly deploy`. Set secrets with `fly secrets set PREMIUM_CODE=...`.
- **Render**: `render.yaml` blueprint with a 1 GB disk at `/data` (starter plan — the free plan has no disk and loses data on redeploy).

## Smoke test
```bash
npm run build && npm start &   # start server
npm run smoke                  # auth, ws round-trip, clientId dedupe
# restart persistence check:
#   restart the server with the same DATA_DIR, then
SMOKE_PHASE=2 SMOKE_TOKEN=<token printed by phase 1> node smoke.js
```
CI (`.github/workflows/server-ci.yml`) runs both phases on every push.

Protocol: see [PROTOCOL.md](./PROTOCOL.md).
