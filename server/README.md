# SAssist Server

## English — short guide

The server is Node.js/TypeScript with WebSocket, a small REST API and SQLite. Run locally with `npm install && npm run dev`, or use Docker from the repository root: `docker compose up -d --build`.

Persistent operation requires a writable persistent `DATA_DIR`. The public Render instance is a free test deployment and does not retain disk data reliably.

---

# Сервер SAssist — русская инструкция

## Назначение

Сервер обслуживает регистрацию по одноразовому коду, профили, медиа, WebSocket-чаты, DM, presence, реакции, историю и signalling звонков. Все долговечные данные self-host варианта лежат в SQLite и каталоге `DATA_DIR`.

## Запуск

```bash
cd server
npm install
npm run dev

# или production-путь
npm run build
npm start
```

Health check: `GET /health` возвращает `SAssist server ok`.

## Docker

Из корня репозитория:

```bash
docker compose up -d --build
```

Контейнер слушает `8080`, а постоянные данные хранятся в Docker volume. Это рекомендуемый вариант для собственного сервера.

## Переменные окружения

| Переменная | Значение по умолчанию | Назначение |
|---|---|---|
| `PORT` | `8080` | Порт HTTP/WebSocket. |
| `HOST` | все интерфейсы | IP привязки, нужен только на отдельных hosting-платформах. |
| `DATA_DIR` | `./data` | SQLite, медиа и секрет подписи токенов. Должен быть постоянным. |
| `AUTH_SECRET` | создаётся автоматически | Подпись токенов. Храните его вместе с `DATA_DIR`. |
| `SMTP_*` | выключено | Реальная отправка OTP по e-mail. |
| `TWILIO_*` | выключено | Реальная отправка OTP по SMS. |
| `DISABLE_DEV_CODE` | `0` | Поставьте `1` на публичном сервере без dev-кодов. |
| `PREMIUM_CODE` | выключено | Только marker для будущих author-only дополнений; обычные функции не блокирует. |

Без SMTP/SMS код может возвращаться клиенту только для разработки. В публичной установке настройте доставку и включите `DISABLE_DEV_CODE=1`.

## Медиа API

Новый быстрый путь — `POST /upload/raw` с заголовком `Authorization: Bearer <token>` и телом из исходных байтов файла. Параметры `mime`, `name`, `kind` и `durationMs` передаются в query string. Это устраняет накладные расходы base64. Старый `POST /upload` с JSON/base64 оставлен как временный fallback для клиентов, которые ещё не обновились.

`GET /media/:id` отвечает с `Accept-Ranges: bytes` и поддерживает `Range`, что позволяет видеоплееру запускаться и перематываться без полной загрузки. Лимит одного файла — **30 МБ**.

## Звонки

WebSocket передаёт `callSignal` и `callEnd` только участникам DM. Сервер не расшифровывает прикладной payload signalling. Медиа-поток звонка проходит через WebRTC между участниками; для сложных NAT-сценариев самостоятельно добавьте TURN.

## Публичный Render backend

Адрес: `https://sassist-labs.onrender.com`, WebSocket: `wss://sassist-labs.onrender.com`.

Render Free подходит для демонстрации и тестирования, но может засыпать и использует ephemeral filesystem. После cold start, redeploy или смены instance SQLite и медиа могут пропасть. Не используйте его как единственное постоянное хранилище.

Для production добавьте persistent database, object storage (S3/R2/B2 или эквивалент) и TURN для звонков. Резервная копия self-host варианта — копия всего `DATA_DIR` (`sassist.db*`, `media/`, `auth_secret`).

## Проверки

```bash
npm run build
npm run smoke
BASE=http://127.0.0.1:8080 WS=ws://127.0.0.1:8080 node dm-smoke.js
BASE=http://127.0.0.1:8080 node media-transport-smoke.js
```

Технические кадры API описаны в [PROTOCOL.md](PROTOCOL.md).
