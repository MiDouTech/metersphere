#!/usr/bin/env bash
# Publish production metersphere.properties to Nacos.
# Usage:
#   export NACOS_SERVER_ADDR=<nacos-host>:8848
#   export NACOS_NAMESPACE=prod
#   export NACOS_USERNAME=nacos
#   export NACOS_PASSWORD=<change-me>
#   ./deploy/seed-nacos-prod.sh /path/to/metersphere.properties

set -euo pipefail

CONFIG_FILE="${1:-deploy/nacos/prod/metersphere.properties}"
NACOS_SERVER_ADDR="${NACOS_SERVER_ADDR:-127.0.0.1:8848}"
NACOS_NAMESPACE="${NACOS_NAMESPACE:-prod}"
NACOS_GROUP="${NACOS_GROUP:-METERSPHERE}"
DATA_ID="metersphere.properties"
NACOS_USERNAME="${NACOS_USERNAME:-}"
NACOS_PASSWORD="${NACOS_PASSWORD:-}"

if [[ ! -f "${CONFIG_FILE}" ]]; then
  echo "Config file not found: ${CONFIG_FILE}" >&2
  exit 1
fi

BASE_URL="http://${NACOS_SERVER_ADDR}"
AUTH_ARGS=()
if [[ -n "${NACOS_USERNAME}" && -n "${NACOS_PASSWORD}" ]]; then
  AUTH_ARGS=(-u "${NACOS_USERNAME}:${NACOS_PASSWORD}")
fi

echo "Checking Nacos readiness at ${BASE_URL} ..."
curl -fsS "${AUTH_ARGS[@]}" \
  "${BASE_URL}/nacos/v1/console/health/readiness" >/dev/null

echo "Ensuring namespace '${NACOS_NAMESPACE}' exists ..."
EXISTING="$(curl -fsS "${AUTH_ARGS[@]}" \
  "${BASE_URL}/nacos/v1/console/namespaces" || true)"
if ! echo "${EXISTING}" | grep -q "\"namespace\":\"${NACOS_NAMESPACE}\""; then
  curl -fsS "${AUTH_ARGS[@]}" -X POST \
    "${BASE_URL}/nacos/v1/console/namespaces" \
    -d "customNamespaceId=${NACOS_NAMESPACE}" \
    -d "namespaceName=${NACOS_NAMESPACE}" \
    -d "namespaceDesc=MeterSphere production" >/dev/null
  echo "Created namespace: ${NACOS_NAMESPACE}"
fi

CONTENT="$(cat "${CONFIG_FILE}")"
echo "Publishing ${DATA_ID} -> namespace=${NACOS_NAMESPACE}, group=${NACOS_GROUP} ..."
curl -fsS "${AUTH_ARGS[@]}" -X POST \
  "${BASE_URL}/nacos/v1/cs/configs" \
  --data-urlencode "dataId=${DATA_ID}" \
  --data-urlencode "group=${NACOS_GROUP}" \
  --data-urlencode "tenant=${NACOS_NAMESPACE}" \
  --data-urlencode "type=properties" \
  --data-urlencode "content@${CONFIG_FILE}" >/dev/null

echo "Done. Verify in Nacos console:"
echo "  ${BASE_URL}/nacos -> namespace=${NACOS_NAMESPACE}, group=${NACOS_GROUP}, dataId=${DATA_ID}"
