# MeterSphere Agent Bridge

Agent Bridge runs on the user's device and creates an outbound WSS connection to MeterSphere. Third-party
Codex/Cursor/WorkBuddy credentials remain owned by their official local client or OS credential store; Bridge never
uploads credential files, cookies, passwords, or provider tokens.

## Supported modes

- Codex CLI: official `codex login`, `codex login status`, `codex logout`, and `codex exec --json` flow. Runs in an
  ephemeral, read-only sandbox and a dedicated temporary working directory.
- Cursor Agent CLI: official `cursor-agent login/status/logout` and `--print --output-format stream-json` flow. It is
  disabled by default because Cursor headless mode can access write/bash tools; enable only inside an OS sandbox.
- WorkBuddy: detection/status boundary is present, but managed SDK execution remains disabled until a separately
  licensed Managed Agents API credential and real-account acceptance are supplied. A consumer membership is not
  treated as an API credential.

No provider is advertised as production-ready merely because its command exists. The platform Feature Flags and
real-account acceptance evidence are the source of truth.

## Configuration

Copy `config.example.json` to the per-user Bridge data directory, pair the device through the platform UI, then run
`npm start`. Do not put provider tokens in this file.

1. In MeterSphere personal settings, create a one-time pairing code.
2. Run `npm run pair -- XXXX-XXXX-XXXX` on the user's device.
3. Run `npm start` and keep Bridge running.
4. Create the Provider connection in MeterSphere and click **Official sign-in**. Bridge launches the Provider's
   official local CLI login; MeterSphere receives only the resulting connected/expired status and capability flags.

The platform must set `ms.ai.user-agent.enabled=true` plus the individual Provider flag. Multi-node deployments must
give each Gateway a unique `ms.ai.user-agent.gateway-node-id` and share Redis. Provider flags must remain disabled
until the corresponding real-account acceptance is complete.
