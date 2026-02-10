# AIPlayer - Stack Docker NeoForge + Ollama

Cette base est 100% dockerisee pour le serveur et le dev du mod.

## Services

- `minecraft` : NeoForge `1.21.1 / 21.1.172` depuis `Ressource minecraft/neoforge-21.1.172` + mods dans `Ressource minecraft/mods.zip`
- `ollama` : endpoint local LLM (port `11434`)
- `ollama-pull` : job ponctuel de pull du modele (`qwen3:8b` par defaut)
- `mod-dev` (profil `dev`) : shell Gradle/JDK21 pour developper le mod sans installer Java localement

## Mods (Git LFS)

- `Ressource minecraft/mods.zip` est versionne via Git LFS.
- Apres clonage : `git lfs pull` pour recuperer les mods.`r`n- `Ressource minecraft/mods-client` : mods client-only (non charges par le serveur).

## Demarrage rapide


## Variables Minecraft

- `MC_EULA` : accepter la EULA (TRUE/FALSE)
- `MC_JVM_HEAP_MIN` : heap minimum (ex: 4G)
- `MC_JVM_HEAP_MAX` : heap maximum (ex: 12G)
- `MC_JVM_EXTRA_OPTS` : options JVM additionnelles
- `MC_FORCE_JVM_ARGS_UPDATE` : forcer regen `user_jvm_args.txt`
- `MC_MAX_TICK_TIME` : valeur `max-tick-time` dans `server.properties` (defaut `-1` pour desactiver le watchdog NeoForge)
- `STRICT_MOD_SYNC` : `true` (defaut) purge `/data/mods` avant copie depuis `mods.zip`; `false` conserve les jars existants
- `AIPLAYER_DECISION_INTERVAL_SECONDS` : intervalle decisions Ollama (secondes)
- `AIPLAYER_DECISION_WINDOW_SECONDS` : fenetre rate-limit decisions LLM
- `AIPLAYER_DECISION_MAX_PER_WINDOW` : max decisions LLM par fenetre
- `AIPLAYER_DECISION_CACHE_SECONDS` : TTL cache des reponses LLM
- `AIPLAYER_DECISION_CACHE_MAX` : taille max cache questions LLM
- `AIPLAYER_DECISION_BATCH_SIZE` : nombre d'actions par requete LLM
- `AIPLAYER_DECISION_DEGRADE_SECONDS` : intervalle degrade en surcharge
- `AIPLAYER_MAX_MSPT` : coupe les decisions LLM si MSPT depasse le seuil
- `AIPLAYER_PATH_CACHE_SECONDS` : TTL cache move/path pour limiter recalculs
- `AIPLAYER_RECIPE_CACHE_SECONDS` : TTL cache demandes craft (AE2)
- `AIPLAYER_ACTION_FLUSH_SECONDS` : flush batch SQLite (bot_actions)
- `AIPLAYER_ACTION_BATCH_SIZE` : taille batch SQLite
- `AIPLAYER_ACTION_BUFFER_WARN` : seuil buffer avant degradation



```bash
cp .env.example .env
docker compose up -d --build minecraft ollama
```

## Pull du modele Ollama

```bash
docker compose --profile bootstrap up ollama-pull
```

## Shell de dev du mod

```bash
docker compose --profile dev run --rm mod-dev bash
```

## Profil perf (serveur max 6c/24GB)

Recommandation de base (profil light):

- `AIPLAYER_DECISION_INTERVAL_SECONDS=90`
- `AIPLAYER_DECISION_MAX_PER_WINDOW=2`
- `AIPLAYER_DECISION_DEGRADE_SECONDS=120`
- `AIPLAYER_ACTION_FLUSH_SECONDS=30`
- `AIPLAYER_ACTION_BATCH_SIZE=50`

## Validation budget serveur (max 6c/24GB)

```powershell
./scripts/check-server-capacity.ps1
```

Le script affiche l'etat par rapport au budget (max 6c/24GB).
- Host: reference 6 cores / 24 GB (pas plus requis)
- Docker: reference 6 CPUs / 24 GB (pas plus requis)
## Etat actuel valide

- `minecraft` healthy sur `25565`
- `ollama` healthy sur `11434`
- modele `qwen3:8b` present

## Reset complet

```bash
docker compose down -v
```


