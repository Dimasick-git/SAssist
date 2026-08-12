# alwaysdata Self-Hosting

## English — short guide

alwaysdata can be used as an alternative persistent Node.js host. Check the provider's current free-plan terms before relying on it. Clone this repository, build `server`, configure a Node.js site, and set a persistent `DATA_DIR`.

---

# Развёртывание на alwaysdata — русская инструкция

Этот вариант нужен, если вы хотите самостоятельный сервер с постоянным каталогом данных вместо ephemeral Render Free. Перед началом самостоятельно проверьте актуальные ограничения и условия бесплатного тарифа на сайте провайдера.

## Шаги

```bash
cd ~
git clone https://github.com/Dimasick-git/SAssist.git
cd SAssist/server
npm install
npm run build
mkdir -p ~/sassist-data
```

В панели alwaysdata создайте Node.js site со следующими параметрами:

| Поле | Значение |
|---|---|
| Команда | `node /home/<account>/SAssist/server/dist/src/index.js` |
| Рабочий каталог | `/home/<account>/SAssist/server` |
| `DATA_DIR` | `/home/<account>/sassist-data` |
| `NODE_ENV` | `production` |
| SMTP/SMS | Настройте для реальной OTP-доставки. |
| `DISABLE_DEV_CODE` | `1` для публичной установки. |

После запуска укажите в SAssist адрес `wss://<ваш-домен>`. Сохраните резервную копию `~/sassist-data`: там находятся SQLite, файлы медиа и секрет подписи токенов.

## Обновление

```bash
cd ~/SAssist
git pull
cd server
npm install
npm run build
```

Затем перезапустите Node.js site в панели. Файлы больше 30 МБ сервер не принимает; для крупных медиа подключайте внешнее object storage.
