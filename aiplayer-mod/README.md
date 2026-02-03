# AIPlayer Mod (Sprint 1-2 baseline)

## Build (validation image)

From project root:

```bash
docker build -f docker/mod-build-check.Dockerfile -t aiplayer-mod-check .
```

## Commandes serveur

- `/bot spawn [name]`
- `/bot status`
- `/bot task <objective>`
- `/bot task done <id>`
- `/bot task start <id>`
- `/bot task cancel <id>`
- `/bot task reopen <id>`
- `/bot task info <id>`
- `/bot task update <id> <objective>`
- `/bot task delete <id>`
- `/bot tasks [limit]`
- `/bot tasks all [limit]`
- `/bot tasks status <status> [limit]`
- `/bot tasks prune [limit]`
- `/bot ask <question>`
- `/bot interactions [limit]`
- `/aiplayer status`
- `/aiplayer spawn`
- `/aiplayer despawn`
- `/aiplayer phase <value>`
- `/aiplayer tick`
- `/aiplayer module list`
- `/aiplayer module enable <name>`
- `/aiplayer module disable <name>`
- `/aiplayer colony status`
- `/aiplayer colony create`
- `/aiplayer colony create <name> <style>`
- `/aiplayer colony claim`
- `/aiplayer colony recruit <count>`
- `/aiplayer ae2 status [radius]`
- `/aiplayer ae2 suggest [radius]`
- `/aiplayer ae2 api`
- `/aiplayer ae2 craft <itemId> [quantity]`
- `/aiplayer ae2 queue [limit]`

## Persistence SQLite

Database path:

- `./aiplayer/bot-memory.db`

Persisted tables:

- `bot_state`
- `bot_actions`
- `bot_tasks`
- `ae2_craft_requests`

## ComputerCraft

Documentation de l'interface peripherals/turtles:

- `docs/computercraft-peripheral-interface.md`
## Ollama

- /bot ask interroge Ollama (qwen3:8b) via http://localhost:11434.
- Variables optionnelles: AIPLAYER_OLLAMA_URL, AIPLAYER_OLLAMA_MODEL.
- Modules activés au démarrage (optionnel): AIPLAYER_ENABLED_MODULES=minecolonies,ae2,computercraft,create.
- Fallback automatique si Ollama indisponible.

## Task Lifecycle

- Cycle simple: PENDING -> ACTIVE -> DONE Ã  chaque /aiplayer tick.
- Override manuel: /bot task done|cancel|reopen <id>.
