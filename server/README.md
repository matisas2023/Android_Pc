# PC Remote Server (FastAPI)

Сервер приймає команди з Android-додатку та керує ПК через локальну мережу (Wi‑Fi).

## Вимоги
- Windows 10/11
- Python 3.10+

## Встановлення
```bash
cd server
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
```

## Налаштування токена
Сервер читає токен з змінної середовища `PC_REMOTE_API_TOKEN`.

```bash
set PC_REMOTE_API_TOKEN=your-secret-token
```

> Якщо змінну не встановлено, використовується дефолтний токен `change-me`.

## Запуск
```bash
uvicorn main:app --host 0.0.0.0 --port 8000
```

## Автовиявлення для Android-додатку
Сервер відповідає на UDP broadcast `PC_REMOTE_DISCOVERY` (порт `9999`) і повертає
JSON із портом та токеном. Додаток використовує це для автоматичного підключення
без введення IP/токена.

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

## Примітки
- Для керування мишею/клавіатурою сервер має працювати у користувацькій сесії (не як сервіс).
- Додайте сервер у виключення антивіруса/файрвола, якщо потрібно.

## Збірка .exe (PyInstaller)
> Потрібно лише для одноразової збірки на Windows.

```bash
pip install -r requirements-dev.txt
```

```powershell
powershell -ExecutionPolicy Bypass -File .\build_exe.ps1
```

Після збірки файл буде тут:
```
server\dist\pc-remote-server.exe
```
