#!/usr/bin/env bash
set -euo pipefail

if [[ -d /opt/bootstrap/mods ]]; then
  mkdir -p /data/mods
  cp -f /opt/bootstrap/mods/*.jar /data/mods/ 2>/dev/null || true
fi

if [[ -f /opt/bootstrap/server.properties && ! -f /data/server.properties ]]; then
  cp /opt/bootstrap/server.properties /data/server.properties
fi