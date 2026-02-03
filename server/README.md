# PC Remote Server (.NET / ASP.NET Core)

Сервер для Android-додатку, який приймає команди та керує ПК через локальну мережу або ззовні.

## Вимоги
- Windows 10/11
- .NET 8 SDK

## Швидкий старт (один клік)
Запустіть файл `start_server.cmd` у корені репозиторію або `server\start_server.cmd`.
Сервер підніметься на `http://0.0.0.0:8000` і буде доступний у локальній та зовнішній мережі (за умови відкритого порту).
Якщо потрібно явно вказати адреси для зовнішнього доступу (наприклад, публічну IP-адресу, інший порт або HTTPS), задайте змінну середовища `PC_REMOTE_SERVER_URLS` зі списком адрес через кому або крапку з комою.
Приклад: `PC_REMOTE_SERVER_URLS=http://0.0.0.0:8000;http://[::]:8000`.

> Якщо змінна `PC_REMOTE_API_TOKEN` не встановлена, використовується дефолтний токен `change-me`.

## Налаштування токена
```cmd
set PC_REMOTE_API_TOKEN=your-secret-token
```

## Запуск вручну
```cmd
cd server

dotnet run --project PCRemoteServer.csproj
```

## Підключення з внутрішньої та зовнішньої мережі
- Внутрішня мережа: додаток може знайти сервер через UDP broadcast.
- Зовнішня мережа: відкрийте порт `8000/TCP` на роутері та у Windows Firewall.
- Якщо у вас CGNAT/немає публічної IP, найпростіший варіант — VPN/тунель між телефоном і ПК.

## Вбудований тунель (CGNAT)
За замовчуванням сервер піднімає **вбудований SSH-тунель** через `localhost.run` (без ручної інсталяції VPN). Публічну адресу можна отримати через:
- автознайдення в Android (в UDP відповіді `tunnelUrl`),
- або `GET /tunnel/status` (повертає `url`, `status`).

> Потрібен доступ до інтернету, DNS та наявний `ssh`-клієнт у системі; якщо тунель тимчасово не створюється, сервіс повторюватиме спроби.

Щоб вимкнути вбудований тунель, задайте змінну середовища `PC_REMOTE_TUNNEL_ENABLE=0`.

## Рекомендований VPN/тунель (CGNAT)
Якщо потрібен класичний VPN, оптимальний варіант — **Tailscale**: працює через CGNAT, має стабільне з'єднання, мінімум налаштувань та безкоштовний план для персонального використання.

Короткі кроки:
1. Встановіть Tailscale на ПК і смартфон, увійдіть в один акаунт.
2. На ПК перевірте адресу Tailscale (зазвичай `100.x.y.z`) у додатку Tailscale.
3. В Android-додатку введіть адресу `100.x.y.z:8000` і ваш token, підключіться вручну.

Альтернатива: **ZeroTier** (також добре працює через CGNAT, але має більше ручних налаштувань).

## Автовиявлення (Android)
Сервер слухає UDP порт `9999` і відповідає на повідомлення `PC_REMOTE_DISCOVERY` JSON-ом:
```json
{"port":8000,"token":"<token>","ips":["192.168.1.10"],"tunnelUrl":"https://example.lhrtunnel.link"}
```

## Авторизація
- Заголовок: `X-API-Token: <token>`
- Або: `Authorization: Bearer <token>`

### Перевірка токена
```bash
curl -X POST http://localhost:8000/auth \
  -H "Content-Type: application/json" \
  -d '{"token": "your-secret-token"}'
```

## API
### POST /mouse/move
```json
{"x": 100, "y": 200, "duration": 0.1, "absolute": true}
```

### POST /mouse/click
```json
{"button": "left", "clicks": 1, "interval": 0.0, "x": 100, "y": 200}
```

### POST /keyboard/press
```json
{"key": "enter", "presses": 1, "interval": 0.0}
```
або
```json
{"keys": ["ctrl", "shift", "esc"]}
```

### POST /system/volume
```json
{"action": "up", "steps": 2}
```

### POST /system/launch
```json
{"command": "notepad.exe", "args": ["C:\\temp\\notes.txt"]}
```

### GET /system/status
Повертає CPU та RAM статистику.

### GET /screen/screenshot
Повертає PNG-скріншот екрана.

### POST /session/start
```json
{"client_name": "android", "timeout_seconds": 900}
```
Повертає `session_id` та час завершення.

### POST /session/heartbeat
```json
{"session_id": "<uuid>"}
```
Оновлює активність сесії.

### POST /session/end
```json
{"session_id": "<uuid>"}
```
Завершує сесію.

### GET /session/status/{session_id}
Повертає актуальний час завершення або 404/410, якщо сесія неактивна.

### POST /system/power
```json
{"action": "shutdown"}
```
Доступні дії: `shutdown`, `restart`, `lock`, `logoff`, `sleep`, `hibernate`.

### GET /screen/stream
Онлайн-трансляція екрана (multipart PNG). Параметри: `fps`.

### GET /camera/stream
Онлайн-трансляція з камери (MJPEG).
> У поточній реалізації потрібен додатковий модуль/інтеграція камери.

### GET /camera/photo
Повертає фото з камери (JPEG).
> У поточній реалізації потрібен додатковий модуль/інтеграція камери.

### POST /screen/record/start
```json
{"fps": 10, "duration_seconds": 30}
```
Старт запису екрана. Повертає `recording_id`.

### POST /screen/record/stop/{recording_id}
Зупинка запису екрана.

### GET /screen/recordings
Список активних/завершених записів і файлів у `server/recordings` (формат `.mpng`).

## Примітки
- Сервер має працювати у користувацькій сесії (не як сервіс) для керування мишею/клавіатурою.
- Додайте сервер у виключення антивіруса/фаєрвола, якщо потрібно.
