# Deployment Status

## Current release

The Android client release is `v1.0.2` (`versionCode 3`). The default backend endpoint is `wss://sassist-labs.onrender.com`.

## English — short status

The public test backend is live at `https://sassist-labs.onrender.com` and `wss://sassist-labs.onrender.com`. It is a Render Free deployment. Health, DM/call signalling and raw media transport smoke checks have passed.

The service is not persistent: Render Free can sleep and its filesystem is ephemeral. Use self-hosting with persistent `DATA_DIR` or external database/object storage for permanent use.

---

# Статус deployment на русском

## Текущий релиз

Android-клиент: `v1.0.2`, `versionCode 3`. Адрес backend по умолчанию: `wss://sassist-labs.onrender.com`.

## Активный публичный backend

| Параметр | Значение |
|---|---|
| Организация | SAssist Labs |
| Сервис | `sassist-labs` |
| HTTPS | `https://sassist-labs.onrender.com` |
| WebSocket | `wss://sassist-labs.onrender.com` |
| Тариф | Render Free, без банковской карты |

Проверены `GET /health`, OTP, WebSocket/DM, private call signalling, аватары/профили, raw media upload и HTTP Range-выдача. Android release build собирается в GitHub Actions.

## Ограничения

Render Free может остановить instance после простоя. Первый запрос затем иногда ожидает запуск до минуты. Диск ephemeral, поэтому после restart/redeploy медиа и SQLite могут не сохраниться. Это публичный тестовый backend, а не постоянное хранилище.

Для постоянного проекта используйте Docker/self-host или добавьте persistent database и object storage. Подробное описание — [server/README.md](server/README.md).
