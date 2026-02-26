# PC Remote Control v2 (Android + Windows Server)

End-to-end remote control system:
- **Server:** ASP.NET Core on **.NET 10** (`net10.0-windows`)
- **Android:** Kotlin app (`com.pcremote.client`) with MVVM + Retrofit + Secure storage

## Prerequisites

### Windows server machine
- Windows 10/11
- .NET 10 SDK (or runtime + SDK for build)
- PowerShell 5+

### Android
- Android Studio (latest)
- JDK 17+
- Android SDK configured

## Server quick start

```cmd
cd server
dotnet restore
dotnet build
dotnet run
```

Or use helper script:

```cmd
scripts\start_server.cmd
```

On startup server prints rotating pairing code in console.

## Pair from Android
1. Launch Android app.
2. Tap **Підключитися (1 кнопка)**.
3. App auto-discovers server in LAN (UDP discovery), requests pairing code and pairs automatically.
4. If autodiscovery fails, use manual fallback (`IP:port` + pairing code).

## Security defaults
- Token auth (`Authorization: Bearer <token>`)
- Replay protection for mutating commands (`X-Timestamp`, `X-Nonce`)
- Rate limiting enabled
- Pairing code is short-lived and rotating

## Firewall

Add inbound firewall rule (admin PowerShell):

```powershell
scripts/firewall_add.ps1
```

Remove rule:

```powershell
scripts/firewall_remove.ps1
```

## Core API (v1)
- `GET /api/v1/pairing/code`
- `POST /api/v1/pairing/pair`
- `GET /api/v1/status`
- `GET /api/v1/screen/screenshot`
- `GET /api/v1/camera/photo`
- `POST /api/v1/system/power`

Swagger UI:
- `http://<server>:8000/swagger`

## Common issues
- `401 invalid token`: pair again, verify bearer token.
- `400 timestamp/nonce`: ensure client sends fresh unix timestamp + unique nonce.
- Android build: if SDK missing, set `ANDROID_HOME` / `sdk.dir` in `local.properties`.

## Migration docs
- `docs/migration.md`
- `docs/security.md`
