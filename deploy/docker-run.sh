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

mkdir -p "${MS_CONF_DIR}" "${MS_LOG_DIR}"

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
  "${MS_IMAGE}"

echo "Container started. Tail logs with:"
echo "  docker logs -f ${MS_CONTAINER_NAME}"
