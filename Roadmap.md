# Plan d'Action BMAD Avancé : Mod NeoForge IA Town Hall Owner (1.21.1)

## 🎯 Vision Produit
**Créer un mod NeoForge qui spawne une entité IA autonome capable de :**
- **Devenir Town Hall Owner** de MineColonies (contrôle total colonie)
- Survivre & prospérer seule (récolte, craft, construction, farming)
- **Orchestrer infrastructure complexe** (AE2 networks, Create automation, ComputerCraft scripts)
- Interagir & collaborer avec les joueurs du serveur (consultation, delegation)
- Apprendre via mémoire persistante (SQLite) + LLM (Ollama local) + apprentissage par essai-erreur
- **Évoluer exponentiellement** au fil des jours/semaines/mois (survie → fermes → factories → megastructures)
- **Plugin architecture** : ajouter nouveaux mods dynamiquement via module handlers

**Cibles:** Serveur NeoForge 6 cores/24 Go RAM + Ollama local + MineColonies + AE2 + ComputerCraft + (extensible)

**Objectif final:** Laisser l'IA seule → civilisation autonome fonctionnelle

---

## 🛠️ BMAD Breakdown Avancé

### Phase 1 : DEFINITION (Semaine 1)

#### 1.1 Comprendre le contexte
- [x] Version NeoForge exacte validée: Minecraft 1.21.1 + NeoForge 21.1.172 (aiplayer-mod/gradle.properties).
- [x] Mods serveur inventoriés (`Ressource minecraft/mods`): 421 jars, incluant MineColonies, AE2, CC-Tweaked, Create.
- [x] MineColonies owner programmatique: partiel (API reflective OK pour assigner un `ServerPlayer` via `setOwner(Player)`; owner bot entite non joueur non supporte a ce stade).
- [x] AE2 API (grids/channels/crafting): partiel. Probe classes + scan blocs + queue craft locale OK; pilotage direct IGrid/channels/ICraftingService non implemente.
- [x] ComputerCraft : non a ce stade. Module placeholder present, mais aucun bridge CC-Tweaked/API peripheral ni ecriture/execution Lua implementee.
- [x] Identifier autres mods interessants (phases futures): Mekanism, Immersive Engineering, Industrial Foregoing, IntegratedDynamics/Tunnels, Advanced Peripherals, PneumaticCraft, Powah, Refined Storage, FTB Quests, Modular Routers, Sophisticated Storage/Backpacks, Ars Nouveau, Occultism.

#### 1.2 Architecture système (PluginBase)

```
┌─────────────────────────────────────────────────────────────────┐
│                   MINECRAFT SERVER (NeoForge)                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │  AIPlayerMod (Core Mod)                                    │ │
│  │  ├─ AIBotEntity (Entité custom, Town Hall owner)           │ │
│  │  ├─ BotBrain (FSM + Décisions hiérarchiques)               │ │
│  │  ├─ ModuleManager (Plugin architecture)                    │ │
│  │  │   ├─ MineColoniesModule (gestion colonie)               │ │
│  │  │   ├─ AE2Module (réseau automatisé)                      │ │
│  │  │   ├─ ComputerCraftModule (automation scripts Lua)       │ │
│  │  │   ├─ CreateModule (contraptions + logistics)            │ │
│  │  │   └─ [DynamicModules] (load at runtime)                 │ │
│  │  ├─ OllamaClient (HTTP decisions)                          │ │
│  │  ├─ MemoryManager (SQLite + learning)                      │ │
│  │  └─ EvolutionEngine (phases temporelles)                   │ │
│  └────────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │  Mods supportés (MineColonies, AE2, CC, Create, etc.)      │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              ↕ (HTTP)
                    ┌─────────────────┐
                    │ Ollama Local    │
                    │ (Qwen3:8b)      │
                    └─────────────────┘
```

#### 1.3 Cas d'usage clés

1. **Jour 0-2 (Survie)** : Bot spawn → survivre seul (mine, craft, eat, sleep)
2. **Jour 2-5 (Colonie)** : Create Town Hall → become owner → govern citizens
3. **Jour 5-10 (Automation)** : Setup AE2 network + ComputerCraft scripts → auto-mining/smelting
4. **Semaine 2-4 (Infrastructure)** : Megastructures (factories, railways, storage)
5. **Semaine 4+ (Expansion)** : Optimal resource loops, player collaboration, new mods

#### 1.4 Plugin Architecture (extensible)

