#!/usr/bin/env bash
set -euo pipefail
# Leave at least 2 GiB beyond the configured 6 GiB V8 heap for native memory/tools.
available_kb=$(awk '/MemAvailable:/ {print $2}' /proc/meminfo)
limit_bytes=0
for file in /sys/fs/cgroup/memory.max /sys/fs/cgroup/memory/memory.limit_in_bytes; do
  if [ -r "$file" ]; then
    value=$(cat "$file")
    if [[ "$value" =~ ^[0-9]+$ ]] && [ "$value" -lt 1099511627776 ]; then
      limit_bytes=$value
      break
    fi
  fi
done
echo "Build memory: available_kib=$available_kb cgroup_limit_bytes=$limit_bytes heap_mib=6144 reserve_mib=2048"
if [ "$available_kb" -lt 8388608 ] || { [ "$limit_bytes" -gt 0 ] && [ "$limit_bytes" -lt 8589934592 ]; }; then
  echo 'Insufficient runner memory for the configured build heap and native reserve.' >&2
  exit 1
fi
node --version
pnpm --version
node -e 'console.log("V8 heap limit MiB=" + Math.round(require("v8").getHeapStatistics().heap_size_limit / 1048576))'
