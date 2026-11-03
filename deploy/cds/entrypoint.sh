#!/bin/sh
set -eu
# Override the legacy source-build path only inside this dedicated preview image.
export MS_CONFIG_DIR=/opt/metersphere/conf
export MS_REDISSON_CONFIG=file:/opt/metersphere/conf/redisson.yml
exec /deployments/run-with-wecom-bridge.sh
