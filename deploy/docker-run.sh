#!/usr/bin/env bash
# Start the standalone MeterSphere image with its embedded WeCom connector.
# Usage: ./deploy/docker-run.sh /opt/metersphere/env.prod

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
: "${SPRING_PROFILES_ACTIVE:=local}"

mkdir -p "${MS_CONF_DIR}" "${MS_LOG_DIR}"

if [[ ! -f "${MS_CONF_DIR}/metersphere.properties" ]]; then
  echo "Missing ${MS_CONF_DIR}/metersphere.properties" >&2
  echo "Copy deploy/conf/metersphere.properties.example and edit dependency settings." >&2
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

echo "Starting ${MS_CONTAINER_NAME} with standalone file configuration ..."
docker run -d \
  --name "${MS_CONTAINER_NAME}" \
  --restart unless-stopped \
  -p "${MS_HTTP_PORT}:8081" \
  -p "${MS_TCP_PORT}:7071" \
  -e "SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}" \
  -e "MS_CONFIG_DIR=/opt/metersphere/conf" \
  -e "MS_REDISSON_CONFIG=file:/opt/metersphere/conf/redisson.yml" \
  -e "MYSQL_HOST=${MYSQL_HOST:-127.0.0.1}" \
  -e "MYSQL_PORT=${MYSQL_PORT:-3306}" \
  -e "MYSQL_USER=${MYSQL_USER:-root}" \
  -e "MYSQL_PASSWORD=${MYSQL_PASSWORD:-}" \
  -e "KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-127.0.0.1:9092}" \
  -e "MINIO_ENDPOINT=${MINIO_ENDPOINT:-http://127.0.0.1:9000}" \
  -e "MINIO_ACCESS_KEY=${MINIO_ACCESS_KEY:-}" \
  -e "MINIO_SECRET_KEY=${MINIO_SECRET_KEY:-}" \
  -v "${MS_CONF_DIR}:/opt/metersphere/conf" \
  -v "${MS_LOG_DIR}:/opt/metersphere/logs" \
  "${MS_IMAGE}"

echo "Container started. MeterSphere and the embedded WeCom connector share this container."
echo "Tail logs with: docker logs -f ${MS_CONTAINER_NAME}"
