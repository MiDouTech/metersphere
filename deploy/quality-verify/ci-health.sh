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
quality_compose=(docker compose -p "$quality_project" -f deploy/quality-verify.compose.yml -f deploy/quality-verify/ci.override.yml)
cleanup() {
  quality_exit_code=$?
  "${quality_compose[@]}" logs --no-color > .ci-health-container.log 2>&1 || true
  "${quality_compose[@]}" down --volumes --remove-orphans || quality_exit_code=1
  exit "$quality_exit_code"
}
trap cleanup EXIT
# These IDs belong only to this fresh runner's test job.
if [ -n "${QUALITY_MYSQL_CONTAINER:-}" ]; then docker stop "$QUALITY_MYSQL_CONTAINER"; fi
while read -r quality_container; do
  [ -z "$quality_container" ] || docker stop "$quality_container"
done < <(docker ps -q --filter label=org.testcontainers=true)
quality_dependency=$(mktemp -d "$GITHUB_WORKSPACE/.ci-dependency.XXXXXX")
[[ "$quality_dependency" == "$GITHUB_WORKSPACE"/.ci-dependency.* ]]
(cd "$quality_dependency" && jar xf "$GITHUB_WORKSPACE/backend/app/target/app-3.x.jar")
docker build -t msp-quality:verify --build-arg "DEPENDENCY=${quality_dependency#"$GITHUB_WORKSPACE"/}" -f Dockerfile.backend .
"${quality_compose[@]}" up -d --wait --wait-timeout 180 mysql redis minio kafka
"${quality_compose[@]}" exec -T minio sh -c 'curl -fsS --aws-sigv4 "aws:amz:us-east-1:s3" --user "$MINIO_ROOT_USER:$MINIO_ROOT_PASSWORD" -X PUT http://127.0.0.1:9000/quality-evidence'
"${quality_compose[@]}" up -d --wait --wait-timeout 360 app
curl -fsS http://127.0.0.1:17071/actuator/health/readiness > .ci-readiness.json
jq -e '.status == "UP"' .ci-readiness.json
quality_frontend_status=$(curl -sS -o .ci-index.html -w '%{http_code}' http://127.0.0.1:18081/)
[ "$quality_frontend_status" = 200 ]
grep -Eq 'id="?app"?' .ci-index.html
quality_image=$(docker image inspect msp-quality:verify --format '{{.Id}}')
jq -n --arg commit "$GITHUB_SHA" --arg image "$quality_image" --arg status "$quality_frontend_status" --slurpfile health .ci-readiness.json \
  '{commit:$commit,image:$image,frontendHttpStatus:$status,readiness:$health[0]}' > .ci-health.json
