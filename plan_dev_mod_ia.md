# Plan d'Action BMAD : Mod NeoForge IA Player Autonome (1.21.1)

## 🎯 Vision Produit
**Créer un mod NeoForge qui spawne une entité IA autonome capable de :**
- Survivre seule (récolte, craft, construction basique)
- Gérer une colonie MineColonies (ou approximation via système custom)
- Interagir & collaborer avec les joueurs du serveur
- Apprendre via mémoire persistante (SQLite) + LLM (Ollama local)
- Évoluer au fil du temps (phases: survie → fermes → infrastructure)

**Cibles:** Serveur NeoForge 6 cores/24 Go RAM + Ollama local + MineColonies

---

## 🛠️ BMAD Breakdown

### Phase 1 : DEFINITION (Semaine 1)

#### 1.1 Comprendre le contexte
- [ ] Valider version NeoForge exacte (1.21.1 ? check build.gradle)
- [ ] Lister mods serveur (MineColonies, Create, Mekanism, etc.)
- [ ] Décider : bot remplace Town Hall owner ou NPC dans colonie ?
- [ ] Définir scope pédagogique (quoi apprendre aux élèves via bot ?)

#### 1.2 Architecture système
```
┌─────────────────────────────────────────────────────────────┐
│                   MINECRAFT SERVER (NeoForge)               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  AIPlayerMod (Custom Mod)                            │   │
│  │  ├─ AIBotEntity (Entité custom)                      │   │
│  │  ├─ BotBrain (FSM + Décisions)                       │   │
│  │  ├─ MemoryManager (SQLite persistance)               │   │
│  │  ├─ OllamaClient (HTTP Ollama)                       │   │
│  │  └─ MineColoniesIntegration (API mod)                │   │
│  └──────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Autres mods (MineColonies, Create, etc.)            │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              ↕ (HTTP)
                    ┌─────────────────┐
                    │ Ollama Local    │
                    │ (Qwen3:8b)      │
                    └─────────────────┘
```

#### 1.3 Cas d'usage clés
1. **Autonomie basique** : `/bot spawn Alex` → bot survit seul (mine, craft, mange)
2. **Colonie** : Bot gère MineColonies (requests, assign jobs, upgrade)
3. **Collab joueurs** : Joueur dit "/bot task <objectif>" → bot exécute + apprend
4. **Persistance** : Shutdown serveur → bot reprend tâches (~stateless mais memory)

---

### Phase 2 : DESIGN & ARCHITECTURE (Semaine 1-2)

#### 2.1 Composants core
**A. AIBotEntity (extends PathfinderMob)**
- Skin custom ou model vanilla
- Health/Hunger/Saturation tracking
- Inventory (39 slots vanilla)
- Navigation + pathfinding

**B. BotBrain (FSM principale)**
```
États: IDLE → HUNGRY → GATHER → CRAFT → BUILD → SLEEP → INTERACT
Transitions: état + LLM feedback
Tick rate: 20 ticks/sec (décisions tous les 30s-1min)
```

**C. MemoryManager**
```
DB Schema:
- tasks (id, desc, status, priority, created_at, updated_at)
- inventory_log (bot_id, items_count, resources, timestamp)
- interactions (player_id, action, response, timestamp)
- learned_skills (skill_name, success_rate, last_used)
```

**D. OllamaClient**
```
POST http://localhost:11434/api/chat
{
  "model": "qwen3:8b",
  "context": "<state: position, inventory, nearby, task>",
  "prompt": "Mon objectif: [task]. Prochaine action ? (mine/craft/move/build)"
}
→ Response: Action + Reasoning
```

**E. MineColoniesHook (Reflection/Events)**
- Listen TownHall events
- Access Citizen management
- Respect perms (bot doit être owner ou authorized)

