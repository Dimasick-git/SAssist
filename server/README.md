# SAssist server

Coder-focused chat backend. **WebSocket + JSON**, deliberately tiny so a future
Nintendo Switch (C++/libnx) client can speak the same protocol.

## Run
```bash
npm install
npm run dev      # hot-reload on :8080
# or
npm run build && npm start
```

Health check: `GET http://localhost:8080/` → `SAssist server ok`

## Smoke test
```bash
npm run build && npm start &   # start server
npm run smoke                  # connect, join, send, verify round-trip
```

Protocol: see [PROTOCOL.md](./PROTOCOL.md).
Deploy: `Dockerfile` included (Railway-ready).
