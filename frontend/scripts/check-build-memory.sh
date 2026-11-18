#!/usr/bin/env bash
set -euo pipefail
# Leave at least 2 GiB beyond the configured 6 GiB V8 heap for native memory/tools.
available_kb=$(awk '/MemAvailable:/ {print $2}' /proc/meminfo)
limit_bytes=0
usage_bytes=0
remaining_bytes=0
for file in /sys/fs/cgroup/memory.max /sys/fs/cgroup/memory/memory.limit_in_bytes; do
  if [ -r "$file" ]; then
    value=$(cat "$file")
    if [[ "$value" =~ ^[0-9]+$ ]] && [ "$value" -lt 1099511627776 ]; then
      limit_bytes=$value
      if [ "$file" = /sys/fs/cgroup/memory.max ]; then
        usage_file=/sys/fs/cgroup/memory.current
      else
        usage_file=/sys/fs/cgroup/memory/memory.usage_in_bytes
      fi
      if [ ! -r "$usage_file" ]; then
        echo 'Cannot determine current usage for the finite cgroup memory limit.' >&2
        exit 1
      fi
      usage_bytes=$(cat "$usage_file")
      if ! [[ "$usage_bytes" =~ ^[0-9]+$ ]]; then
        echo 'Invalid cgroup memory usage.' >&2
        exit 1
      fi
      remaining_bytes=$((limit_bytes - usage_bytes))
      break
    fi
  fi
done
echo "Build memory: available_kib=$available_kb cgroup_limit_bytes=$limit_bytes cgroup_usage_bytes=$usage_bytes cgroup_remaining_bytes=$remaining_bytes heap_mib=6144 reserve_mib=2048"
if [ "$available_kb" -lt 8388608 ] || { [ "$limit_bytes" -gt 0 ] && [ "$remaining_bytes" -lt 8589934592 ]; }; then
  echo 'Insufficient runner memory for the configured build heap and native reserve.' >&2
  exit 1
fi
node --version
pnpm --version
node -e 'console.log("V8 heap limit MiB=" + Math.round(require("v8").getHeapStatistics().heap_size_limit / 1048576))'
