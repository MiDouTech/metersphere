#!/usr/bin/env bash
# Start MeterSphere backend with Nacos profile (Solution A).
# Usage on server:
#   cp deploy/env.prod.example /opt/metersphere/env.prod
#   vim /opt/metersphere/env.prod
#   cp deploy/conf/redisson.yml.example /opt/metersphere/conf/redisson.yml
#   vim /opt/metersphere/conf/redisson.yml
#   ./deploy/docker-run.sh /opt/metersphere/env.prod

set -euo pipefail

ENV_FILE="${1:-/opt/metersphere/env.prod}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Env file not found: ${ENV_FILE}" >&2
  echo "Copy deploy/env.prod.example and fill in real values first." >&2
  exit 1
fi

# shellcheck disable=SC1090
source "${ENV_FILE}"

: "${MS_IMAGE:?MS_IMAGE is required}"
: "${MS_CONTAINER_NAME:=metersphere}"
: "${MS_CONF_DIR:=/opt/metersphere/conf}"
: "${MS_LOG_DIR:=/opt/metersphere/logs}"
: "${MS_HTTP_PORT:=8081}"
: "${MS_TCP_PORT:=7071}"
: "${SPRING_PROFILES_ACTIVE:=nacos}"
: "${NACOS_SERVER_ADDR:?NACOS_SERVER_ADDR is required}"
: "${NACOS_NAMESPACE:=prod}"
: "${NACOS_GROUP:=METERSPHERE}"
: "${MS_DOCKER_NETWORK:=metersphere-internal}"

mkdir -p "${MS_CONF_DIR}" "${MS_LOG_DIR}"

for secret_file in "${MS_WECOM_BRIDGE_TOKEN_FILE:-}" "${MS_WECOM_BRIDGE_CALLBACK_TOKEN_FILE:-}" "${MS_WECOM_SECRET_MASTER_KEY_FILE:-}"; do
  if [[ -n "${secret_file}" && ! -f "${secret_file}" ]]; then
    echo "Missing WeCom secret file: ${secret_file}" >&2
    exit 1
  fi
done

if ! docker network inspect "${MS_DOCKER_NETWORK}" >/dev/null 2>&1; then
  docker network create "${MS_DOCKER_NETWORK}" >/dev/null
fi

WECOM_ARGS=()
if [[ -n "${MS_WECOM_BRIDGE_TOKEN_FILE:-}" && -n "${MS_WECOM_BRIDGE_CALLBACK_TOKEN_FILE:-}" && -n "${MS_WECOM_SECRET_MASTER_KEY_FILE:-}" ]]; then
  WECOM_ARGS+=(
    -e "MS_WECOM_BRIDGE_URL=${MS_WECOM_BRIDGE_URL:-http://wecom-bot-bridge:8095}"
    -e "MS_WECOM_BRIDGE_TOKEN_FILE=/run/secrets/wecom_bridge_token"
    -e "MS_WECOM_BRIDGE_CALLBACK_TOKEN_FILE=/run/secrets/wecom_callback_token"
    -e "MS_WECOM_SECRET_MASTER_KEY_FILE=/run/secrets/wecom_master_key"
    -v "${MS_WECOM_BRIDGE_TOKEN_FILE}:/run/secrets/wecom_bridge_token:ro"
    -v "${MS_WECOM_BRIDGE_CALLBACK_TOKEN_FILE}:/run/secrets/wecom_callback_token:ro"
    -v "${MS_WECOM_SECRET_MASTER_KEY_FILE}:/run/secrets/wecom_master_key:ro"
  )
elif [[ -n "${MS_WECOM_BRIDGE_TOKEN_FILE:-}${MS_WECOM_BRIDGE_CALLBACK_TOKEN_FILE:-}${MS_WECOM_SECRET_MASTER_KEY_FILE:-}" ]]; then
  echo "All three WeCom secret files must be configured together." >&2
  exit 1
fi

if [[ ! -f "${MS_CONF_DIR}/redisson.yml" ]]; then
  echo "Missing ${MS_CONF_DIR}/redisson.yml" >&2
  echo "Copy deploy/conf/redisson.yml.example and edit Redis connection." >&2
  exit 1
fi

if docker ps -a --format '{{.Names}}' | grep -qx "${MS_CONTAINER_NAME}"; then
  echo "Removing existing container: ${MS_CONTAINER_NAME}"
  docker rm -f "${MS_CONTAINER_NAME}" >/dev/null
fi

echo "Starting ${MS_CONTAINER_NAME} with Nacos profile ..."
docker run -d \
  --name "${MS_CONTAINER_NAME}" \
  --restart unless-stopped \
  --network "${MS_DOCKER_NETWORK}" \
  -p "${MS_HTTP_PORT}:8081" \
  -p "${MS_TCP_PORT}:7071" \
  -e "SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}" \
  -e "NACOS_SERVER_ADDR=${NACOS_SERVER_ADDR}" \
  -e "NACOS_NAMESPACE=${NACOS_NAMESPACE}" \
  -e "NACOS_GROUP=${NACOS_GROUP}" \
  -e "NACOS_USERNAME=${NACOS_USERNAME:-}" \
  -e "NACOS_PASSWORD=${NACOS_PASSWORD:-}" \
  -v "${MS_CONF_DIR}:/opt/metersphere/conf" \
  -v "${MS_LOG_DIR}:/opt/metersphere/logs" \
  "${WECOM_ARGS[@]}" \
  "${MS_IMAGE}"

echo "Container started. Tail logs with:"
echo "  docker logs -f ${MS_CONTAINER_NAME}"