#### 2.2 Flux décisionnel
```
Tick (20x/sec) :
1. Scan état (position, faim, inven, environs)
2. Query Ollama (~1min interval, cache réponse)
   → Priorité action: SURVIVAL > COLONY > LEARNING
3. Exécute action:
   - MOVE: Pathfind + nav
   - MINE: Hit block + collect drops
   - CRAFT: Recipe → crafting table
   - BUILD: Place blocs (schematic si dispo)
   - INTERACT: Chat joueur / MineColonies API
4. Log outcome → Memory (success/fail)
5. Sleep (night ou full tiredness)
```

#### 2.3 Config fichier
```yaml
# config/aiplayer/bot.yml
bot:
  model: "qwen3:8b"
  ollama_url: "http://localhost:11434"
  decision_interval_ticks: 600  # 30 sec
  memory_db: "aiplayer.db"
  
behaviors:
  hunger_threshold: 6
  sleep_time: "20:00-06:00"
  build_mode: "schematic"  # ou "custom"
  minecolonies_integration: true
  
learning:
  enable_persistence: true
  success_bonus: 10
  failure_penalty: -5
```

---

### Phase 3 : BUILD ITERATIF (Semaines 2-6)

#### **Sprint 1 (3-4 jours) : MVP Basique**
- [ ] Setup NeoForge MDK (build.gradle, maven repo)
- [ ] Classe AIBotEntity minimaliste (extends PathfinderMob)
- [ ] Spawn command `/bot spawn <name>`
- [ ] Pathfinding vanilla (navigue sans collision)
- [ ] **Deliverable** : Bot spawn, walk, détecte blocks → testable

**Tests:**
```
/bot spawn Alice
/tp @s <pos>  → Alice follow
```

#### **Sprint 2 (4-5 jours) : Survival Loop**
- [ ] Health/Hunger/Saturation management
- [ ] Harvest crops + eat
- [ ] Mining (detects ores, collect drops)
- [ ] Basic crafting (workbench, furnace)
- [ ] Sleep system (night → bed)
- [ ] **Deliverable** : Bot survit seul 24h sans joueur

**Tests:**
```
/bot spawn Bob
(leave 24h)
→ Bot doit avoir health > 0, inven avec resources
```

#### **Sprint 3 (5-6 jours) : Ollama Integration**
- [ ] HTTP client Ollama (OkHttp)
- [ ] BotBrain FSM simple (3-5 états clés)
- [ ] Query Ollama tous les 30s (decision cache)
- [ ] Action exécution basée réponse LLM
- [ ] Memory logging SQLite (tasks, interactions)
- [ ] **Deliverable** : Bot prend décisions via LLM, persiste

**Tests:**
```
Config Ollama: ollama run qwen3:8b
/bot spawn Charlie learning:true
→ Logs indiquent Ollama queries, state dans SQLite
```

#### **Sprint 4 (5-7 jours) : MineColonies Hook**
- [ ] Détecte Town Hall (BlockEntity)
- [ ] Accès Citizen API (reflection si privé)
- [ ] Assigne tâches citoyens ou spawn virtual citizens
- [ ] Gère requests system (deposit items → warehouse)
- [ ] Ou: Bot remplace mayor pour décisions (avancé)
- [ ] **Deliverable** : Bot interagit avec colonie existante

**Tests:**
```
/bot spawn Diana minecolonies:true
→ Town Hall détecté, bot assign tasks ou manage
→ Colonie évolue avec bot actions
```

#### **Sprint 5 (4-5 jours) : Player Interaction**
- [ ] Command `/bot task <objective>`
- [ ] Chat command `/bot ask <question>`
- [ ] Bot respond chat (async Ollama)
- [ ] Log interactions (player→bot→action)
- [ ] Collaborative builds (joueur + bot)
- [ ] **Deliverable** : Joueurs donnent ordres → bot apprend patterns

**Tests:**
```
/bot task "Build me a house at X Y Z"
→ Bot génère schematic, start build
→ Joueur see logs, step-by-step actions
```

