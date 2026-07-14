#!/usr/bin/env bash
# Start MeterSphere backend with file-based config (no Nacos).
# Usage on server:
#   cp deploy/conf/metersphere.properties.example /opt/metersphere/conf/metersphere.properties
#   cp deploy/conf/redisson.yml.example /opt/metersphere/conf/redisson.yml
#   vim /opt/metersphere/conf/metersphere.properties   # real MySQL/Kafka/MinIO
#   vim /opt/metersphere/conf/redisson.yml             # real Redis
#   cp deploy/env.file.example /opt/metersphere/env.file
#   vim /opt/metersphere/env.file
#   ./deploy/docker-run-file.sh /opt/metersphere/env.file

set -euo pipefail

ENV_FILE="${1:-/opt/metersphere/env.file}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Env file not found: ${ENV_FILE}" >&2
  echo "Copy deploy/env.file.example and fill in real values first." >&2
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
: "${MS_HTTP_BIND:=}"
: "${SPRING_PROFILES_ACTIVE:=local}"

if [[ "${SPRING_PROFILES_ACTIVE}" != "local" ]]; then
  echo "Warning: SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE} (expected local for no-Nacos)." >&2
fi

mkdir -p "${MS_CONF_DIR}" "${MS_LOG_DIR}"

if [[ ! -f "${MS_CONF_DIR}/metersphere.properties" ]]; then
  echo "Missing ${MS_CONF_DIR}/metersphere.properties" >&2
  echo "Copy deploy/conf/metersphere.properties.example and edit DB/Kafka/MinIO." >&2
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

HTTP_PUBLISH="${MS_HTTP_PORT}:8081"
TCP_PUBLISH="${MS_TCP_PORT}:7071"
if [[ -n "${MS_HTTP_BIND}" ]]; then
  HTTP_PUBLISH="${MS_HTTP_BIND}${MS_HTTP_PORT}:8081"
  TCP_PUBLISH="${MS_HTTP_BIND}${MS_TCP_PORT}:7071"
fi

echo "Starting ${MS_CONTAINER_NAME} with file config (profile=${SPRING_PROFILES_ACTIVE}) ..."
docker run -d \
  --name "${MS_CONTAINER_NAME}" \
  --restart unless-stopped \
  -p "${HTTP_PUBLISH}" \
  -p "${TCP_PUBLISH}" \
  -e "SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}" \
  -v "${MS_CONF_DIR}:/opt/metersphere/conf" \
  -v "${MS_LOG_DIR}:/opt/metersphere/logs" \
  "${MS_IMAGE}"

echo "Container started. Tail logs with:"
echo "  docker logs -f ${MS_CONTAINER_NAME}"
echo "Verify:"
echo "  curl -I http://127.0.0.1:${MS_HTTP_PORT}/"