```java
// Interface générique pour modules
interface IBotModule {
    String getName();
    void init(AIBotEntity bot);
    void tick();
    JsonObject serializeState();
    void deserializeState(JsonObject);
}

// Exemple implémentation
class AE2Module implements IBotModule {
    @Override public void tick() {
        // Monitor grids, schedule crafts, rebalance channels
    }
}

// Manager load/unload dynamique
ModuleManager manager = new ModuleManager();
manager.loadModule("minecolonies", MineColoniesModule.class);
manager.loadModule("ae2", AE2Module.class);
manager.loadModule("computercraft", ComputerCraftModule.class);
// + Load custom modules via config folder
```

---

## 🛠️ Architecture Détaillée par Mod

### MineColonies Module (Town Hall Owner)

**Objectif:** Bot = mayor complet, gère colonie autonome

**Mécaniques:**
- Create Town Hall + claim land
- Recruit citizens (automated via LLM: "J'ai 100 planches → recrute 2 builders")
- Assign jobs optimalement (worker shortage → priorité haute)
- Manage happiness (food, entertainment, housing → auto-build)
- Upgrade huts via colony resources
- Handle raids/defense

**API Access:**
```java
// Get colony manager
IColonyManager colonyManager = IColonyManager.getInstance();
IColony colony = colonyManager.getColonyByTeam(bot.getTeam());

// Create new colony
IColony newColony = colonyManager.createColony(bot.getWorld(), bot.getPos(), bot);
newColony.setOwner(bot);  // Critical: set as owner

// Manage citizens
colony.getCitizenManager().createCitizen("Builder", "Steve");
JobEntry job = colony.getJobManager().getJob(JOB_BUILDER);
job.assign(citizen);

// Upgrade huts
colony.getBuildingManager().getBuilding(pos).upgrade();

// Resource management
int wood = colony.getWarehouse().getAmount(PLANKS);
```

**Evolution Timeline:**
- T+0 : Colony lvl1, 1 citizen
- T+24h : lvl 2, 4 citizens, workers
- T+3d : lvl 3, fermes wheat/carrot/potato auto
- T+1w : lvl 4-5, mines, smelters, composter farms
- T+2w+ : lvl 4-5 full, dedicated districts

### Applied Energistics 2 Module (Smart Automation)

**Objectif:** Bot contrôle AE2 network → craft automation intelligente

**Mécaniques:**
- Build ME controller, drives, autocrafters
- Monitor grid, detect shortages
- Schedule crafts (LLM: "Besoin 64 planches → craft 4 stacks")
- Rebalance channels (add drives, split networks)
- Export excess resources (chest/furnace external)
- Integrate with MineColonies (request system → AE2 craft)

**API Access:**
```java
// Get grid
IGridNode gridNode = ...;
IGrid grid = gridNode.getGrid();
IStorageGrid storage = grid.getStorageService();

// Check inventory
long items = storage.getInventory().getStoredAmount(item);

// Schedule craft
ICraftingService crafting = grid.getCraftingService();
ICraftingJob job = crafting.requestMake(item, 64);

// Monitor channels
ISecurityGrid security = grid.getSecurityService();
int usedChannels = security.getUsedChannels();
```

**Evolution Timeline:**
- T+1d : 1 simple network (1 controller, 2 drives, autocrafts)
- T+3d : Multi-network (hub + satellites), ~30 drives
- T+1w : Sub-networks specialized (ores, crops, metals, etc.)
- T+2w+ : Fully self-sustaining (request from citizens → auto-craft)

### ComputerCraft Module (Autonomous Scripts)

**Objectif:** Bot génère & execute Lua scripts pour automation tâches répétitives

**Mécaniques:**
- Create "AI Agent" turtles/computers
- Generate Lua scripts via LLM (+ RAG ComputerCraft wiki)
- Scripts: Mine, build, sort items, navigate, interact blocks
- Execute scripts, learn from failures (modify next version)
- Deploy turtles around map (mining, quarries, railways)

**API/Integration:**
```lua
-- Generated script example (bot writes this):
-- File: scripts/auto_mine.lua
local pos = {x = 100, y = 64, z = 100}
while true do
    turtle.dig()
    turtle.forward()
    if turtle.getItemCount(1) > 48 then
        turtle.dropDown()  -- Drop to chest below
    end
    sleep(0.1)
end

-- Bot executes via computercraft terminal:
-- Run script via redstone signal or direct peripheral call
```

