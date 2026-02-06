# Roadmap AIPlayer (basée sur plan_dev_mod_ia.md)

## Phase 1 : Définition
- [x] Valider version NeoForge exacte (NeoForge 21.1.172 + MineColonies 1.1.1260-1.21.1-snapshot)
- [x] Lister mods serveur (Ressource minecraft/mods.zip)
- [x] Décider : bot remplace Town Hall owner ou NPC dans colonie ? (owner)
- [ ] Définir scope pédagogique (quoi apprendre aux élèves via bot ?)

## Phase 2 : Design & Architecture
- [x] AIBotEntity (extends PathfinderMob)
- [x] OllamaClient (HTTP) opérationnel
- [x] BotBrain FSM (états + transitions) 
- [x] MemoryManager SQLite (BotMemoryRepository)
- [x] MineColoniesHook (bridge reflection)

## Sprint 1 : MVP Basique
- [x] Setup NeoForge MDK (build.gradle)
- [x] Classe AIBotEntity minimaliste
- [x] Spawn command `/bot spawn <name>` (entit? AIBot)
- [x] Pathfinding vanilla
- [x] Deliverable: Bot spawn + walk

## Sprint 2 : Survival Loop
- [x] Health/Hunger/Saturation management (simul?)
- [x] Harvest crops + eat (simul?)
- [x] Mining (detect ores, collect drops) (simul?)
- [x] Basic crafting (workbench, furnace) (simul?)
- [x] Sleep system (night ? bed) (simulé)
- [x] Deliverable: bot survit seul 24h (simul?) (basique)

## Sprint 3 : Ollama Integration
- [x] HTTP client Ollama (actuel: Java HttpClient)
- [x] BotBrain simple (décision + cache temps)
- [x] Query Ollama intervalle configurable (AIPLAYER_DECISION_INTERVAL_TICKS)
- [x] Action execution basée réponse LLM (basique)
- [x] Memory logging SQLite (tasks, interactions)
- [x] Deliverable: bot décide + exécute (basique)

## Sprint 4 : MineColonies Hook
- [x] Détecte Town Hall (MineColoniesBridge)
- [x] Accès Citizen API (reflection via MineColoniesBridge)
- [x] Assigne tâches citoyens / spawn virtual citizens (ensure citizens)
- [x] Gère requests system (deposit items -> warehouse) (simulé)
- [x] Bot remplace mayor (avance) (simule)
- [x] Deliverable: bot interagit avec colonie (basique)

## Sprint 5 : Player Interaction
- [x] Command `/bot task <objective>` (existant)
- [x] Chat command `/bot ask <question>` (existant)
- [x] Bot repond chat (Ollama sync basique)
- [x] Log interactions (SQLite)
- [x] Collaborative builds (simule via /bot build)
- [x] Deliverable: joueurs donnent ordres (basique)

## Sprint 6 : Evolution & Persistence
- [x] Phases d evolution (manual via /aiplayer phase)
- [x] Unlock skills via XP (tiers basiques)
- [x] Sauvegarde state entre shutdowns (SQLite)
- [x] UI simple `/bot status` (existant)
- [x] Optimisations perf (cache + backoff + rate limit + stats + DB batch + degrade LLM + tick throttle)
- [x] Deliverable: bot persiste & evolue (basique)

## Phase 4 : Test & Polish
- [ ] QA mono-serveur (10 joueurs + 1 IA)
- [ ] Stress tests MineColonies + Create + IA
- [ ] Edge cases (respawn, path, conflicts, timeouts)
- [ ] Documentation (README, wiki, guide Ollama)
- [ ] Optimisations (batch LLM, cache path, async DB)
















