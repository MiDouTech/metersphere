# MeterSphere WeCom Bot Bridge

Internal Node.js bridge for the official `@wecom/aibot-node-sdk`. It owns the WebSocket connection only; MeterSphere Java services own permissions, rules, timers and the durable outbox.

Required runtime variables:

```text
MS_WECOM_BRIDGE_TOKEN
MS_WECOM_BRIDGE_CALLBACK_TOKEN
MS_WECOM_CALLBACK_BASE_URL
```

Bot credentials can be injected at startup with `MS_WECOM_BOT_ID` and `MS_WECOM_BOT_SECRET`, or supplied by the protected `/v1/configure` endpoint. Never put real values in Compose files or Git.

Run locally:

```bash
npm ci
npm test
npm start
```
