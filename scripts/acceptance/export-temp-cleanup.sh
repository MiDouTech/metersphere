#!/bin/sh
set -eu

# Pipe this into the isolated application container as appuser, never as root.
if [ "$(id -u)" -eq 0 ]; then
  echo 'Run this test as the application user, not root' >&2
  exit 1
fi
root=/tmp/metersphere/system/temp
nonce=$(cat /proc/sys/kernel/random/uuid)
case "$nonce" in ''|*[!a-f0-9-]*) exit 1 ;; esac
orphan="$root/ms-case-export.acceptanceorphan.$nonce"
fresh="$root/ms-case-export.acceptancefresh.$nonce"
keep="$root/remediation-keep.$nonce"

cleanup() {
  rm -f "$orphan/payload.bin" "$fresh/payload.bin" "$keep/payload.bin"
  for directory in "$orphan" "$fresh" "$keep"; do
    [ ! -d "$directory" ] || rmdir "$directory"
  done
}
trap cleanup EXIT
mkdir -p "$orphan" "$fresh" "$keep"
printf fixture > "$orphan/payload.bin"
printf fixture > "$fresh/payload.bin"
printf fixture > "$keep/payload.bin"
touch -t 200001010000 "$orphan"

deadline=$(( $(date +%s) + 120 ))
while [ -e "$orphan" ]; do
  if [ "$(date +%s)" -ge "$deadline" ]; then
    echo 'Scheduled orphan cleanup did not run within 120 seconds' >&2
    exit 1
  fi
  sleep 2
done
test -f "$fresh/payload.bin"
test -f "$keep/payload.bin"
printf '%s\n' '{"orphanRemoved":true,"freshRetained":true,"foreignRetained":true}'
