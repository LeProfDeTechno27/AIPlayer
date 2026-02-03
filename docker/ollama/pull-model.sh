#!/bin/sh
set -eu

MODEL="${OLLAMA_MODEL:-qwen3:8b}"
HOST="${OLLAMA_HOST:-http://ollama:11434}"

echo "[ollama-pull] Pull du modele ${MODEL} via ${HOST}"
export OLLAMA_HOST="$HOST"
ollama pull "$MODEL"
echo "[ollama-pull] Modele pret"