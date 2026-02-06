#!/usr/bin/env bash
set -euo pipefail

if [[ -f /opt/bootstrap/mods.zip ]]; then
  temp_dir="$(mktemp -d)"
  unzip -o -q /opt/bootstrap/mods.zip -d "$temp_dir"
  mkdir -p /data/mods
  while IFS= read -r -d '' mod_file; do
    cp -f "$mod_file" /data/mods/
  done < <(find "$temp_dir" -type f -name '*.jar' -print0)
  rm -rf "$temp_dir"
elif [[ -d /opt/bootstrap/mods ]]; then
  mkdir -p /data/mods
  cp -f /opt/bootstrap/mods/*.jar /data/mods/ 2>/dev/null || true
fi

if [[ -f /opt/bootstrap/server.properties && ! -f /data/server.properties ]]; then
  cp /opt/bootstrap/server.properties /data/server.properties
fi