#### **Sprint 6 (4-5 jours) : Evolution & Persistence**
- [ ] Phases: Jour1 (survie) → Sem1 (fermes) → Mois1 (infrastructure)
- [ ] Unlock skills via XP (mining speed, craft precision)
- [ ] Sauvegarde state entre shutdowns (position, tâches, memory)
- [ ] UI simple (/bot status → dashboard)
- [ ] Optimisations perf (batch requests, cache)
- [ ] **Deliverable** : Bot persiste, évolue, durable long-term

**Tests:**
```
/bot status
→ Shows phase, skills, XP, memory recap
(Shutdown server, restart)
→ Bot reprend tâches précédentes
```

---

### Phase 4 : TEST & POLISH (Semaine 6-7)

#### 4.1 QA
- [ ] Mono-serveur test: 10 joueurs + 1 IA (perf ?)
- [ ] Serveur "stressé": MineColonies + Create + IA → TPS check
- [ ] Edge cases:
  - Bot mort → respawn logic
  - Terrain modifié → recalc pathfinding
  - Mod conflicts (Create contraptions, etc.)
  - Ollama timeout → fallback behavior

#### 4.2 Documentations
- [ ] README GitHub (install, config, usage)
- [ ] Wiki mod (FAQ, commands, examples)
- [ ] Setup guide Ollama
- [ ] Troubleshooting (perf, crashes)

#### 4.3 Optimisations
- [ ] Batch Ollama queries (multi-decision per query)
- [ ] Cache paths, recipes
- [ ] Async DB writes
- [ ] Limit AI decision frequency if TPS low

---

## 📊 Roadmap Timeline

| Phase | Durée | Jalons |
|-------|-------|--------|
| **Definition** | 3-4 jours | Design doc + Setup MDK |
| **Design** | 3-4 jours | Architecture finalisée |
| **Sprint 1-2** (MVP) | 1 semaine | Bot survit seul |
| **Sprint 3-4** (IA + Colonie) | 1.5 semaine | Ollama + MineColonies hook |
| **Sprint 5-6** (Interaction) | 1.5 semaine | Player commands + evolution |
| **QA + Release** | 1 semaine | Tests, docs, optimisations |
| **TOTAL** | **~5-6 semaines** | Mod 1.0 production-ready |

---

## 💾 Tech Stack

- **Dev:** IntelliJ IDEA + NeoForge MDK 1.21.1
- **Language:** Java 21
- **Libs:**
  - OkHttp (HTTP Ollama)
  - Gson (JSON)
  - SQLite JDBC (persistence)
  - Apache Commons Math (learning: Q-table)
  - NeoForge API (entity, events, commands)
- **IA:** Ollama (Qwen3:8b local)
- **VCS:** Git/GitHub
- **Collab tools:** GitHub Issues (backlog), Discussions

---

## 🎓 Bonus : Pédagogique (pour tes élèves)

- **Montrer:** IA évolution vs hardcoded behavior
- **Lab:** Élèves modifient prompt Ollama → observe changement
- **Project:** Élèves créent colonie, bot la gère autonome
- **Challenge:** "Qui peut faire une colonie que le bot gère mieux ?"

---

## ⚠️ Risques & Mitigation

| Risque | Impact | Mitigation |
|--------|--------|-----------|
| Ollama trop lent | Lag serveur | Cache decisions, fallback FSM simple |
| MineColonies API private | Pas d'integration | Use reflection ou events hooks |
| Bot stuck (pathfinding) | Infinité attente | Timeout + random teleport |
| Memory DB corruption | Perte state | Regular backups, transactions |
| Mod conflicts (Create) | CTD/bugs | Test combos, event priority |

---

## 📋 Checklist Go/No-Go

Avant de start coding:
- [ ] Version NeoForge 1.21.1 exacte confirmée
- [ ] Liste mods serveur finalisée
- [ ] MineColonies version + API understanding
- [ ] Ollama sur machine test (Qwen3:8b ok ?)
- [ ] GitHub repo créé (issues, discussions)
- [ ] 6 core/24 Go RAM serveur = validated ✓

**Go?** ✅ Oui, tout est prêt → Start Sprint 1
