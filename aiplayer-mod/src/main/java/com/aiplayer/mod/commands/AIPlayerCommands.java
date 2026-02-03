package com.aiplayer.mod.commands;

import com.aiplayer.mod.core.AIPlayerRuntime;
import com.aiplayer.mod.integrations.AE2Bridge;
import com.aiplayer.mod.integrations.MineColoniesBridge;
import com.aiplayer.mod.persistence.BotMemoryRepository;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

public final class AIPlayerCommands {
    private static final String DEFAULT_COLONY_NAME = "aiplayer";
    private static final String DEFAULT_COLONY_STYLE = "medievaloak";
    private static final int DEFAULT_RECRUIT_COUNT = 3;

    private static final int DEFAULT_AE2_RADIUS = 12;
    private static final int DEFAULT_AE2_CRAFT_QUANTITY = 1;
    private static final int DEFAULT_AE2_QUEUE_LIMIT = 10;
    private static final int DEFAULT_BOT_TASK_LIST_LIMIT = 5;
    private static final int DEFAULT_BOT_INTERACTION_LIST_LIMIT = 5;

    private static final List<String> COLONY_STYLE_SUGGESTIONS = List.of(
        "medievaloak",
        "medievalbirch",
        "medievaldarkoak",
        "medievalspruce",
        "fortress",
        "nordic",
        "caledonia",
        "colonial",
        "pagoda",
        "original"
    );

