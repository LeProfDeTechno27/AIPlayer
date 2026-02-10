#!/usr/bin/env bash
set -euo pipefail

DATA_DIR="${DATA_DIR:-/data}"
BOOTSTRAP_MODS_DIR="${BOOTSTRAP_MODS_DIR:-/bootstrap/mods}"
BOOTSTRAP_MODS_ZIP="${BOOTSTRAP_MODS_ZIP:-/bootstrap/mods.zip}"
BOOTSTRAP_NEOFORGE_DIR="${BOOTSTRAP_NEOFORGE_DIR:-/bootstrap/neoforge}"
BOOTSTRAP_CONFIG_DIR="${BOOTSTRAP_CONFIG_DIR:-/bootstrap/server-config}"
STRICT_MOD_SYNC="${STRICT_MOD_SYNC:-true}"
MAX_TICK_TIME="${MAX_TICK_TIME:--1}"

log() {
  echo "[aiplayer-entrypoint] $*"
}

to_bool() {
  local value="${1:-false}"
  shopt -s nocasematch
  case "$value" in
    true|1|yes|y|on) echo "true" ;;
    *) echo "false" ;;
  esac
  shopt -u nocasematch
}

if [[ "$(to_bool "${EULA:-false}")" != "true" ]]; then
  log "EULA non acceptee. Definis MC_EULA=TRUE dans .env."
  exit 1
fi

mkdir -p "$DATA_DIR" "$DATA_DIR/mods" "$DATA_DIR/config" "$DATA_DIR/logs"
echo "eula=true" > "$DATA_DIR/eula.txt"

install_neoforge() {
  local installer
  installer="$(find "$BOOTSTRAP_NEOFORGE_DIR" -maxdepth 1 -type f -name '*.jar' | sort | head -n 1 || true)"

  if [[ -z "$installer" ]]; then
    log "Aucun installateur NeoForge trouve dans $BOOTSTRAP_NEOFORGE_DIR."
    exit 1
  fi

  log "Installation NeoForge via $(basename "$installer")"
  cd "$DATA_DIR"

  if java -jar "$installer" --install-server; then
    return
  fi

  log "Fallback vers --installServer"
  java -jar "$installer" --installServer
}

is_lfs_pointer() {
  local file="$1"
  grep -a -q -m1 '^version https://git-lfs.github.com/spec/v1$' "$file" 2>/dev/null
}

ensure_valid_mods_zip() {
  local file="$1"

  if is_lfs_pointer "$file"; then
    log "mods.zip est un pointeur Git LFS. Sur l'hote : git lfs install && git lfs pull"
    exit 1
  fi

  if ! unzip -tq "$file" >/dev/null 2>&1; then
    log "mods.zip invalide ou corrompu: $file"
    exit 1
  fi
}

extract_mods_zip() {
  local temp_dir

  ensure_valid_mods_zip "$BOOTSTRAP_MODS_ZIP"

  temp_dir="$(mktemp -d)"
  unzip -o -q "$BOOTSTRAP_MODS_ZIP" -d "$temp_dir"

  while IFS= read -r -d '' mod_file; do
    cp -f "$mod_file" "$DATA_DIR/mods/"
  done < <(find "$temp_dir" -type f -name '*.jar' -print0)

  rm -rf "$temp_dir"
}

purge_existing_mods() {
  local removed_count=0

  while IFS= read -r -d '' existing_mod; do
    rm -f "$existing_mod"
    removed_count=$((removed_count + 1))
  done < <(find "$DATA_DIR/mods" -maxdepth 1 -type f -name '*.jar' -print0)

  if [[ "$removed_count" -gt 0 ]]; then
    log "Purge des anciens mods: $removed_count supprimes depuis $DATA_DIR/mods"
  fi
}

