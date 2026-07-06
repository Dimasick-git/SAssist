# Deploy SAssist free forever on alwaysdata (no credit card)

[alwaysdata](https://www.alwaysdata.com/en/) has a genuine **free 100 MB plan**
— no credit card, no trial expiry, the process does **not** sleep, and the
filesystem is **persistent** (so the SQLite DB keeps all users/messages
forever). It runs long-lived Node.js processes and supports WebSockets, which
is exactly what SAssist needs.

## 1. Create the account
- Sign up at https://www.alwaysdata.com/en/ → choose the **Free (100 MB)** plan.
- No card is asked for the free plan.

## 2. Get the code onto your account
Open the web SSH terminal (or connect via SSH: `ssh <account>@ssh-<account>.alwaysdata.net`):
```bash
cd ~
git clone https://github.com/Dimasick-git/SAssist.git
cd SAssist/server
npm install
npm run build
mkdir -p ~/sassist-data
```

## 3. Create the site (the long-running server)
In the admin panel → **Web → Sites → Add a site**:
- **Type**: Node.js
- **Node.js version**: 20 (or newer)
- **Command**:
  ```
  node /home/<account>/SAssist/server/dist/src/index.js
  ```
- **Working directory**: `/home/<account>/SAssist/server`
- **Addresses**: add a subdomain, e.g. `<account>.alwaysdata.net`
- **Environment variables**:
  | name | value |
  |---|---|
  | `DATA_DIR` | `/home/<account>/sassist-data` |
  | `NODE_ENV` | `production` |
  | *(optional)* `PREMIUM_CODE` | your premium code |
  | *(optional)* `SMTP_HOST` … | real e-mail OTP |

The server automatically binds to the `HOST`/`PORT` alwaysdata injects — no
extra config needed.

## 4. Start it
Click **Restart** on the site. It's live at:
```
https://<account>.alwaysdata.net
```
In the app: **Sign in → Server settings** → `wss://<account>.alwaysdata.net`.

Login works immediately — with no SMTP configured, the one-time code is shown
in the app.

## Updating later
```bash
cd ~/SAssist && git pull && cd server && npm install && npm run build
# then Restart the site in the panel
```

## Notes / limits
- **100 MB** total. SQLite + text chat is tiny, but uploaded media (up to
  30 MB each) shares that space — prune `~/sassist-data/media` if it fills up,
  or keep media off (text-only stays well under the limit).
- Data is durable: everything under your home directory survives restarts and
  redeploys — nothing resets.
- Want more room for free forever with a card? See the Oracle Cloud Always Free
  option in the main README (full VM + Docker).