**Evolution Timeline:**
- T+1d : 1 simple miner turtle
- T+3d : 3-5 turtles, dedicated mining routes
- T+1w : Quarry turtle, auto-refueling, item sorting system
- T+2w+ : Fully autonomous mining + building + logistics network

### Create Module (Mechanical Automation)

**Objective:** Bot builds contraptions for passive resource generation

**Mechanics:**
- Detect Create mod, build farms (andesite, copper)
- Schedule: wheat farm → millstone → composting
- Ore crushers, smelting trains
- Mixing vats for mod materials
- Deploy schematics (Create schematicannon)
- Optimize production chains

**Evolution Timeline:**
- T+2d : Basic farm (wheat → flour → compost)
- T+1w : Full farm complex + ore crushing + smelting
- T+2w : Integrated with AE2 (Create outputs feed ME storage)

### Generic Module Handler (Future Mods)

**Adding new mods at runtime:**
```yaml
# config/aiplayer/modules.yml
modules:
  minecolonies:
    enabled: true
    priority: 100
  ae2:
    enabled: true
    priority: 90
  computercraft:
    enabled: true
    priority: 80
  create:
    enabled: true
    priority: 70
  
  # New mod (e.g., Mekanism)
  mekanism:
    enabled: false  # Enable when ready
    class: "com.mymodule.MekanismModule"
    priority: 60
```

---

## 🛠️ BotBrain: FSM Hiérarchique + LLM

### État Hiérarchique (Pyramid)

```
LEVEL 1 (Strategic - Ollama, décision 1x/min)
├─ SURVIVAL (hunger, health, sleep)
├─ COLONY_MANAGEMENT (citizen assignments, upgrades, defense)
├─ INFRASTRUCTURE (AE2 setup, Create farms, CC scripts)
└─ EXPANSION (new projects, mod integration, mega-builds)

LEVEL 2 (Tactical - Simple FSM, décision 5x/sec)
├─ MOVE / PATHFIND
├─ MINE / HARVEST
├─ CRAFT / COMBINE
├─ PLACE / BUILD
├─ INTERACT (citizens, blocks, chests)
└─ WAIT / SLEEP

LEVEL 3 (Reactive - Milliseconds)
├─ Collision avoidance
├─ Item pickup
├─ Combat (if mobs)
└─ Redstone pulses
```

### Decision Flow

```
Tick (20x/sec):
1. Update LEVEL 3 (reactive, instant)
   - Navigate, pickup drops, dodge mobs

2. Every 5 ticks (4x/sec):
   - Update LEVEL 2 (tactical FSM)
   - Execute current action

3. Every 30 ticks (once every 1.5s):
   - Check state (position, inven, environs, colony status, grid status)
   - If priority changed → update task queue

4. Every 600 ticks (30s):
   - Query LEVEL 1 (Ollama strategic decision)
   - Context: "Colonie: 8 citizens, faim, no stone. AE2: 15/30 drives. CC: 2 miners ok. Quoi faire ?"
   - LLM propose: "Priorité: recrute +3 miners (need planks/pierre) → assign mining route → schedule crops"
   - Update main objective

5. Log to SQLite:
   - action, result (success/fail), resources delta, citizen state
```

### Context Prompt (sent to Ollama)

```
System: "Tu es le Maire d'une civilisation Minecraft. Gère optimalement."

State:
- Time: Jour 5, Heure 14:00
- Position: X=-150, Y=64, Z=200 (près Town Hall)
- Health/Hunger: 18/20 HP, 7/10 Hunger (ok)

MineColonies:
- Colony Lvl: 2
- Citizens: 8/10 (Worker shortage: -2)
- Huts: TownHall, 1 Builder, 2 Miners, 1 Farmer, 1 Guard
- Happiness: 80% (bon)
- Warehouse: Stone 450/500, Planks 200/200 (full), Ore Iron 50, Wheat 200

Applied Energistics 2:
- Grid Status: 24/30 channels used
- Storage: 2500 items, 3 types low (Iron ore 5 left, Charcoal 0)
- Autocrafters: 3 running (planks, sticks, torches)

ComputerCraft:
- Miners: 2 active (granite mine, diorite mine)
- Status: Mining ok, return in 4h

Create:
- Farm: Wheat growing (80% done), Compost ok
- Status: Smelters running

Next Actions Suggestion:
1. Recruit 2 new miners (have planks + stone ✓)
2. Assign to iron mine (shortage detected)
3. Monitor AE2 charcoal shortage (craft via smelter when iron done)
4. Check Citizens housing (some may be unhappy)

Your Decision:
```

