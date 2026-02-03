# Interface ComputerCraft (CC: Tweaked)

## Statut

- Modulaire: `ComputerCraftModule` actif dans l'architecture du bot.
- Intégration API Java: **non branchée** à ce stade (documentation et contrat d'interface définis).
- Objectif sprint suivant: brancher l'exécution réelle des actions via peripherals/turtles.

## Contrat d'interface visé

Le module expose 4 opérations logiques:

1. `listPeripherals()`
   - Retourne les noms/types des périphériques visibles.
2. `runCommand(target, command, args)`
   - Exécute une commande ciblée (`computer`/`turtle`).
3. `readState(target)`
   - Retourne un état minimal (fuel, inventory, busy/idle, lastError).
4. `stop(target)`
   - Demande l'arrêt propre d'une tâche en cours.

## Mapping prévu (CC: Tweaked)

- **Computers**: `peripheral.call(<name>, ...)` pour lancer les primitives disponibles.
- **Turtles**: opérations `forward`, `turnLeft`, `dig`, `place`, `drop`, etc.
- **Inventaire**: extraction via méthodes d'inventaire des peripherals exposés.
- **Sécurité**: whitelist de commandes autorisées + timeout côté runtime.

## Gestion d'erreurs

- Périphérique introuvable -> `NOT_FOUND`
- Commande non autorisée -> `DENIED`
- Timeout d'exécution -> `TIMEOUT`
- Erreur interne -> `FAILED`

Les erreurs doivent être persistées dans `bot_actions` pour audit.

## Commandes serveur (prochain incrément)

- `/aiplayer cc status`
- `/aiplayer cc list`
- `/aiplayer cc run <target> <command> [args...]`
- `/aiplayer cc stop <target>`

## Référence code actuelle

- `src/main/java/com/aiplayer/mod/modules/ComputerCraftModule.java`
