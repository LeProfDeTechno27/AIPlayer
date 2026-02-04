# AIPlayer - Stack Docker NeoForge + Ollama

Cette base est 100% dockerisee pour le serveur et le dev du mod.

## Services

- `minecraft` : NeoForge `1.21.1 / 21.1.172` + mods du dossier `Ressource minecraft/mods`
- `ollama` : endpoint local LLM (port `11434`)
- `ollama-pull` : job ponctuel de pull du modele (`qwen3:8b` par defaut)
- `mod-dev` (profil `dev`) : shell Gradle/JDK21 pour developper le mod sans installer Java localement

## Demarrage rapide

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

## Validation serveur cible (6c/24GB)

```powershell
./scripts/check-server-capacity.ps1
```

Le script vérifie:
- Host >= 6 cores logiques et >= 24 GB RAM
- Docker >= 6 CPUs et >= 24 GB RAM alloués
## Etat actuel valide

- `minecraft` healthy sur `25565`
- `ollama` healthy sur `11434`
- modele `qwen3:8b` present

## Reset complet

```bash
docker compose down -v
```