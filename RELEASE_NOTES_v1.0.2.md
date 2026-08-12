# SAssist v1.0.2

## Русский

Обновлена release-сборка Android-клиента до версии `1.0.2` с `versionCode 3`.

Публичный backend по умолчанию остаётся доступен по адресу `wss://sassist-labs.onrender.com`. Серверный Docker deployment и health-check `/health` сохранены совместимыми с Render Free. Настройки сервера по-прежнему можно изменить на экране входа.

Render Free подходит для тестирования и демонстрации: после периода простоя сервис может заснуть, а первый запрос может запускаться с задержкой. Локальные SQLite-данные и медиа не следует считать постоянным хранилищем на бесплатном тарифе.

## English

The Android release is bumped to `1.0.2` with `versionCode 3`.

The default public backend remains `wss://sassist-labs.onrender.com`. The Docker deployment and `/health` check remain compatible with Render Free. Users can still override the server address on the sign-in screen.

Render Free is suitable for testing and demonstrations: the service may spin down after inactivity and the first request can be delayed. Local SQLite data and media must not be treated as durable storage on the free tier.
