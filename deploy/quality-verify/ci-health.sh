#!/usr/bin/env bash
set -euo pipefail
# Deliberately restricted to the ephemeral GitHub runner; never control a local/shared stack.
[[ "${GITHUB_ACTIONS:-}" == "true" && "${GITHUB_RUN_ID:-}" =~ ^[0-9]+$ ]]
cd "$GITHUB_WORKSPACE"
quality_project="msp-ci-${GITHUB_RUN_ID}"
[[ "$quality_project" =~ ^msp-ci-[0-9]+$ ]]
export QUALITY_DB_PASSWORD=quality-test-only
export QUALITY_STORAGE_PASSWORD=quality-storage-only
# Synthetic key for this empty disposable database, never a deployment or historical-data key.
export QUALITY_FACTS_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
quality_phase() {
  jq -n --arg phase "$1" --arg commit "$GITHUB_SHA" --arg time "$(date -u +%FT%TZ)" \
    '{phase:$phase,commit:$commit,time:$time}' > .ci-phase.json
  echo "Runtime acceptance phase: $1"
}
quality_compose=(docker compose -p "$quality_project" -f deploy/quality-verify.compose.yml -f deploy/quality-verify/ci.override.yml)
cleanup() {
  quality_exit_code=$?
  if [ -n "${quality_browser_proxy:-}" ]; then kill "$quality_browser_proxy" 2>/dev/null || true; fi
  "${quality_compose[@]}" logs --no-color > .ci-health-container.log 2>&1 || true
  "${quality_compose[@]}" down --volumes --remove-orphans || quality_exit_code=1
  exit "$quality_exit_code"
}
trap cleanup EXIT
# These IDs belong only to this fresh runner's test job.
if [ -n "${QUALITY_MYSQL_CONTAINER:-}" ]; then docker stop "$QUALITY_MYSQL_CONTAINER"; fi
if [ -n "${QUALITY_REDIS_CONTAINER:-}" ]; then docker stop "$QUALITY_REDIS_CONTAINER"; fi
while read -r quality_container; do
  [ -z "$quality_container" ] || docker stop "$quality_container"
done < <(docker ps -q --filter label=org.testcontainers=true)
quality_dependency=$(mktemp -d "$GITHUB_WORKSPACE/.ci-dependency.XXXXXX")
[[ "$quality_dependency" == "$GITHUB_WORKSPACE"/.ci-dependency.* ]]
(cd "$quality_dependency" && jar xf "$GITHUB_WORKSPACE/backend/app/target/app-3.x.jar")
quality_phase image-build
if [ "${QUALITY_IMAGE_CACHE_HIT:-}" = true ]; then
  quality_phase image-restore
  test "$(jq -r .key .ci-app-image.json)" = "$QUALITY_IMAGE_KEY"
  test "$(jq -r .jarSha256 .ci-app-image.json)" = "$(jq -r .jarSha256 .ci-backend-build.json)"
  timeout --signal=TERM --kill-after=15s 120s docker load -i .ci-app-image.tar
  test "$(docker image inspect msp-quality:verify --format '{{.Id}}')" = "$(jq -r .image .ci-app-image.json)"
else
  timeout --signal=TERM --kill-after=15s 480s docker build -t msp-quality:verify --build-arg "DEPENDENCY=${quality_dependency#"$GITHUB_WORKSPACE"/}" -f Dockerfile.backend .
  if [ -n "${QUALITY_IMAGE_KEY:-}" ]; then
    timeout --signal=TERM --kill-after=15s 120s docker save -o .ci-app-image.tar msp-quality:verify
    jq -n --arg key "$QUALITY_IMAGE_KEY" --arg image "$(docker image inspect msp-quality:verify --format '{{.Id}}')" \
      --arg jar "$(jq -r .jarSha256 .ci-backend-build.json)" '{key:$key,image:$image,jarSha256:$jar}' > .ci-app-image.json
  fi
fi
quality_phase dependency-start
"${quality_compose[@]}" up -d --wait --wait-timeout 180 mysql redis minio kafka
"${quality_compose[@]}" exec -T minio sh -c 'curl -fsS --aws-sigv4 "aws:amz:us-east-1:s3" --user "$MINIO_ROOT_USER:$MINIO_ROOT_PASSWORD" -X PUT http://127.0.0.1:9000/quality-evidence'
quality_phase app-start-and-migrate
"${quality_compose[@]}" up -d --wait --wait-timeout 360 app
curl -fsS http://127.0.0.1:17071/actuator/health/readiness > .ci-readiness.json
jq -e '.status == "UP"' .ci-readiness.json
quality_frontend_status=$(curl -sS -o .ci-index.html -w '%{http_code}' http://127.0.0.1:18081/)
[ "$quality_frontend_status" = 200 ]
grep -Eq 'id="?app"?' .ci-index.html
quality_image=$(docker image inspect msp-quality:verify --format '{{.Id}}')
jq -n --arg commit "$GITHUB_SHA" --arg image "$quality_image" --arg status "$quality_frontend_status" --slurpfile health .ci-readiness.json \
  '{commit:$commit,image:$image,frontendHttpStatus:$status,readiness:$health[0]}' > .ci-health.json
# Enable the existing local-admin entry in this fresh disposable database only.
"${quality_compose[@]}" exec -T mysql sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot metersphere_quality -e "UPDATE system_parameter SET param_value=\"true\" WHERE param_key=\"ui.login.admin.enabled\""'
node scripts/quality-browser-proxy.mjs > .ci-core-proxy.log 2>&1 &
quality_browser_proxy=$!
for quality_attempt in $(seq 1 20); do
  if curl -fsS http://127.0.0.1:15173/ > /dev/null; then break; fi
  sleep 1
done
quality_phase browser-mcp-smoke
timeout --signal=TERM --kill-after=15s 300s node deploy/quality-verify/core-smoke.mjs
