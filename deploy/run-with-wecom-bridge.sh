#!/bin/sh

set -eu

BRIDGE_HOME="${MS_WECOM_BRIDGE_HOME:-/opt/wecom-bot-bridge}"
BRIDGE_PORT="${MS_WECOM_BRIDGE_PORT:-8095}"
MASTER_KEY_FILE="${MS_WECOM_SECRET_MASTER_KEY_FILE:-/opt/metersphere/conf/.wecom-master-key}"

mkdir -p "$(dirname "${MASTER_KEY_FILE}")"
if [ ! -s "${MASTER_KEY_FILE}" ]; then
  node -e "process.stdout.write(require('node:crypto').randomBytes(48).toString('base64'))" > "${MASTER_KEY_FILE}"
  chmod 600 "${MASTER_KEY_FILE}"
fi

export MS_WECOM_SECRET_MASTER_KEY_FILE="${MASTER_KEY_FILE}"
export MS_WECOM_BRIDGE_TOKEN="${MS_WECOM_BRIDGE_TOKEN:-$(node -e "process.stdout.write(require('node:crypto').randomBytes(32).toString('hex'))")}"
export MS_WECOM_BRIDGE_CALLBACK_TOKEN="${MS_WECOM_BRIDGE_CALLBACK_TOKEN:-$(node -e "process.stdout.write(require('node:crypto').randomBytes(32).toString('hex'))")}"
export MS_WECOM_BRIDGE_URL="http://127.0.0.1:${BRIDGE_PORT}"
export MS_WECOM_CALLBACK_BASE_URL="http://127.0.0.1:${SERVER_PORT:-8081}"
export MS_WECOM_BRIDGE_PORT="${BRIDGE_PORT}"
export MS_WECOM_BOT_ENABLED="false"
export MS_WECOM_IDEMPOTENCY_FILE="${MS_WECOM_IDEMPOTENCY_FILE:-/tmp/wecom-bot-idempotency.json}"

node "${BRIDGE_HOME}/src/main.mjs" &
BRIDGE_PID=$!

shutdown() {
  kill -TERM "${JAVA_PID:-}" "${BRIDGE_PID}" 2>/dev/null || true
}

trap shutdown INT TERM EXIT

/deployments/run-java.sh &
JAVA_PID=$!
wait "${JAVA_PID}"
EXIT_CODE=$?

trap - INT TERM EXIT
kill -TERM "${BRIDGE_PID}" 2>/dev/null || true
wait "${BRIDGE_PID}" 2>/dev/null || true
exit "${EXIT_CODE}"