    private AIPlayerCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, AIPlayerRuntime runtime) {
        dispatcher.register(
            Commands.literal("bot")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(context -> spawnMarkerNamed(
                            context.getSource(),
                            runtime,
                            StringArgumentType.getString(context, "name")
                        )))
                    .executes(context -> spawnMarkerNamed(context.getSource(), runtime, "AIPlayer Bot")))
                .then(Commands.literal("status")
                    .executes(context -> showStatus(context.getSource(), runtime)))
                .then(Commands.literal("task")
                    .then(Commands.argument("objective", StringArgumentType.greedyString())
                        .executes(context -> botTask(
                            context.getSource(),
                            runtime,
                            StringArgumentType.getString(context, "objective")
                        ))))
                .then(Commands.literal("tasks")
                    .executes(context -> botTasks(context.getSource(), runtime, DEFAULT_BOT_TASK_LIST_LIMIT))
                    .then(Commands.argument("limit", IntegerArgumentType.integer(1, 20))
                        .executes(context -> botTasks(
                            context.getSource(),
                            runtime,
                            IntegerArgumentType.getInteger(context, "limit")
                        ))))
                .then(Commands.literal("ask")
                    .then(Commands.argument("question", StringArgumentType.greedyString())
                        .executes(context -> botAsk(
                            context.getSource(),
                            runtime,
                            StringArgumentType.getString(context, "question")
                        ))))
                .then(Commands.literal("interactions")
                    .executes(context -> botInteractions(context.getSource(), runtime, DEFAULT_BOT_INTERACTION_LIST_LIMIT))
                    .then(Commands.argument("limit", IntegerArgumentType.integer(1, 20))
                        .executes(context -> botInteractions(
                            context.getSource(),
                            runtime,
                            IntegerArgumentType.getInteger(context, "limit")
                        ))))
        );

        dispatcher.register(
            Commands.literal("aiplayer")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status")
                    .executes(context -> showStatus(context.getSource(), runtime)))
                .then(Commands.literal("spawn")
                    .executes(context -> spawnMarker(context.getSource(), runtime)))
                .then(Commands.literal("despawn")
                    .executes(context -> despawnMarker(context.getSource(), runtime)))
                .then(Commands.literal("tick")
                    .executes(context -> tickOnce(context.getSource(), runtime)))
                .then(Commands.literal("phase")
                    .then(Commands.argument("value", StringArgumentType.word())
                        .executes(context -> setPhase(context.getSource(), runtime, StringArgumentType.getString(context, "value")))))
                .then(Commands.literal("module")
                    .then(Commands.literal("list")
                        .executes(context -> listModules(context.getSource(), runtime)))
                    .then(Commands.literal("enable")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(runtime.getRegisteredModules(), builder))
                            .executes(context -> enableModule(context.getSource(), runtime, StringArgumentType.getString(context, "name")))))
                    .then(Commands.literal("disable")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(runtime.getRegisteredModules(), builder))
                            .executes(context -> disableModule(context.getSource(), runtime, StringArgumentType.getString(context, "name"))))))
                .then(Commands.literal("colony")
                    .then(Commands.literal("status")
                        .executes(context -> colonyStatus(context.getSource(), runtime)))
                    .then(Commands.literal("create")
                        .executes(context -> colonyCreateDefault(context.getSource(), runtime))
                        .then(Commands.argument("name", StringArgumentType.word())
                            .then(Commands.argument("style", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(COLONY_STYLE_SUGGESTIONS, builder))
                                .executes(context -> colonyCreateCustom(
                                    context.getSource(),
                                    runtime,
                                    StringArgumentType.getString(context, "name"),
                                    StringArgumentType.getString(context, "style")
                                )))))
                    .then(Commands.literal("claim")
                        .executes(context -> colonyClaim(context.getSource(), runtime)))
                    .then(Commands.literal("recruit")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 20))
                            .executes(context -> colonyRecruit(
                                context.getSource(),
                                runtime,
                                IntegerArgumentType.getInteger(context, "count")
                            )))))
                .then(Commands.literal("ae2")
                    .then(Commands.literal("status")
                        .executes(context -> ae2Status(context.getSource(), runtime, DEFAULT_AE2_RADIUS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(2, 32))
                            .executes(context -> ae2Status(
                                context.getSource(),
                                runtime,
                                IntegerArgumentType.getInteger(context, "radius")
                            ))))
                    .then(Commands.literal("suggest")
                        .executes(context -> ae2Suggest(context.getSource(), runtime, DEFAULT_AE2_RADIUS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(2, 32))
                            .executes(context -> ae2Suggest(
                                context.getSource(),
                                runtime,
                                IntegerArgumentType.getInteger(context, "radius")
                            ))))
                    .then(Commands.literal("api")
                        .executes(context -> ae2Api(context.getSource(), runtime)))
                    .then(Commands.literal("craft")
                        .then(Commands.argument("itemId", StringArgumentType.word())
                            .executes(context -> ae2Craft(
                                context.getSource(),
                                runtime,
                                StringArgumentType.getString(context, "itemId"),
                                DEFAULT_AE2_CRAFT_QUANTITY
                            ))
                            .then(Commands.argument("quantity", IntegerArgumentType.integer(1, 4096))
                                .executes(context -> ae2Craft(
                                    context.getSource(),
                                    runtime,
                                    StringArgumentType.getString(context, "itemId"),
                                    IntegerArgumentType.getInteger(context, "quantity")
                                )))))
                    .then(Commands.literal("queue")
                        .executes(context -> ae2Queue(context.getSource(), runtime, DEFAULT_AE2_QUEUE_LIMIT))
                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, 25))
                            .executes(context -> ae2Queue(
                                context.getSource(),
                                runtime,
                                IntegerArgumentType.getInteger(context, "limit")
                            )))))
        );
    }

    private static int showStatus(CommandSourceStack source, AIPlayerRuntime runtime) {
        StringJoiner joiner = new StringJoiner(", ");
        runtime.getEnabledModules().forEach(joiner::add);

        source.sendSuccess(
            () -> Component.literal(
                "phase=" + runtime.getPhase()
                    + " | objective=" + runtime.getCurrentObjective()
                    + " | botTasks=" + runtime.countOpenBotTasks()
                    + " | modules=" + joiner
            ),
            false
        );
        return 1;
    }

    private static int spawnMarker(CommandSourceStack source, AIPlayerRuntime runtime) {
        boolean spawned = runtime.spawnMarker(source.getLevel(), source.getPosition());
        if (!spawned) {
            source.sendFailure(Component.literal("Unable to spawn bot marker"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Bot marker spawned"), false);
        return 1;
    }

    private static int spawnMarkerNamed(CommandSourceStack source, AIPlayerRuntime runtime, String markerName) {
        boolean spawned = runtime.spawnMarker(source.getLevel(), source.getPosition(), markerName);
        if (!spawned) {
            source.sendFailure(Component.literal("Unable to spawn bot marker"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Bot marker spawned: " + markerName), false);
        return 1;
    }

    private static int despawnMarker(CommandSourceStack source, AIPlayerRuntime runtime) {
        boolean removed = runtime.despawnMarker(source.getLevel());
        if (!removed) {
            source.sendFailure(Component.literal("No tracked bot marker to despawn"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Bot marker removed"), false);
        return 1;
    }

    private static int tickOnce(CommandSourceStack source, AIPlayerRuntime runtime) {
        runtime.tickOnce();
        source.sendSuccess(() -> Component.literal("AIPlayer tick executed"), false);
        return 1;
    }

    private static int setPhase(CommandSourceStack source, AIPlayerRuntime runtime, String phase) {
        runtime.setPhase(phase);
        source.sendSuccess(() -> Component.literal("Phase set to " + phase), false);
        return 1;
    }

    private static int botTask(CommandSourceStack source, AIPlayerRuntime runtime, String objective) {
        String requestedBy = source.getTextName();
        long taskId = runtime.queueBotTask(objective, requestedBy);
        if (taskId <= 0) {
            source.sendFailure(Component.literal("Impossible de creer la tache bot"));
            return 0;
        }

        source.sendSuccess(
            () -> Component.literal("Bot task queued id=" + taskId + " objective=" + objective),
            false
        );
        return 1;
    }

    private static int botTasks(CommandSourceStack source, AIPlayerRuntime runtime, int limit) {
        List<BotMemoryRepository.BotTask> tasks = runtime.getOpenBotTasks(limit);
        int total = runtime.countOpenBotTasks();

        if (tasks.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Bot tasks: none (open=" + total + ")"), false);
            return 1;
        }

        StringJoiner joiner = new StringJoiner(" | ");
        tasks.forEach(task -> joiner.add("#" + task.id() + " " + task.status() + " " + task.objective()));
        source.sendSuccess(() -> Component.literal("Bot tasks open=" + total + " -> " + joiner), false);
        return 1;
    }

    private static int botAsk(CommandSourceStack source, AIPlayerRuntime runtime, String question) {
        String playerId = source.getTextName();
        long interactionId = runtime.askBot(playerId, question);
        if (interactionId <= 0) {
            source.sendFailure(Component.literal("Impossible d'enregistrer l'interaction bot"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Bot ask logged id=" + interactionId + " question=" + question), false);
        return 1;
    }

    private static int botInteractions(CommandSourceStack source, AIPlayerRuntime runtime, int limit) {
        List<BotMemoryRepository.InteractionRecord> records = runtime.getRecentInteractions(limit);
        if (records.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Bot interactions: none"), false);
            return 1;
        }

        StringJoiner joiner = new StringJoiner(" | ");
        records.forEach(record -> joiner.add("#" + record.id() + " " + record.playerId() + ": " + record.question()));
        source.sendSuccess(() -> Component.literal("Bot interactions -> " + joiner), false);
        return 1;
    }

    private static int listModules(CommandSourceStack source, AIPlayerRuntime runtime) {
        StringJoiner registered = new StringJoiner(", ");
        runtime.getRegisteredModules().forEach(registered::add);

        StringJoiner enabled = new StringJoiner(", ");
        runtime.getEnabledModules().forEach(enabled::add);

        source.sendSuccess(
            () -> Component.literal("registered=[" + registered + "] enabled=[" + enabled + "]"),
            false
        );
        return 1;
    }

    private static int enableModule(CommandSourceStack source, AIPlayerRuntime runtime, String moduleName) {
        if (!runtime.enableModule(moduleName)) {
            source.sendFailure(Component.literal("Unknown module: " + moduleName));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Module enabled: " + moduleName), false);
        return 1;
    }

    private static int disableModule(CommandSourceStack source, AIPlayerRuntime runtime, String moduleName) {
        if (!runtime.disableModule(moduleName)) {
            source.sendFailure(Component.literal("Module already disabled or unknown: " + moduleName));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Module disabled: " + moduleName), false);
        return 1;
    }

    private static int colonyStatus(CommandSourceStack source, AIPlayerRuntime runtime) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(source);

        if (!runtime.isMineColoniesAvailable()) {
            source.sendFailure(Component.literal("MineColonies n'est pas charge"));
            return 0;
        }

        Optional<MineColoniesBridge.ColonyInfo> colonyOptional = runtime.getOwnedMineColoniesColony(player);
        if (colonyOptional.isEmpty()) {
            source.sendSuccess(() -> Component.literal("MineColonies OK. Aucune colonie owner pour " + player.getName().getString()), false);
            return 1;
        }

        MineColoniesBridge.ColonyInfo colony = colonyOptional.get();
        source.sendSuccess(
            () -> Component.literal(
                "colony id=" + colony.id()
                    + " name=" + colony.name()
                    + " citizens=" + colony.citizenCount()
                    + " owner=" + colony.ownerId()
            ),
            false
        );
        return 1;
    }

    private static int colonyCreateDefault(CommandSourceStack source, AIPlayerRuntime runtime) throws CommandSyntaxException {
        return colonyCreateCustom(source, runtime, DEFAULT_COLONY_NAME, DEFAULT_COLONY_STYLE);
    }

    private static int colonyCreateCustom(CommandSourceStack source, AIPlayerRuntime runtime, String colonyName, String styleName)
        throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(source);
        MineColoniesBridge.BridgeResult result = runtime.createMineColoniesColony(
            player,
            colonyName,
            styleName,
            DEFAULT_RECRUIT_COUNT
        );
        return sendBridgeResult(source, result);
    }

    private static int colonyClaim(CommandSourceStack source, AIPlayerRuntime runtime) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(source);
        MineColoniesBridge.BridgeResult result = runtime.claimMineColoniesColony(player);
        return sendBridgeResult(source, result);
    }

    private static int colonyRecruit(CommandSourceStack source, AIPlayerRuntime runtime, int recruitCount)
        throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(source);
        MineColoniesBridge.BridgeResult result = runtime.recruitMineColoniesCitizens(player, recruitCount);
        return sendBridgeResult(source, result);
    }

    private static int ae2Status(CommandSourceStack source, AIPlayerRuntime runtime, int radius) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(source);

        if (!runtime.isAe2Available()) {
            source.sendFailure(Component.literal("AE2 n'est pas charge"));
            return 0;
        }

        AE2Bridge.AE2ScanResult scan = runtime.scanAe2(player, radius);
        source.sendSuccess(
            () -> Component.literal(
                "AE2 " + scan.summary() + " radius=" + scan.radius()
            ),
            false
        );
        return 1;
    }

    private static int ae2Suggest(CommandSourceStack source, AIPlayerRuntime runtime, int radius) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(source);

        if (!runtime.isAe2Available()) {
            source.sendFailure(Component.literal("AE2 n'est pas charge"));
            return 0;
        }

        AE2Bridge.AE2ScanResult scan = runtime.scanAe2(player, radius);
        source.sendSuccess(
            () -> Component.literal("AE2 stage=" + scan.stage() + " -> " + scan.nextActionHint()),
            false
        );
        return 1;
    }

    private static int ae2Api(CommandSourceStack source, AIPlayerRuntime runtime) {
        AE2Bridge.AE2ApiProbeResult probe = runtime.probeAe2Api();
        String details = "found=" + probe.foundCount() + "/" + probe.expectedCount();

        if (!runtime.isAe2Available()) {
            source.sendFailure(Component.literal("AE2 n'est pas charge (" + details + ")"));
            return 0;
        }

        if (!probe.accessible()) {
            source.sendFailure(Component.literal("AE2 API indisponible: " + details));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("AE2 API OK: " + details), false);
        return 1;
    }

    private static int ae2Craft(CommandSourceStack source, AIPlayerRuntime runtime, String itemId, int quantity)
        throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(source);

        if (!runtime.isAe2Available()) {
            source.sendFailure(Component.literal("AE2 n'est pas charge"));
            return 0;
        }

        long requestId = runtime.queueAe2CraftRequest(itemId, quantity, player.getGameProfile().getName());
        if (requestId <= 0) {
            source.sendFailure(Component.literal("Impossible de creer la requete AE2"));
            return 0;
        }

        source.sendSuccess(
            () -> Component.literal("AE2 request queued id=" + requestId + " item=" + itemId + " qty=" + quantity),
            false
        );
        return 1;
    }

    private static int ae2Queue(CommandSourceStack source, AIPlayerRuntime runtime, int limit) {
        List<BotMemoryRepository.AE2CraftRequest> requests = runtime.getPendingAe2CraftRequests(limit);
        int pendingCount = runtime.countPendingAe2CraftRequests();

        if (requests.isEmpty()) {
            source.sendSuccess(() -> Component.literal("AE2 queue vide (pending=" + pendingCount + ")"), false);
            return 1;
        }

        StringJoiner joiner = new StringJoiner(" | ");
        requests.forEach(request -> joiner.add("#" + request.id() + " " + request.itemId() + " x" + request.quantity()));

        source.sendSuccess(
            () -> Component.literal("AE2 queue pending=" + pendingCount + " -> " + joiner),
            false
        );
        return 1;
    }

    private static ServerPlayer requirePlayer(CommandSourceStack source) throws CommandSyntaxException {
        return source.getPlayerOrException();
    }

    private static int sendBridgeResult(CommandSourceStack source, MineColoniesBridge.BridgeResult result) {
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(result.message()), false);
        return 1;
    }
}