sync_mods() {
  if [[ "$(to_bool "$STRICT_MOD_SYNC")" == "true" ]]; then
    purge_existing_mods
  fi

  if [[ -d "$BOOTSTRAP_MODS_DIR" ]]; then
    log "Synchronisation des mods depuis $BOOTSTRAP_MODS_DIR vers $DATA_DIR/mods"
    while IFS= read -r -d '' mod_file; do
      cp -f "$mod_file" "$DATA_DIR/mods/"
    done < <(find "$BOOTSTRAP_MODS_DIR" -maxdepth 1 -type f -name '*.jar' -print0)
    return
  fi

  if [[ -f "$BOOTSTRAP_MODS_ZIP" ]]; then
    log "Extraction des mods depuis $(basename "$BOOTSTRAP_MODS_ZIP") vers $DATA_DIR/mods"
    extract_mods_zip
    return
  fi

  log "Aucun mods trouve (dossier: $BOOTSTRAP_MODS_DIR, zip: $BOOTSTRAP_MODS_ZIP)"
}

copy_default_server_config() {
  if [[ ! -d "$BOOTSTRAP_CONFIG_DIR" ]]; then
    return
  fi

  while IFS= read -r -d '' config_file; do
    local target_file
    target_file="$DATA_DIR/$(basename "$config_file")"
    if [[ ! -f "$target_file" ]]; then
      cp "$config_file" "$target_file"
    fi
  done < <(find "$BOOTSTRAP_CONFIG_DIR" -maxdepth 1 -type f -print0)
}

set_server_property() {
  local file="$1"
  local key="$2"
  local value="$3"

  if grep -Eq "^${key}=" "$file"; then
    sed -i "s/^${key}=.*/${key}=${value}/" "$file"
  else
    echo "${key}=${value}" >> "$file"
  fi
}

ensure_server_properties() {
  local file
  file="$DATA_DIR/server.properties"

  if [[ ! -f "$file" ]]; then
    return
  fi

  if [[ -n "${MAX_TICK_TIME:-}" ]]; then
    set_server_property "$file" "max-tick-time" "$MAX_TICK_TIME"
  fi
}

ensure_jvm_args() {
  local file
  local force_update
  local has_xms
  local has_xmx

  file="$DATA_DIR/user_jvm_args.txt"
  force_update="$(to_bool "${FORCE_JVM_ARGS_UPDATE:-false}")"

  if [[ ! -f "$file" || "$force_update" == "true" ]]; then
    cat > "$file" <<EOF
-Xms${JVM_HEAP_MIN:-4G}
-Xmx${JVM_HEAP_MAX:-20G}
${JVM_EXTRA_OPTS:-}
EOF
    return
  fi

  if grep -Eq '^[[:space:]]*-Xms' "$file"; then
    has_xms="true"
  else
    has_xms="false"
  fi

  if grep -Eq '^[[:space:]]*-Xmx' "$file"; then
    has_xmx="true"
  else
    has_xmx="false"
  fi

  if [[ "$has_xms" != "true" || "$has_xmx" != "true" ]]; then
    log "Regeneration de user_jvm_args.txt (Xms/Xmx manquants)"
    cat > "$file" <<EOF
-Xms${JVM_HEAP_MIN:-4G}
-Xmx${JVM_HEAP_MAX:-20G}
${JVM_EXTRA_OPTS:-}
EOF
  fi
}

find_unix_args() {
  if [[ -d "$DATA_DIR/libraries/net/neoforged/neoforge" ]]; then
    find "$DATA_DIR/libraries/net/neoforged/neoforge" -type f -name 'unix_args.txt' | sort | head -n 1
  fi
}

cd "$DATA_DIR"

unix_args="$(find_unix_args || true)"
if [[ ! -f "$DATA_DIR/run.sh" && -z "$unix_args" ]]; then
  install_neoforge
fi

sync_mods
copy_default_server_config
ensure_server_properties
ensure_jvm_args

cd "$DATA_DIR"

if [[ -f "$DATA_DIR/run.sh" ]]; then
  log "Demarrage du serveur via run.sh"
  exec /bin/bash "$DATA_DIR/run.sh" nogui
fi

unix_args="$(find_unix_args || true)"
if [[ -n "$unix_args" ]]; then
  log "Demarrage du serveur via unix_args.txt"
  exec java @"$DATA_DIR/user_jvm_args.txt" @"$unix_args" nogui
fi

log "Impossible de trouver une commande de lancement NeoForge."
exit 1
