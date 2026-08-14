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

## Windows phase-one installation

The user-facing product name is **MeterSphere Agent**. The web UI detects it through the registered
`metersphere-agent://` protocol and keeps the one-time pairing code out of visible UI and browser storage.

Release packaging is driven by `scripts/windows/build-package.ps1` and requires an official Windows Node.js runtime
directory so end users do not need Node.js installed. Publish the ZIP through **System Settings > Agent Integration >
Agent Package Management**. MeterSphere stores the package in its MinIO file repository and serves the active
package through an authenticated download endpoint, so package releases remain independent from backend builds. The
legacy `ms.ai.user-agent.bridge.windows-download-url` setting remains available as a fallback. Sign a production
executable installer in the release pipeline with the organization's code-signing certificate. The repository does
not contain or fabricate signing credentials.

For development, extract the package and run `scripts/windows/install.ps1`. This registers the protocol and a
current-user auto-start entry. Use `scripts/windows/uninstall.ps1`; add `-RemoveApplicationData` only when device
identity recovery is not required. A production release should wrap the same actions in a signed MSIX/EXE.

### Internal unsigned distribution

For an internal deployment, an unsigned ZIP is sufficient. Build it on Windows with an x64 Node.js 20+ runtime:

```powershell
.\scripts\windows\build-package.ps1 `
  -OutputDirectory C:\tmp\metersphere-agent-release `
  -NodeRuntimeDirectory C:\path\to\node-runtime
```

The output contains the ZIP, a JSON manifest, and a `.sha256` file. Upload the ZIP in the system administration page,
enter the version from the manifest, and compare the SHA-256 shown after upload with the generated manifest. The old
external HTTPS mode can still be configured with either the Spring property or its environment-variable form:

```properties
ms.ai.user-agent.bridge.windows-download-url=https://internal.example.com/metersphere-agent-windows-x64.zip
```

```text
MS_AI_USER_AGENT_BRIDGE_WINDOWS_DOWNLOAD_URL=https://internal.example.com/metersphere-agent-windows-x64.zip
```

The user extracts the ZIP and double-clicks `Install-MeterSphere-Agent.cmd`. Installation is per-user under
`%LOCALAPPDATA%\MeterSphere\Agent`; it does not require administrator rights. The wrapper uses a process-scoped
PowerShell execution-policy bypass because files downloaded from an internal site can retain the Windows zone mark.
Organizations that prohibit this must distribute or approve the script through their endpoint-management policy.

After installation, return to MeterSphere and select **Installed - detect again**. The browser protocol request pairs
the device and starts the Bridge. A successful first-phase deployment has all of the following:

- `HKCU\Software\Classes\metersphere-agent` exists.
- The Gateway accepts WebSocket upgrades on `/ai/agent-bridge/ws` over WSS.
- The device appears as `ONLINE` in the personal Agent page.
- The selected Provider CLI is installed locally and can complete its official sign-in.