---

## 📊 Evolution Engine: Temporal Phases

### Timeline & Exponential Growth

```
DAY 0-2: SURVIVAL (Solo)
├─ Bot spawns, finds food, crafts tools
├─ Builds simple shelter
├─ Chops wood, mines stone
└─ Goal: survive 48h = SUCCESS

DAY 2-5: FOUNDATION (Town Hall Owner)
├─ Create & claim colony land
├─ Recruit 4-6 initial citizens
├─ Assign builder: construct huts
├─ Assign miners: simple mine
├─ Assign farmer: start crops
└─ Goal: lvl2 colony autonomous = SUCCESS

DAY 5-10: AUTOMATION (AE2 + ComputerCraft)
├─ Build first AE2 network (small, 8 drives)
├─ Setup initial autocrafters (planks, sticks)
├─ Deploy 2 ComputerCraft miner turtles
├─ Build Create farm (wheat → composting)
├─ Upgrade colony huts (miner huts lvl2)
└─ Goal: 50% resource loops automated = SUCCESS

WEEK 2-3: SCALING (Infrastructure)
├─ Expand AE2 (multi-networks, 50+ drives)
├─ Deploy 5-10 CC turtles (mining routes optimized)
├─ Build massive Create farms (all food types)
├─ Integrate Create outputs → AE2 requests
├─ Upgrade colony to lvl 4
├─ Recruit 15-20 citizens (specialized)
└─ Goal: Self-sustaining civilization = SUCCESS

WEEK 3-4: OPTIMIZATION (Advanced)
├─ Optimize resource loops (zero waste ideally)
├─ Automate ore processing (all ores → ingots via AE2)
├─ Build railways (CC turtles connect sites)
├─ Exploit modifiers (efficiency, speed)
├─ Expand territory (claim new biomes)
├─ Potential: Add new mods (Mekanism, Immersive Engineering, etc.)
└─ Goal: Near-optimal economy = SUCCESS

WEEK 4+: MEGASTRUCTURES (Mega-builds)
├─ Build: Massive nether farm
├─ Build: Automated quarry system
├─ Build: Central hub (aesthetic + functional)
├─ Build: Citizen housing (megabases)
├─ Potential: Moonshot projects (flying machines, etc.)
├─ Player interaction: Delegate mega-projects to players
└─ Goal: Beautiful + functional civilization = AMAZING
```

### Metrics Tracked

```
EvolutionMetrics (SQLite):
- resource_generation_per_min (copper, iron, stone, etc.)
- citizen_count, happiness_avg
- ae2_channels_used, ae2_items_stored
- cc_turtles_active, cc_tasks_completed
- automation_percentage (0-100%)
- uptime (hours)
- player_interactions (count, type)
```

**Example output after 1 week:**
```
=== WEEK 1 SUMMARY ===
Colony: Lvl 3, 14 citizens, 85% happiness
Resources: Stone +450/min, Ore +80/min, Food +120/min
Automation: 65% (up from 0%)
AE2: 45 drives, 50,000 items stored
CC Miners: 4 active, avg 200 ore/hour
Revenue Rate: Could sustain 20 players solo
```

---

## 🎯 Tech Stack & Dependencies

### Core
- **Dev:** IntelliJ IDEA + NeoForge MDK 1.21.1
- **Language:** Java 21
- **Build:** Gradle

### Libs
- **HTTP:** OkHttp (Ollama)
- **JSON:** Gson
- **Database:** SQLite JDBC
- **Math:** Apache Commons Math (Q-learning)
- **Scheduling:** Quartz (optional, for cron-like tasks)
- **NeoForge API:** Living entities, events, commands, NBT

### Mod APIs (to depend on)
- **MineColonies:** API module (public)
- **AE2:** API access grids, channels
- **ComputerCraft:** Peripheral access + Lua execution
- **Create:** Schematic deploy
- **Others:** As added

### IA & Learning
- **LLM:** Ollama (Qwen3:8b local)
- **RAG:** Mod wikis (MineColonies, AE2, CC, Create)
- **Reinforcement:** Q-learning (SQLite state-action table)

---

## 🛠️ BUILD ITERATIF (Sprints)

### Sprint 1 (1 week) : MVP Baseline + MineColonies Owner

**Goals:**
- Bot spawns, becomes Town Hall Owner
- Simple FSM: gather → craft → build
- Recruit citizens autonomously
- Basic resource tracking

**Deliverable:**
- Bot spawn → create colony → recruit 3 citizens → assign jobs
- Log: citizen state, happiness, resource production
- TPS: stable (no lag)

