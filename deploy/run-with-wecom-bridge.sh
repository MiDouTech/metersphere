#!/bin/sh

set -eu

BRIDGE_HOME="${MS_WECOM_BRIDGE_HOME:-/opt/wecom-bot-bridge}"
BRIDGE_PORT="${MS_WECOM_BRIDGE_PORT:-8095}"
BRIDGE_START_TIMEOUT_SECONDS="${MS_WECOM_BRIDGE_START_TIMEOUT_SECONDS:-30}"
MASTER_KEY_FILE="${MS_WECOM_SECRET_MASTER_KEY_FILE:-/opt/metersphere/conf/.wecom-master-key}"
RUNTIME_UID="${MS_WECOM_RUNTIME_UID:-1000}"
RUNTIME_GID="${MS_WECOM_RUNTIME_GID:-1000}"
BRIDGE_PID=""
JAVA_PID=""

fail() {
  echo "wecom bridge startup error: $*" >&2
  exit 1
}

terminate_process() {
  pid="${1:-}"
  if [ -n "${pid}" ] && kill -0 "${pid}" 2>/dev/null; then
    kill -TERM "${pid}" 2>/dev/null || true
  fi
}

shutdown() {
  terminate_process "${JAVA_PID}"
  terminate_process "${BRIDGE_PID}"
  [ -z "${JAVA_PID}" ] || wait "${JAVA_PID}" 2>/dev/null || true
  [ -z "${BRIDGE_PID}" ] || wait "${BRIDGE_PID}" 2>/dev/null || true
}

trap 'shutdown; exit 143' INT TERM
trap shutdown EXIT

node --version >/dev/null 2>&1 || fail "Node.js runtime is unavailable"

mkdir -p "$(dirname "${MASTER_KEY_FILE}")"
if [ ! -s "${MASTER_KEY_FILE}" ]; then
  if ! node -e "process.stdout.write(require('node:crypto').randomBytes(48).toString('base64'))" > "${MASTER_KEY_FILE}"; then
    fail "cannot create ${MASTER_KEY_FILE}; mount its parent read-write or pre-create the file on the host"
  fi
fi

if ! chown "${RUNTIME_UID}:${RUNTIME_GID}" "${MASTER_KEY_FILE}" 2>/dev/null; then
  KEY_UID="$(stat -c '%u' "${MASTER_KEY_FILE}")"
  KEY_GID="$(stat -c '%g' "${MASTER_KEY_FILE}")"
  if [ "${KEY_UID}" != "${RUNTIME_UID}" ] || [ "${KEY_GID}" != "${RUNTIME_GID}" ]; then
    fail "${MASTER_KEY_FILE} must be owned by ${RUNTIME_UID}:${RUNTIME_GID}; current owner is ${KEY_UID}:${KEY_GID}"
  fi
fi

if ! chmod 600 "${MASTER_KEY_FILE}" 2>/dev/null; then
  KEY_MODE="$(stat -c '%a' "${MASTER_KEY_FILE}")"
  [ "${KEY_MODE}" = "600" ] || fail "${MASTER_KEY_FILE} must have mode 600; current mode is ${KEY_MODE}"
fi

export MS_WECOM_SECRET_MASTER_KEY_FILE="${MASTER_KEY_FILE}"
if [ -z "${MS_WECOM_BRIDGE_TOKEN:-}" ]; then
  MS_WECOM_BRIDGE_TOKEN="$(node -e "process.stdout.write(require('node:crypto').randomBytes(32).toString('hex'))")"
fi
if [ -z "${MS_WECOM_BRIDGE_CALLBACK_TOKEN:-}" ]; then
  MS_WECOM_BRIDGE_CALLBACK_TOKEN="$(node -e "process.stdout.write(require('node:crypto').randomBytes(32).toString('hex'))")"
fi
export MS_WECOM_BRIDGE_TOKEN MS_WECOM_BRIDGE_CALLBACK_TOKEN
export MS_WECOM_BRIDGE_URL="http://127.0.0.1:${BRIDGE_PORT}"
export MS_WECOM_CALLBACK_BASE_URL="http://127.0.0.1:${SERVER_PORT:-8081}"
export MS_WECOM_BRIDGE_PORT="${BRIDGE_PORT}"
export MS_WECOM_BOT_ENABLED="false"
export MS_WECOM_IDEMPOTENCY_FILE="${MS_WECOM_IDEMPOTENCY_FILE:-/tmp/wecom-bot-idempotency.json}"

node "${BRIDGE_HOME}/src/main.mjs" &
BRIDGE_PID=$!

BRIDGE_READY="false"
ELAPSED=0
while [ "${ELAPSED}" -lt "${BRIDGE_START_TIMEOUT_SECONDS}" ]; do
  if ! kill -0 "${BRIDGE_PID}" 2>/dev/null; then
    set +e
    wait "${BRIDGE_PID}"
    BRIDGE_EXIT_CODE=$?
    set -e
    fail "Bridge exited before becoming ready with code ${BRIDGE_EXIT_CODE}"
  fi
  if node -e "fetch('http://127.0.0.1:${BRIDGE_PORT}/health/live').then(r => process.exit(r.ok ? 0 : 1)).catch(() => process.exit(1))"; then
    BRIDGE_READY="true"
    break
  fi
  sleep 1
  ELAPSED=$((ELAPSED + 1))
done

[ "${BRIDGE_READY}" = "true" ] || fail "Bridge did not become ready within ${BRIDGE_START_TIMEOUT_SECONDS} seconds"

/deployments/run-java.sh &
JAVA_PID=$!

while :; do
  if ! kill -0 "${BRIDGE_PID}" 2>/dev/null; then
    set +e
    wait "${BRIDGE_PID}"
    BRIDGE_EXIT_CODE=$?
    set -e
    echo "wecom bridge exited unexpectedly with code ${BRIDGE_EXIT_CODE}" >&2
    terminate_process "${JAVA_PID}"
    wait "${JAVA_PID}" 2>/dev/null || true
    trap - INT TERM EXIT
    [ "${BRIDGE_EXIT_CODE}" -ne 0 ] || BRIDGE_EXIT_CODE=1
    exit "${BRIDGE_EXIT_CODE}"
  fi

  if ! kill -0 "${JAVA_PID}" 2>/dev/null; then
    set +e
    wait "${JAVA_PID}"
    JAVA_EXIT_CODE=$?
    set -e
    terminate_process "${BRIDGE_PID}"
    wait "${BRIDGE_PID}" 2>/dev/null || true
    trap - INT TERM EXIT
    exit "${JAVA_EXIT_CODE}"
  fi

  sleep 1
done
