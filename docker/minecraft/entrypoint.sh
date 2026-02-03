#!/usr/bin/env bash
set -euo pipefail

DATA_DIR="${DATA_DIR:-/data}"
BOOTSTRAP_MODS_DIR="${BOOTSTRAP_MODS_DIR:-/bootstrap/mods}"
BOOTSTRAP_NEOFORGE_DIR="${BOOTSTRAP_NEOFORGE_DIR:-/bootstrap/neoforge}"
BOOTSTRAP_CONFIG_DIR="${BOOTSTRAP_CONFIG_DIR:-/bootstrap/server-config}"

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

sync_mods() {
  if [[ ! -d "$BOOTSTRAP_MODS_DIR" ]]; then
    log "Dossier de mods absent: $BOOTSTRAP_MODS_DIR"
    return
  fi

  log "Synchronisation des mods vers $DATA_DIR/mods"
  while IFS= read -r -d '' mod_file; do
    cp -f "$mod_file" "$DATA_DIR/mods/"
  done < <(find "$BOOTSTRAP_MODS_DIR" -maxdepth 1 -type f -name '*.jar' -print0)
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

ensure_jvm_args() {
  local force_update
  force_update="$(to_bool "${FORCE_JVM_ARGS_UPDATE:-false}")"

  if [[ ! -f "$DATA_DIR/user_jvm_args.txt" || "$force_update" == "true" ]]; then
    cat > "$DATA_DIR/user_jvm_args.txt" <<EOF
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