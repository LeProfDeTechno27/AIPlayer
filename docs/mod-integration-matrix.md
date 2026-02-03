# Mod Integration Matrix (Sprint 1 Input)

Generated: 2026-02-02 23:39:28
Total mod jars detected: 421

| Module | Priority | Status | Matches | Example jar | Roadmap role |
|---|---|---|---:|---|---|
| MineColonies | P0 | Present | 1 | minecolonies-1.1.1260-1.21.1-snapshot.jar | Town Hall owner and colony governance |
| Applied Energistics 2 | P0 | Present | 2 | AE2-Things-1.4.2-beta.jar | Storage and auto-crafting network |
| ComputerCraft / CC:Tweaked | P0 | Present | 1 | cc-tweaked-1.21.1-forge-1.115.1.jar | Lua turtles and scripted automation |
| Create | P0 | Present | 1 | create-1.21.1-6.0.4.jar | Mechanical automation and farms |
| Mekanism | P1 | Present | 8 | MekanismGenerators-1.21.1-10.7.14.79.jar | Future expansion module target |
| Immersive Engineering | P1 | Present | 1 | ImmersiveEngineering-1.21.1-12.3.1-189.jar | Future expansion module target |
| Refined Storage | P2 | Present | 4 | refinedstorage-mekanism-integration-1.0.0.jar | Alt storage ecosystem compatibility |
| KubeJS | P2 | Present | 2 | kubejs-neoforge-2101.7.1-build.181.jar | Server scripting and custom logic |
| FTB Quests | P2 | Present | 1 | ftb-quests-neoforge-2101.1.9.jar | Quest/mission integration potential |

Summary: 9 present, 0 missing.

## Next technical steps
1. Create NeoForge mod skeleton for `AIPlayerMod` (Sprint 1 baseline).
2. Add `ModuleManager` + `IBotModule` interfaces (MineColonies/AE2/CC/Create stubs).
3. Add dedicated server command to spawn and control the bot entity.
4. Persist basic bot state and metrics to SQLite.
