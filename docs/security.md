# Security Model

## Threat model
Primary goal: safe remote control on trusted LAN.

Threats addressed:
- Unauthorized command execution
- Replay of captured sensitive commands
- Brute-force / flooding API

## Controls implemented
- **Pairing code**: short-lived rotating code for first trust bootstrap.
- **Bearer token**: long-lived token issued only after successful pairing.
- **Replay protection**: required `X-Timestamp` + `X-Nonce` on mutating endpoints.
- **Rate limiting**: fixed-window limiter to throttle abusive clients.
- **Audit logging**: command summary, result, client IP logged via Serilog.

## Defaults
- Designed for LAN usage.
- Pairing code endpoint intended for local network use only.
- Dangerous actions (`power`, process kill) require authenticated client.

## WAN guidance
Not enabled by default. If WAN exposure is absolutely needed:
1. Put server behind VPN.
2. Use TLS and reverse proxy.
3. Restrict IP allowlist.
4. Rotate tokens frequently.
