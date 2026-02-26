# Migration Plan: Legacy Android_Pc → RemoteControl v2

## Repository tree summary (current)
- `app/` — legacy Android app (mixed features, unstable flows).
- `server/` — legacy ASP.NET Core Windows server.
- `start_server.cmd` — legacy launcher.

## New target architecture

## Server (`server/`) — .NET 10 / Windows-first
- **TargetFramework:** `net10.0-windows`
- **Host:** ASP.NET Core + Kestrel
- **Core layers**
  - `Security`: pairing, token issuance, replay-protection, rate-limiting.
  - `Services`: `ISystemService`, `IInputService`, `IScreenService`, `IMediaService`, `IProcessService`, `IFileService`, `IClipboardService`.
  - `API`: `/api/v1/...` endpoints with Swagger/OpenAPI.
  - `Audit`: Serilog command audit (file + console).
- **Security defaults**
  - LAN-safe default binding.
  - Time-limited rotating pairing code.
  - Bearer token auth.
  - Sensitive-command replay protection (`X-Timestamp`, `X-Nonce`).
  - Per-client rate limiting.

## Android (`app/`) — rewritten client (new package)
- **Package:** `com.pcremote.client`
- **Architecture:** MVVM + Coroutines + Flow + Retrofit/OkHttp
- **Secure storage:** EncryptedSharedPreferences
- **Modules/folders**
  - `data/api`
  - `data/repository`
  - `domain/model`
  - `ui/screens`
  - `ui/viewmodel`
- **First-priority UX flow**
  1. Onboarding + Pairing
  2. Dashboard (status, screenshot, camera photo, power controls)
  3. Then feature expansion (input, files, media, process, clipboard)

## Legacy handling
- Legacy logic is replaced in-place by new default startup paths.
- New documentation and scripts become canonical run path.

## Milestones
1. Server skeleton: auth/pairing/replay/security + status endpoint.
2. Android rewrite baseline: onboarding/pairing/dashboard.
3. Core command features: screenshot/camera/power.
4. Remaining feature set: input/media/process/files/clipboard.
5. Docs/scripts/tests hardening.
