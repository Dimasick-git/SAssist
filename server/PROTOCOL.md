# SAssist Protocol

## English — short reference

REST handles authentication, profiles and media. WebSocket carries JSON chat frames. Authenticate every socket with `join { token }`. The current Android client uses `clientId` to deduplicate offline retries.

---

# Протокол SAssist — русская справка

REST и WebSocket работают на одном порту. REST предназначен для входа, профиля и медиа; WebSocket — для чатов, DM, presence, read receipts и signalling звонков.

## REST

| Метод | Авторизация | Назначение |
|---|---|---|
| `GET /health` | нет | Проверка сервера. |
| `POST /auth/request` | нет | Запрос OTP: `{method, identifier}`. |
| `POST /auth/verify` | нет | Проверка OTP: `{method, identifier, code, username?}`. |
| `GET /handle/check?handle=x` | нет | Проверка `@username`. |
| `POST /handle/claim` | Bearer | Занять `@username`. |
| `GET /profile` | Bearer | Текущий профиль. |
| `GET /users/:id` | Bearer | Публичный профиль пользователя. |
| `POST /profile` | Bearer | Имя, bio, цвет, avatar/banner id. |
| `POST /upload/raw?...` | Bearer | Быстрая бинарная загрузка, максимум 30 МБ. |
| `POST /upload` | Bearer | Legacy JSON/base64 fallback. |
| `GET /media/:id` | нет | Файл, `Range` поддерживается. |

Bearer token передаётся заголовком `Authorization: Bearer <token>`.

## WebSocket: клиент → сервер

| `type` | Основные поля | Назначение |
|---|---|---|
| `join` | `token` | Авторизация WebSocket. |
| `send` | `channel`, `text`, `clientId?`, `media?`, `replyTo?` | Сообщение. |
| `switchChannel` | `channel` | Выбор канала. |
| `history` | `channel`, `since?`, `limit?` | История или incremental sync. |
| `listChannels` | — | Список каналов и DM. |
| `typing` | `channel` | Индикатор печати. |
| `react` | `channel`, `messageId`, `emoji` | Реакция. |
| `read` | `channel`, `messageIds[]` | Read receipt. |
| `startDm` | `userId` | Создать/открыть личный чат. |
| `callSignal` | `channel`, `payload` | Зашифрованный offer/answer/ICE только для DM. |
| `callEnd` | `channel` | Завершение звонка. |

## WebSocket: сервер → клиент

`welcome`, `history`, `message`, `reaction`, `read`, `presence`, `typing`, `channels`, `dmStarted`, `callSignal`, `callEnd`, `error`.

## Offline clientId

Клиент кладёт UUID в `clientId` при отправке. Сервер считает пару `(userId, clientId)` уникальной и при повторной отправке возвращает уже существующее сообщение отправителю вместо создания дубля. Это позволяет без потерь повторять офлайн-очередь.

## Приватность

Android-клиент шифрует текст в формате `v1:<salt>:<iv>:<ciphertext>`. Сервер хранит и пересылает строку как непрозрачные данные. Для настоящего общего шифрования участники должны установить одинаковый ключ комнаты. Медиа не шифруются этим форматом.

Стандартные публичные каналы: `general`, `code-help`, `showtime`.