---

### Sprint 2 (1 week) : AE2 Module

**Goals:**
- Bot builds first AE2 network
- Monitor grid, schedule crafts
- Integrate colony warehouse ↔ AE2

**Deliverable:**
- AE2 network auto-built, 10 drives
- Autocrafts basic items (planks, sticks, stone bricks)
- Zero manual intervention

---

### Sprint 3 (1 week) : ComputerCraft Module

**Goals:**
- Bot generates & deploys CC turtles
- Simple Lua scripts (mine, navigate, deposit)
- Learn from failures (script v2, v3)

**Deliverable:**
- 2-3 miner turtles operational
- Mining routes logged
- Resources returned to main base

---

### Sprint 4 (1 week) : Create Module + Evolution Engine

**Goals:**
- Build Create farms
- Temporal phases (day, week, month logic)
- Metrics tracking

**Deliverable:**
- Wheat farm producing
- 1-week timeline shows growth
- Dashboard showing phase + metrics

---

### Sprint 5 (1 week) : Ollama Integration + Hierarchical FSM

**Goals:**
- Integrate full LLM decision making
- Level 1-3 FSM hierarchy
- Context prompt design

**Deliverable:**
- Bot makes strategic decisions (Ollama)
- Tactical actions follow strategy
- Observable improvement in resource generation

---

### Sprint 6 (1-2 weeks) : Module Manager + Extensibility

**Goals:**
- Generic IBotModule interface
- Load/unload mods dynamically
- RAG for each mod (wiki access)
- Add 1-2 new mods (Mekanism, IE, etc.)

**Deliverable:**
- Config-driven module loading
- Add 5+ mods seamlessly
- Demo: load Mekanism → bot uses reactor

---

### Sprint 7 (1-2 weeks) : Polish + Long-term Testing

**Goals:**
- Run bot 1+ weeks unattended
- Observe exponential growth
- Fix bugs, optimize perf
- Documentation

**Deliverable:**
- Bot runs stable 7+ days
- Video: time-lapse week evolution
- README + setup guide

---

## 📈 Expected Growth Trajectory

### Resources (per minute)

```
Timeline | Stone | Ore | Charcoal | Food | Wood | Overall
---------|-------|-----|----------|------|------|----------
Day 1    | 5     | 0   | 0        | 0    | 20   | Low
Day 3    | 50    | 10  | 5        | 30   | 30   | Growing
Day 5    | 150   | 50  | 20       | 100  | 40   | Exponential
Week 1   | 450   | 200 | 80       | 300  | 50   | Self-sustaining
Week 2   | 800   | 400 | 200      | 600  | 80   | Optimized
Week 3+  | 1500+ | 1000+| 500+    | 1200+| 100+ | Mature
```

### Automation Percentage

```
Day 1:  0% (all manual)
Day 3:  10% (citizen farming starts)
Day 5:  40% (AE2 crafting, CC miners)
Week 1: 70% (integrated loops)
Week 2: 85% (optimized)
Week 3+: 95%+ (near fully autonomous)
```

### Citizen Count + Happiness

```
Day 0:  0 citizens
Day 2:  4 citizens, 60% happiness
Day 5:  10 citizens, 75% happiness
Week 1: 16 citizens, 80% happiness
Week 2: 22 citizens, 85% happiness (housing limits kick in)
Week 3: 24 citizens, 85% (max with current infrastructure)
```

---

## ⚠️ Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| MineColonies API private/changes | Can't set owner | Use reflection, fallback event hooks |
| AE2 grid crashes with 100+ drives | Server lag | Limit drives, split networks, cache grid state |
| CC turtles infinite loop | Server stuck | Timeout (3600s max script), force kill |
| Ollama OOM | Server crash | Monitor memory, fallback to simpler model (4B) |
| Mod conflicts (Create + AE2 + MC) | CTD | Heavy testing, prioritize stable combos |
| AI hallucinating bad decisions | Waste resources | Log failures, learn, adjust next iteration |

---

## 🎬 Final Vision

**After 1 month unattended:**
- **Town Hall Owner:** Full colony, 24+ citizens, lvl 4+, optimal jobs
- **AE2 Network:** 100+ drives, fully integrated, zero bottlenecks
- **CC Automation:** 10+ turtles, mining quarries, auto-sorting, railways
- **Create Farms:** All food types, ore processing, integrated with AE2
- **Resource Generation:** Exponential (e.g., 1000+ ore/min)
- **Beauty:** Megastructures, themed biomes, functional art
- **Scalability:** Ready to add new mods seamlessly

