# AIPlayer Mod (Sprint 1-2 baseline)

## Build (validation image)

From project root:

```bash
docker build -f docker/mod-build-check.Dockerfile -t aiplayer-mod-check .
```

## Commandes serveur

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
- `/aiplayer ae2 craft <itemId> [quantity]`
- `/aiplayer ae2 queue [limit]`

## Persistence SQLite

Database path:

- `./aiplayer/bot-memory.db`

Persisted tables:

- `bot_state`
- `bot_actions`
- `ae2_craft_requests`