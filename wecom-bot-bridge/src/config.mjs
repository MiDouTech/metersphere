const integer = (value, fallback) => {
  const parsed = Number.parseInt(value ?? '', 10);
  return Number.isFinite(parsed) ? parsed : fallback;
};

const bool = (value, fallback = false) => {
  if (value === undefined) return fallback;
  return String(value).toLowerCase() === 'true';
};

const secretValue = (env, name) => {
  if (env[name]) return env[name];
  const file = env[`${name}_FILE`];
  if (!file) return '';
  return requireSecretFile(file);
};

const requireSecretFile = (path) => {
  try {
    return readFileSync(path, 'utf8').trim();
  } catch {
    throw new Error(`Unable to read secret file for ${path.split(/[\\/]/).pop()}`);
  }
};

export function loadConfig(env = process.env) {
  const config = {
    port: integer(env.MS_WECOM_BRIDGE_PORT, 8095),
    botId: env.MS_WECOM_BOT_ID ?? '',
    secret: secretValue(env, 'MS_WECOM_BOT_SECRET'),
    enabled: bool(env.MS_WECOM_BOT_ENABLED, Boolean(env.MS_WECOM_BOT_ID && env.MS_WECOM_BOT_SECRET)),
    bridgeToken: secretValue(env, 'MS_WECOM_BRIDGE_TOKEN'),
    callbackToken: secretValue(env, 'MS_WECOM_BRIDGE_CALLBACK_TOKEN'),
    callbackBaseUrl: (env.MS_WECOM_CALLBACK_BASE_URL ?? '').replace(/\/$/, ''),
    requestTimeoutMs: integer(env.MS_WECOM_REQUEST_TIMEOUT_MS, 10000),
    idempotencyTtlMs: integer(env.MS_WECOM_IDEMPOTENCY_TTL_MS, 24 * 60 * 60 * 1000),
    idempotencyFile: env.MS_WECOM_IDEMPOTENCY_FILE ?? '',
    wsUrl: env.MS_WECOM_WS_URL || undefined,
  };
  if (!config.bridgeToken) throw new Error('MS_WECOM_BRIDGE_TOKEN is required');
  if (config.callbackBaseUrl && !config.callbackToken) {
    throw new Error('MS_WECOM_BRIDGE_CALLBACK_TOKEN is required when callbacks are enabled');
  }
  if (config.enabled && (!config.botId || !config.secret)) {
    throw new Error('MS_WECOM_BOT_ID and MS_WECOM_BOT_SECRET are required when enabled');
  }
  return config;
}
import { readFileSync } from 'node:fs';