**Watching it evolve:** Like an ant colony time-lapse, but in Minecraft, driven by AI.

---

## 📋 Pre-Dev Checklist

- [x] NeoForge 1.21.1 version confirmed
- [x] Mods list finalized (MC, AE2, CC, Create + others)
- [x] GitHub repo created
- [x] Ollama tested locally (Qwen3:8b)
- [x] MineColonies API study (owner assignment feasibility)
- [x] AE2 API access confirmed
- [x] CC peripheral interface documented
- [ ] 6c/24GB server ready (check script: `scripts/check-server-capacity.ps1`; current: host 12 cores / 15.34 GB RAM, Docker 12 vCPU / 7.43 GB RAM).

**Go?** ✅ Start Sprint 1 → Bot becomes Town Hall Owner
---

## Avancement (2026-02-02)

- [x] Stack Docker operationnelle (minecraft, ollama, ollama-pull, mod-dev)
- [x] NeoForge server: 1.21.1 / 21.1.172
- [x] Mods loaded from Ressource minecraft/mods
- [x] Mod aiplayer build OK in Docker (docker/mod-build-check.Dockerfile)
- [x] Sprint 1 base: runtime, /aiplayer commands, SQLite persistence
- [x] MineColonies owner bridge integrated (create/claim/recruit commands)
- [x] AE2 baseline integrated (scan/status/suggest commands)
- [x] AE2 autocrafting request pipeline (queue persistée + commandes craft/queue + dispatch runtime)
- [x] Plan d'action appliqué: `/bot spawn <name>` ajouté (kickoff Sprint 1).
- [x] Plan appliqué (Sprint 1): `/bot task <objectif>` + `/bot tasks` + persistance `bot_tasks`.
- [x] Plan appliqué (Sprint 1): `/bot ask <question>` + log SQLite `interactions` + `/bot interactions`.
- [x] Plan appliqué (Sprint 3 kickoff): `/bot ask` connecté à Ollama (`qwen3:8b`) avec fallback.
- [x] Plan appliqué (Sprint 1): boucle simple tasks `/aiplayer tick` (`PENDING -> ACTIVE -> DONE`).
- [x] Plan appliqué (Sprint 1): commandes manuelles `/bot task done <id>` et `/bot task cancel <id>`.
- [x] Plan appliqué (Sprint 1): commande `/bot task reopen <id>` pour remettre une tâche en PENDING.
- [x] Plan appliqué (Sprint 1): commande `/bot tasks all [limit]` pour consulter l'historique complet des tâches.
- [x] Plan appliqué (Sprint 1): commande `/bot tasks status <status> [limit]` pour filtrer les tâches par état.
- [x] Plan appliqué (Sprint 1): commande `/bot tasks prune [limit]` pour nettoyer les tâches fermées.
- [x] Plan appliqué (Sprint 1): commande `/bot task info <id>` pour inspecter une tâche précise.
- [x] Plan appliqué (Sprint 1): commande `/bot task update <id> <objective>` pour corriger un objectif en cours.
- [x] Plan appliqué (Sprint 1): commande `/bot task delete <id>` pour supprimer une tâche fermée ciblée.
- [x] Plan appliqué (Sprint 1): commande `/bot task start <id>` pour forcer une tâche en ACTIVE.
- [x] Plan appliqué (Phase 1.2): activation dynamique des modules au boot via `AIPLAYER_ENABLED_MODULES`.
- [x] Plan appliqué (Ops): script `scripts/check-server-capacity.ps1` ajouté pour vérifier la cible 6c/24GB (host + Docker).
- [x] Plan appliqué (Phase 1.2): persistance SQLite des modules activés (reload automatique au boot, sauf override env).
- [x] Plan appliqué (Phase 1.2): commande `/aiplayer module reset` pour réactiver le set complet de modules.
- [x] Plan appliqué (Phase 1.2): commande `/aiplayer module disable-all` pour couper tous les modules actifs en une action.
- [x] Plan appliqué (Phase 1.2): commande /aiplayer module status <name> pour diagnostiquer rapidement l'état d'un module.
- [x] Plan appliqué (Phase 1.2): commande `/aiplayer module reload` pour recharger la config modules depuis SQLite (hors override env).
- [x] Plan appliqué (Phase 1.2): commande `/aiplayer module source` pour afficher la source de configuration modules (env vs SQLite).
