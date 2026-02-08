package com.aiplayer.mod.commands;

import com.aiplayer.mod.core.AIPlayerRuntime;
import com.aiplayer.mod.core.BotBrain;
import com.aiplayer.mod.integrations.AE2Bridge;
import com.aiplayer.mod.integrations.MineColoniesBridge;
import com.aiplayer.mod.persistence.BotMemoryRepository;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.StringJoiner;

public final class AIPlayerCommands {
    private static final String DEFAULT_COLONY_NAME = "aiplayer";
    private static final String DEFAULT_COLONY_STYLE = "medievaloak";
    private static final int DEFAULT_RECRUIT_COUNT = 3;
    private static final int DEFAULT_COLONY_ENSURE_COUNT = 4;
    private static final int DEFAULT_COLONY_REQUEST_COUNT = 16;

    private static final int DEFAULT_AE2_RADIUS = 12;
    private static final int DEFAULT_AE2_CRAFT_QUANTITY = 1;
    private static final int DEFAULT_AE2_QUEUE_LIMIT = 10;
    private static final int DEFAULT_AE2_DISPATCH_LIMIT = 5;
    private static final int DEFAULT_AE2_CLEAR_LIMIT = 25;
    private static final int DEFAULT_AE2_HISTORY_LIMIT = 10;

    private static final int DEFAULT_AE2_REPLAY_LIMIT = 25;
    private static final int DEFAULT_AE2_EXPORT_LIMIT = 25;
    private static final int DEFAULT_BOT_TASK_LIST_LIMIT = 5;
    private static final int DEFAULT_BOT_TASK_PRUNE_LIMIT = 20;
    private static final int DEFAULT_BOT_INTERACTION_LIST_LIMIT = 5;

    private static final List<String> BOT_TASK_STATUS_SUGGESTIONS = List.of("PENDING", "ACTIVE", "DONE", "CANCELED");
    private static final List<String> AE2_REQUEST_STATUS_SUGGESTIONS = List.of("PENDING", "DISPATCHED", "FAILED", "DONE", "CANCELED");

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
        var botTaskCommand = Commands.literal("task")
            .then(Commands.literal("done")
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                    .executes(context -> botTaskDone(
                        context.getSource(),
                        runtime,
                        IntegerArgumentType.getInteger(context, "id")
                    ))))
            .then(Commands.literal("start")
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                    .executes(context -> botTaskStart(
                        context.getSource(),
                        runtime,
                        IntegerArgumentType.getInteger(context, "id")
                    ))))
            .then(Commands.literal("cancel")
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                    .executes(context -> botTaskCancel(
                        context.getSource(),
                        runtime,
                        IntegerArgumentType.getInteger(context, "id")
                    ))))
            .then(Commands.literal("reopen")
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                    .executes(context -> botTaskReopen(
                        context.getSource(),
                        runtime,
                        IntegerArgumentType.getInteger(context, "id")
                    ))))
            .then(Commands.literal("info")
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                    .executes(context -> botTaskInfo(
                        context.getSource(),
                        runtime,
                        IntegerArgumentType.getInteger(context, "id")
                    ))))
            .then(Commands.literal("update")
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                    .then(Commands.argument("objective", StringArgumentType.greedyString())
                        .executes(context -> botTaskUpdate(
                            context.getSource(),
                            runtime,
                            IntegerArgumentType.getInteger(context, "id"),
                            StringArgumentType.getString(context, "objective")
                        )))))
            .then(Commands.literal("delete")
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                    .executes(context -> botTaskDelete(
                        context.getSource(),
                        runtime,
                        IntegerArgumentType.getInteger(context, "id")
                    ))))
            .then(Commands.argument("objective", StringArgumentType.greedyString())
                .executes(context -> botTask(
                    context.getSource(),
                    runtime,
                    StringArgumentType.getString(context, "objective")
                )));

        var botTasksCommand = Commands.literal("tasks")
            .executes(context -> botTasks(context.getSource(), runtime, DEFAULT_BOT_TASK_LIST_LIMIT))
            .then(Commands.literal("all")
                .executes(context -> botTasks(context.getSource(), runtime, DEFAULT_BOT_TASK_LIST_LIMIT, true))
                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 20))
                    .executes(context -> botTasks(
                        context.getSource(),
                        runtime,
                        IntegerArgumentType.getInteger(context, "limit"),
                        true
                    ))))
            .then(Commands.literal("status")
                .then(Commands.argument("status", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(BOT_TASK_STATUS_SUGGESTIONS, builder))
                    .executes(context -> botTasksByStatus(
                        context.getSource(),
                        runtime,
                        StringArgumentType.getString(context, "status"),
                        DEFAULT_BOT_TASK_LIST_LIMIT
                    ))
                    .then(Commands.argument("limit", IntegerArgumentType.integer(1, 20))
                        .executes(context -> botTasksByStatus(
                            context.getSource(),
                            runtime,
                            StringArgumentType.getString(context, "status"),
                            IntegerArgumentType.getInteger(context, "limit")
                        )))))
            .then(Commands.literal("prune")
                .executes(context -> botTasksPrune(context.getSource(), runtime, DEFAULT_BOT_TASK_PRUNE_LIMIT))
                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 200))
                    .executes(context -> botTasksPrune(
                        context.getSource(),
                        runtime,
                        IntegerArgumentType.getInteger(context, "limit")
                    ))))
            .then(Commands.argument("limit", IntegerArgumentType.integer(1, 20))
                .executes(context -> botTasks(
                    context.getSource(),
                    runtime,
                    IntegerArgumentType.getInteger(context, "limit")
                )));

        var botCommand = Commands.literal("bot")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("spawn")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(context -> spawnBotNamed(
                        context.getSource(),
                        runtime,
                        StringArgumentType.getString(context, "name")
                    )))
                .executes(context -> spawnBotNamed(context.getSource(), runtime, "AIPlayer Bot")))
            .then(Commands.literal("status")
                .executes(context -> showStatus(context.getSource(), runtime)))
            .then(Commands.literal("xp")
                .executes(context -> botXpStatus(context.getSource(), runtime))
                .then(Commands.literal("add")
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1, 10000))
                        .executes(context -> botXpAdd(
                            context.getSource(),
                            runtime,
                            IntegerArgumentType.getInteger(context, "amount")
                        ))))
                .then(Commands.literal("set")
                    .then(Commands.argument("amount", IntegerArgumentType.integer(0, 100000))
                        .executes(context -> botXpSet(
                            context.getSource(),
                            runtime,
                            IntegerArgumentType.getInteger(context, "amount")
                        )))))
            .then(botTaskCommand)
            .then(Commands.literal("build")
                .then(Commands.argument("objective", StringArgumentType.greedyString())
                    .executes(context -> botBuild(
                        context.getSource(),
                        runtime,
                        StringArgumentType.getString(context, "objective")
                    ))))
            .then(botTasksCommand)
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
                    ))));

        dispatcher.register(botCommand);

        var ae2QueueCommand = Commands.literal("queue")
            .executes(context -> ae2Queue(context.getSource(), runtime, DEFAULT_AE2_QUEUE_LIMIT))
            .then(Commands.argument("limit", IntegerArgumentType.integer(1, 25))
                .executes(context -> ae2Queue(
                    context.getSource(),
                    runtime,
                    IntegerArgumentType.getInteger(context, "limit")
                )))
            .then(Commands.literal("pending")
                .executes(context -> ae2Queue(context.getSource(), runtime, DEFAULT_AE2_QUEUE_LIMIT))
                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 25))
                    .executes(context -> ae2Queue(
                        context.getSource(),
                        runtime,
                        IntegerArgumentType.getInteger(context, "limit")
                    ))))
            .then(Commands.literal("clear")
                .executes(context -> ae2QueueClear(context.getSource(), runtime, DEFAULT_AE2_CLEAR_LIMIT))
                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 500))
                    .executes(context -> ae2QueueClear(
                        context.getSource(),
                        runtime,
                        IntegerArgumentType.getInteger(context, "limit")
                    ))))
            .then(Commands.literal("retry")
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                    .executes(context -> ae2QueueRetry(
                        context.getSource(),
                        runtime,
                        IntegerArgumentType.getInteger(context, "id")
                    ))))
            .then(Commands.literal("replay")
                .then(Commands.literal("failed")
                    .executes(context -> ae2QueueReplayFailed(
                        context.getSource(),
                        runtime,
                        DEFAULT_AE2_REPLAY_LIMIT
                    ))
                    .then(Commands.argument("limit", IntegerArgumentType.integer(1, 200))
                        .executes(context -> ae2QueueReplayFailed(
                            context.getSource(),
                            runtime,
                            IntegerArgumentType.getInteger(context, "limit")
                        ))))
                .then(Commands.literal("dispatched")
                    .executes(context -> ae2QueueReplayDispatched(
                        context.getSource(),
                        runtime,
                        DEFAULT_AE2_REPLAY_LIMIT
                    ))
                    .then(Commands.argument("limit", IntegerArgumentType.integer(1, 200))
                        .executes(context -> ae2QueueReplayDispatched(
                            context.getSource(),
                            runtime,
                            IntegerArgumentType.getInteger(context, "limit")
                        ))))
                .then(Commands.literal("canceled")
                    .executes(context -> ae2QueueReplayCanceled(
                        context.getSource(),
                        runtime,
                        DEFAULT_AE2_REPLAY_LIMIT
                    ))
                    .then(Commands.argument("limit", IntegerArgumentType.integer(1, 200))
                        .executes(context -> ae2QueueReplayCanceled(
                            context.getSource(),
                            runtime,
                            IntegerArgumentType.getInteger(context, "limit")
                        ))))
                .then(Commands.literal("done")
                    .executes(context -> ae2QueueReplayDone(
                        context.getSource(),
                        runtime,
                        DEFAULT_AE2_REPLAY_LIMIT
                    ))
                    .then(Commands.argument("limit", IntegerArgumentType.integer(1, 200))
                        .executes(context -> ae2QueueReplayDone(
                            context.getSource(),
                            runtime,
                            IntegerArgumentType.getInteger(context, "limit")
                        ))))
                .then(Commands.literal("all")
                    .executes(context -> ae2QueueReplayAll(
                        context.getSource(),
                        runtime,
                        DEFAULT_AE2_REPLAY_LIMIT
                    ))
                    .then(Commands.argument("limit", IntegerArgumentType.integer(1, 200))
                        .executes(context -> ae2QueueReplayAll(
                            context.getSource(),
                            runtime,
                            IntegerArgumentType.getInteger(context, "limit")
                        ))))
                .then(Commands.argument("status", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(AE2_REQUEST_STATUS_SUGGESTIONS, builder))
                    .executes(context -> ae2QueueReplay(
                        context.getSource(),
                        runtime,
                        StringArgumentType.getString(context, "status"),
                        DEFAULT_AE2_REPLAY_LIMIT
                    ))
                    .then(Commands.argument("limit", IntegerArgumentType.integer(1, 200))
                        .executes(context -> ae2QueueReplay(
                            context.getSource(),
                            runtime,
                            StringArgumentType.getString(context, "status"),
                            IntegerArgumentType.getInteger(context, "limit")
                        )))))
            .then(Commands.literal("delete")
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                    .executes(context -> ae2QueueDelete(
                        context.getSource(),
                        runtime,
                        IntegerArgumentType.getInteger(context, "id")
                    ))))
            .then(Commands.literal("done")
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                    .executes(context -> ae2QueueDone(
                        context.getSource(),
                        runtime,
                        IntegerArgumentType.getInteger(context, "id"),
                        "Completed manually"
                    ))
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> ae2QueueDone(
                            context.getSource(),
                            runtime,
                            IntegerArgumentType.getInteger(context, "id"),
                            StringArgumentType.getString(context, "message")
                        )))))
            .then(Commands.literal("cancel")
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                    .executes(context -> ae2QueueCancel(
                        context.getSource(),
                        runtime,
                        IntegerArgumentType.getInteger(context, "id"),
                        "Canceled manually"
                    ))
                    .then(Commands.argument("reason", StringArgumentType.greedyString())
                        .executes(context -> ae2QueueCancel(
                            context.getSource(),
                            runtime,
                            IntegerArgumentType.getInteger(context, "id"),
                            StringArgumentType.getString(context, "reason")
                        )))))
            .then(Commands.literal("stats")
                .executes(context -> ae2QueueStats(context.getSource(), runtime)))
            .then(Commands.literal("purge")
                .executes(context -> ae2QueuePurge(context.getSource(), runtime, 100))
                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 1000))
                    .executes(context -> ae2QueuePurge(
                        context.getSource(),
                        runtime,
                        IntegerArgumentType.getInteger(context, "limit")
                    ))))
            .then(Commands.literal("fail")
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                    .then(Commands.argument("reason", StringArgumentType.greedyString())
                        .executes(context -> ae2QueueFail(
                            context.getSource(),
                            runtime,
                            IntegerArgumentType.getInteger(context, "id"),
                            StringArgumentType.getString(context, "reason")
                        )))))
            .then(Commands.literal("history")
                .executes(context -> ae2QueueHistory(context.getSource(), runtime, DEFAULT_AE2_HISTORY_LIMIT))
                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 200))
                    .executes(context -> ae2QueueHistory(
                        context.getSource(),
                        runtime,
                        IntegerArgumentType.getInteger(context, "limit")
                    )))
                .then(Commands.argument("status", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(AE2_REQUEST_STATUS_SUGGESTIONS, builder))
                    .executes(context -> ae2QueueHistoryByStatus(
                        context.getSource(),
                        runtime,
                        StringArgumentType.getString(context, "status"),
                        DEFAULT_AE2_HISTORY_LIMIT
                    ))
                    .then(Commands.argument("limit", IntegerArgumentType.integer(1, 200))
                        .executes(context -> ae2QueueHistoryByStatus(
                            context.getSource(),
                            runtime,
                            StringArgumentType.getString(context, "status"),
                            IntegerArgumentType.getInteger(context, "limit")
                        )))))
            .then(Commands.literal("export")
                .executes(context -> ae2QueueExport(context.getSource(), runtime, DEFAULT_AE2_EXPORT_LIMIT))
                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 100))
                    .executes(context -> ae2QueueExport(
                        context.getSource(),
                        runtime,
                        IntegerArgumentType.getInteger(context, "limit")
                    ))));

        var ae2Command = Commands.literal("ae2")
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
            .then(ae2QueueCommand)
            .then(Commands.literal("dispatch")
                .executes(context -> ae2Dispatch(context.getSource(), runtime, DEFAULT_AE2_DISPATCH_LIMIT))
                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 25))
                    .executes(context -> ae2Dispatch(
                        context.getSource(),
                        runtime,
                        IntegerArgumentType.getInteger(context, "limit")
                    ))));

        var aiPlayerCommand = Commands.literal("aiplayer")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("status")
                .executes(context -> showStatus(context.getSource(), runtime)))
            .then(Commands.literal("spawn")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(context -> spawnMarkerNamed(
                        context.getSource(),
                        runtime,
                        StringArgumentType.getString(context, "name")
                    )))
                .executes(context -> spawnMarker(context.getSource(), runtime)))
            .then(Commands.literal("despawn")
                .executes(context -> despawnMarker(context.getSource(), runtime)))
            .then(Commands.literal("tick")
                .executes(context -> tickOnce(context.getSource(), runtime)))
            .then(Commands.literal("phase")
                .then(Commands.argument("value", StringArgumentType.word())
                    .executes(context -> setPhase(
                        context.getSource(),
                        runtime,
                        StringArgumentType.getString(context, "value")
                    ))))
            .then(Commands.literal("module")
                .then(Commands.literal("list")
                    .executes(context -> listModules(context.getSource(), runtime)))
                .then(Commands.literal("status")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(runtime.getRegisteredModules(), builder))
                        .executes(context -> moduleStatus(
                            context.getSource(),
                            runtime,
                            StringArgumentType.getString(context, "name")
                        ))))
                .then(Commands.literal("source")
                    .executes(context -> moduleSource(context.getSource(), runtime)))
                .then(Commands.literal("audit")
                    .executes(context -> moduleAudit(context.getSource(), runtime)))
                .then(Commands.literal("drift")
                    .executes(context -> moduleDrift(context.getSource(), runtime)))
                .then(Commands.literal("reconcile")
                    .executes(context -> moduleReconcile(context.getSource(), runtime)))
                .then(Commands.literal("clear-storage")
                    .executes(context -> clearModuleStorage(context.getSource(), runtime)))
                .then(Commands.literal("save")
                    .executes(context -> saveModules(context.getSource(), runtime)))
                .then(Commands.literal("reload")
                    .executes(context -> reloadModules(context.getSource(), runtime)))
                .then(Commands.literal("reset")
                    .executes(context -> resetModules(context.getSource(), runtime)))
                .then(Commands.literal("disable-all")
                    .executes(context -> disableAllModules(context.getSource(), runtime)))
                .then(Commands.literal("enable")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(runtime.getRegisteredModules(), builder))
                        .executes(context -> enableModule(
                            context.getSource(),
                            runtime,
                            StringArgumentType.getString(context, "name")
                        ))))
                .then(Commands.literal("disable")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(runtime.getRegisteredModules(), builder))
                        .executes(context -> disableModule(
                            context.getSource(),
                            runtime,
                            StringArgumentType.getString(context, "name")
                        )))))
            .then(Commands.literal("colony")
                .then(Commands.literal("status")
                    .executes(context -> colonyStatus(context.getSource(), runtime)))
                .then(Commands.literal("townhall")
                    .executes(context -> colonyTownHall(context.getSource(), runtime)))
                .then(Commands.literal("ensure")
                    .executes(context -> colonyEnsure(context.getSource(), runtime, DEFAULT_COLONY_ENSURE_COUNT))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                        .executes(context -> colonyEnsure(
                            context.getSource(),
                            runtime,
                            IntegerArgumentType.getInteger(context, "count")
                        ))))
                .then(Commands.literal("request")
                    .then(Commands.argument("itemId", StringArgumentType.word())
                        .executes(context -> colonyRequest(
                            context.getSource(),
                            runtime,
                            StringArgumentType.getString(context, "itemId"),
                            DEFAULT_COLONY_REQUEST_COUNT
                        ))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 512))
                            .executes(context -> colonyRequest(
                                context.getSource(),
                                runtime,
                                StringArgumentType.getString(context, "itemId"),
                                IntegerArgumentType.getInteger(context, "count")
                            )))))
                .then(Commands.literal("mayor")
                    .executes(context -> colonyMayor(context.getSource(), runtime)))
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
            .then(ae2Command);

        dispatcher.register(aiPlayerCommand);
    }

    private static int showStatus(CommandSourceStack source, AIPlayerRuntime runtime) {
        StringJoiner joiner = new StringJoiner(", ");
        BotBrain.DecisionStats stats = runtime.getDecisionStats();
        runtime.getEnabledModules().forEach(joiner::add);

        source.sendSuccess(
            () -> Component.literal(
                "phase=" + runtime.getPhase()
                    + " | objective=" + runtime.getCurrentObjective()
                    + " | xp=" + runtime.getBotXp() + " (" + runtime.getBotSkillTier() + ")"
                    + " | botTasks=" + runtime.countOpenBotTasks()
                    + " | decisions=" + stats.totalDecisions() + " cache=" + stats.cacheHits() + " rateLimit=" + stats.rateLimitSkips()
                    + " | bot=" + runtime.getBotVitals(source.getLevel())
                        .map(v -> String.format("health=%.1f/%.1f hunger=%d", v.health(), v.maxHealth(), v.hunger()))
                        .orElse("none")
                    + " | survivalMin=" + runtime.getSurvivalMinutes()
                    + " | modules=" + joiner
            ),
            false
        );
        return 1;
    }

    private static int spawnBotNamed(CommandSourceStack source, AIPlayerRuntime runtime, String botName) {
        boolean spawned = runtime.spawnBot(source.getLevel(), source.getPosition(), botName);
        if (!spawned) {
            source.sendFailure(Component.literal("Unable to spawn bot entity"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Bot entity spawned: " + botName), false);
        return 1;
    }

    private static int spawnMarker(CommandSourceStack source, AIPlayerRuntime runtime) {
        boolean spawned = runtime.spawnBot(source.getLevel(), source.getPosition(), "AIPlayer Bot");
        if (!spawned) {
            source.sendFailure(Component.literal("Unable to spawn bot"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Bot spawned"), false);
        return 1;
    }

    private static int spawnMarkerNamed(CommandSourceStack source, AIPlayerRuntime runtime, String markerName) {
        boolean spawned = runtime.spawnBot(source.getLevel(), source.getPosition(), markerName);
        if (!spawned) {
            source.sendFailure(Component.literal("Unable to spawn bot"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Bot spawned: " + markerName), false);
        return 1;
    }

    private static int despawnMarker(CommandSourceStack source, AIPlayerRuntime runtime) {
        boolean removed = runtime.despawnBot(source.getLevel());
        if (!removed) {
            source.sendFailure(Component.literal("No tracked bot to despawn"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Bot despawned"), false);
        return 1;
    }

    private static int tickOnce(CommandSourceStack source, AIPlayerRuntime runtime) {
        runtime.tickOnce(source.getLevel());
        source.sendSuccess(() -> Component.literal("AIPlayer tick executed"), false);
        return 1;
    }

    private static int setPhase(CommandSourceStack source, AIPlayerRuntime runtime, String phase) {
        runtime.setPhase(phase);
        source.sendSuccess(() -> Component.literal("Phase set to " + phase), false);
        return 1;
    }

    private static int botXpStatus(CommandSourceStack source, AIPlayerRuntime runtime) {
        source.sendSuccess(
            () -> Component.literal("Bot xp=" + runtime.getBotXp() + " tier=" + runtime.getBotSkillTier()),
            false
        );
        return 1;
    }

    private static int botXpAdd(CommandSourceStack source, AIPlayerRuntime runtime, int amount) {
        int xp = runtime.addBotXp(amount);
        source.sendSuccess(
            () -> Component.literal("Bot xp=" + xp + " tier=" + runtime.getBotSkillTier()),
            false
        );
        return 1;
    }

    private static int botXpSet(CommandSourceStack source, AIPlayerRuntime runtime, int amount) {
        int xp = runtime.setBotXp(amount);
        source.sendSuccess(
            () -> Component.literal("Bot xp=" + xp + " tier=" + runtime.getBotSkillTier()),
            false
        );
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

    private static int botBuild(CommandSourceStack source, AIPlayerRuntime runtime, String objective) {
        String requestedBy = source.getTextName();
        String payload = "build:" + objective;
        long taskId = runtime.queueBotTask(payload, requestedBy);
        if (taskId <= 0) {
            source.sendFailure(Component.literal("Impossible de creer la tache build"));
            return 0;
        }

        source.sendSuccess(
            () -> Component.literal("Bot build task queued id=" + taskId + " objective=" + objective),
            false
        );
        return 1;
    }
    private static int botTasks(CommandSourceStack source, AIPlayerRuntime runtime, int limit) {
        return botTasks(source, runtime, limit, false);
    }

    private static int botTasks(CommandSourceStack source, AIPlayerRuntime runtime, int limit, boolean includeClosed) {
        List<BotMemoryRepository.BotTask> tasks = runtime.getBotTasks(limit, includeClosed);
        int total = runtime.countBotTasks(includeClosed);
        String scope = includeClosed ? "all" : "open";

        if (tasks.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Bot tasks: none (" + scope + "=" + total + ")"), false);
            return 1;
        }

        StringJoiner joiner = new StringJoiner(" | ");
        tasks.forEach(task -> joiner.add("#" + task.id() + " " + task.status() + " " + task.objective()));
        source.sendSuccess(() -> Component.literal("Bot tasks " + scope + "=" + total + " -> " + joiner), false);
        return 1;
    }


    private static int botTasksByStatus(CommandSourceStack source, AIPlayerRuntime runtime, String statusValue, int limit) {
        String status = statusValue.toUpperCase(Locale.ROOT);
        if (!BOT_TASK_STATUS_SUGGESTIONS.contains(status)) {
            source.sendFailure(Component.literal("Statut invalide: " + statusValue + " (PENDING|ACTIVE|DONE|CANCELED)"));
            return 0;
        }

        List<BotMemoryRepository.BotTask> tasks = runtime.getBotTasksByStatus(status, limit);
        int total = runtime.countBotTasksByStatus(status);
        if (tasks.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Bot tasks: none (status=" + status + " total=" + total + ")"), false);
            return 1;
        }

        StringJoiner joiner = new StringJoiner(" | ");
        tasks.forEach(task -> joiner.add("#" + task.id() + " " + task.status() + " " + task.objective()));
        source.sendSuccess(() -> Component.literal("Bot tasks status=" + status + " total=" + total + " -> " + joiner), false);
        return 1;
    }

    private static int botTasksPrune(CommandSourceStack source, AIPlayerRuntime runtime, int limit) {
        int deleted = runtime.pruneClosedBotTasks(limit);
        source.sendSuccess(() -> Component.literal("Bot tasks prune deleted=" + deleted + " limit=" + limit), false);
        return 1;
    }


    private static int botTaskDelete(CommandSourceStack source, AIPlayerRuntime runtime, int taskId) {
        boolean deleted = runtime.deleteBotTask(taskId);
        if (!deleted) {
            source.sendFailure(Component.literal("Bot task non supprimable: id=" + taskId + " (inconnue ou non fermee)"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Bot task DELETED id=" + taskId), false);
        return 1;
    }
    private static int botTaskInfo(CommandSourceStack source, AIPlayerRuntime runtime, int taskId) {
        Optional<BotMemoryRepository.BotTask> taskOptional = runtime.getBotTaskById(taskId);
        if (taskOptional.isEmpty()) {
            source.sendFailure(Component.literal("Bot task introuvable: id=" + taskId));
            return 0;
        }

        BotMemoryRepository.BotTask task = taskOptional.get();
        source.sendSuccess(
            () -> Component.literal(
                "Bot task #" + task.id()
                    + " status=" + task.status()
                    + " objective=" + task.objective()
                    + " requestedBy=" + task.requestedBy()
                    + " createdAt=" + task.createdAt()
                    + " updatedAt=" + task.updatedAt()
            ),
            false
        );
        return 1;
    }

    private static int botTaskUpdate(CommandSourceStack source, AIPlayerRuntime runtime, int taskId, String objective) {
        boolean updated = runtime.updateBotTaskObjective(taskId, objective);
        if (!updated) {
            source.sendFailure(Component.literal("Bot task introuvable: id=" + taskId));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Bot task UPDATED id=" + taskId + " objective=" + objective), false);
        return 1;
    }
    private static int botTaskDone(CommandSourceStack source, AIPlayerRuntime runtime, int taskId) {
        boolean updated = runtime.markBotTaskDone(taskId);
        if (!updated) {
            source.sendFailure(Component.literal("Bot task introuvable: id=" + taskId));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Bot task DONE id=" + taskId), false);
        return 1;
    }

    private static int botTaskReopen(CommandSourceStack source, AIPlayerRuntime runtime, int taskId) {
        boolean updated = runtime.reopenBotTask(taskId);
        if (!updated) {
            source.sendFailure(Component.literal("Bot task introuvable: id=" + taskId));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Bot task PENDING id=" + taskId), false);
        return 1;
    }

    private static int botTaskStart(CommandSourceStack source, AIPlayerRuntime runtime, int taskId) {
        boolean updated = runtime.startBotTask(taskId);
        if (!updated) {
            source.sendFailure(Component.literal("Bot task introuvable: id=" + taskId));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Bot task ACTIVE id=" + taskId), false);
        return 1;
    }
    private static int botTaskCancel(CommandSourceStack source, AIPlayerRuntime runtime, int taskId) {
        boolean updated = runtime.cancelBotTask(taskId);
        if (!updated) {
            source.sendFailure(Component.literal("Bot task introuvable: id=" + taskId));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Bot task CANCELED id=" + taskId), false);
        return 1;
    }

    private static int botAsk(CommandSourceStack source, AIPlayerRuntime runtime, String question) {
        String playerId = source.getTextName();
        AIPlayerRuntime.BotAskResult result = runtime.askBot(playerId, question);
        if (result.interactionId() <= 0) {
            source.sendFailure(Component.literal("Impossible d'enregistrer l'interaction bot"));
            return 0;
        }

        source.sendSuccess(
            () -> Component.literal("Bot ask id=" + result.interactionId() + " -> " + result.response()),
            false
        );
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





    private static int moduleAudit(CommandSourceStack source, AIPlayerRuntime runtime) {
        boolean envOverride = runtime.isModulesEnvOverrideActive();
        StringJoiner registered = new StringJoiner(", ");
        runtime.getRegisteredModules().forEach(registered::add);

        StringJoiner enabled = new StringJoiner(", ");
        runtime.getEnabledModules().forEach(enabled::add);

        String stored = runtime.getStoredEnabledModules()
            .map(values -> values.isEmpty() ? "[]" : values.toString())
            .orElse("<none>");

        source.sendSuccess(
            () -> Component.literal(
                "Module audit: envOverride=" + envOverride
                    + " registered=[" + registered + "]"
                    + " enabled=[" + enabled + "]"
                    + " sqlite=" + stored
            ),
            false
        );
        return 1;
    }


    private static int moduleReconcile(CommandSourceStack source, AIPlayerRuntime runtime) {
        String action = runtime.reconcileModulesWithStorage();
        if ("env-override".equals(action)) {
            source.sendFailure(Component.literal("Module reconcile bloque: override environnement AIPLAYER_MODULES actif"));
            return 0;
        }

        StringJoiner enabled = new StringJoiner(", ");
        runtime.getEnabledModules().forEach(enabled::add);
        source.sendSuccess(
            () -> Component.literal("Module reconcile action=" + action + " enabled=[" + enabled + "]"),
            false
        );
        return 1;
    }
    private static int moduleDrift(CommandSourceStack source, AIPlayerRuntime runtime) {
        List<String> enabled = runtime.getEnabledModules();
        Optional<List<String>> storedOptional = runtime.getStoredEnabledModules();
        if (storedOptional.isEmpty()) {
            source.sendFailure(Component.literal("Module drift: aucune config SQLite disponible."));
            return 0;
        }

        List<String> stored = storedOptional.get();
        String enabledText = enabled.isEmpty() ? "[]" : enabled.toString();
        String storedText = stored.isEmpty() ? "[]" : stored.toString();
        boolean drift = !enabled.equals(stored);

        source.sendSuccess(
            () -> Component.literal("Module drift=" + drift + " enabled=" + enabledText + " sqlite=" + storedText),
            false
        );
        return 1;
    }
    private static int moduleSource(CommandSourceStack source, AIPlayerRuntime runtime) {
        boolean envOverride = runtime.isModulesEnvOverrideActive();
        Optional<List<String>> stored = runtime.getStoredEnabledModules();
        String storedText = stored.map(values -> values.isEmpty() ? "[]" : values.toString()).orElse("<none>");

        source.sendSuccess(
            () -> Component.literal("Module source: envOverride=" + envOverride + " sqlite=" + storedText),
            false
        );
        return 1;
    }
    private static int moduleStatus(CommandSourceStack source, AIPlayerRuntime runtime, String moduleName) {
        if (!runtime.isModuleRegistered(moduleName)) {
            source.sendFailure(Component.literal("Unknown module: " + moduleName));
            return 0;
        }

        boolean enabled = runtime.isModuleEnabled(moduleName);
        source.sendSuccess(() -> Component.literal("Module status: " + moduleName + " enabled=" + enabled), false);
        return 1;
    }
    private static int clearModuleStorage(CommandSourceStack source, AIPlayerRuntime runtime) {
        boolean cleared = runtime.clearStoredEnabledModules();
        if (!cleared) {
            source.sendFailure(Component.literal("Aucune config modules en SQLite"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Configuration modules SQLite effacee"), false);
        return 1;
    }
    private static int saveModules(CommandSourceStack source, AIPlayerRuntime runtime) {
        runtime.saveModulesToStorage();
        StringJoiner enabled = new StringJoiner(", ");
        runtime.getEnabledModules().forEach(enabled::add);
        source.sendSuccess(() -> Component.literal("Modules saved to storage: enabled=[" + enabled + "]"), false);
        return 1;
    }
    private static int reloadModules(CommandSourceStack source, AIPlayerRuntime runtime) {
        boolean reloaded = runtime.reloadModulesFromStorage();
        if (!reloaded) {
            source.sendFailure(Component.literal("Impossible de recharger les modules (config absente ou override env actif)."));
            return 0;
        }

        StringJoiner enabled = new StringJoiner(", ");
        runtime.getEnabledModules().forEach(enabled::add);
        source.sendSuccess(() -> Component.literal("Modules reloaded from storage: enabled=[" + enabled + "]"), false);
        return 1;
    }
    private static int resetModules(CommandSourceStack source, AIPlayerRuntime runtime) {
        runtime.resetModules();
        StringJoiner enabled = new StringJoiner(", ");
        runtime.getEnabledModules().forEach(enabled::add);
        source.sendSuccess(() -> Component.literal("Modules reset: enabled=[" + enabled + "]"), false);
        return 1;
    }

    private static int disableAllModules(CommandSourceStack source, AIPlayerRuntime runtime) {
        int disabledCount = runtime.disableAllModules();
        source.sendSuccess(() -> Component.literal("Modules disabled: count=" + disabledCount), false);
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

    private static int colonyTownHall(CommandSourceStack source, AIPlayerRuntime runtime) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(source);

        if (!runtime.isMineColoniesAvailable()) {
            source.sendFailure(Component.literal("MineColonies n est pas charge"));
            return 0;
        }

        Optional<BlockPos> pos = runtime.getMineColoniesTownHall(player);
        if (pos.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Town Hall introuvable pour ce joueur"), false);
            return 1;
        }

        BlockPos townHall = pos.get();
        source.sendSuccess(() -> Component.literal("Town Hall pos=" + townHall.toShortString()), false);
        return 1;
    }

    private static int colonyEnsure(CommandSourceStack source, AIPlayerRuntime runtime, int count)
        throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(source);

        if (!runtime.isMineColoniesAvailable()) {
            source.sendFailure(Component.literal("MineColonies n est pas charge"));
            return 0;
        }

        MineColoniesBridge.BridgeResult result = runtime.ensureMineColoniesCitizens(player, count);
        return sendBridgeResult(source, result);
    }

    private static int colonyRequest(CommandSourceStack source, AIPlayerRuntime runtime, String itemId, int quantity)
        throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(source);

        MineColoniesBridge.BridgeResult result = runtime.requestMineColoniesDeposit(player, itemId, quantity);
        return sendBridgeResult(source, result);
    }
    private static int colonyMayor(CommandSourceStack source, AIPlayerRuntime runtime) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(source);

        MineColoniesBridge.BridgeResult result = runtime.enableMineColoniesMayorMode(player);
        return sendBridgeResult(source, result);
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




    private static int ae2QueueHistory(CommandSourceStack source, AIPlayerRuntime runtime, int limit) {
        List<BotMemoryRepository.AE2CraftRequest> requests = runtime.getAe2CraftRequestHistory(limit);
        if (requests.isEmpty()) {
            source.sendSuccess(() -> Component.literal("AE2 queue history: empty"), false);
            return 1;
        }

        StringJoiner joiner = new StringJoiner(" | ");
        requests.forEach(request -> joiner.add(
            "#" + request.id() + " " + request.status() + " " + request.itemId() + " x" + request.quantity()
        ));

        source.sendSuccess(
            () -> Component.literal("AE2 queue history -> " + joiner),
            false
        );
        return 1;
    }


    private static int ae2QueueHistoryByStatus(CommandSourceStack source, AIPlayerRuntime runtime, String statusValue, int limit) {
        String status = statusValue.toUpperCase(Locale.ROOT);
        if (!AE2_REQUEST_STATUS_SUGGESTIONS.contains(status)) {
            source.sendFailure(Component.literal("Statut AE2 invalide: " + statusValue + " (PENDING|DISPATCHED|FAILED|DONE|CANCELED)"));
            return 0;
        }

        List<BotMemoryRepository.AE2CraftRequest> requests = runtime.getAe2CraftRequestHistoryByStatus(status, limit);
        if (requests.isEmpty()) {
            source.sendSuccess(() -> Component.literal("AE2 queue history status=" + status + ": empty"), false);
            return 1;
        }

        StringJoiner joiner = new StringJoiner(" | ");
        requests.forEach(request -> joiner.add(
            "#" + request.id() + " " + request.status() + " " + request.itemId() + " x" + request.quantity()
        ));

        source.sendSuccess(
            () -> Component.literal("AE2 queue history status=" + status + " -> " + joiner),
            false
        );
        return 1;
    }
    private static int ae2QueueExport(CommandSourceStack source, AIPlayerRuntime runtime, int limit) {
        List<BotMemoryRepository.AE2CraftRequest> requests = runtime.getAe2CraftRequestHistory(limit);
        if (requests.isEmpty()) {
            source.sendSuccess(() -> Component.literal("AE2 queue export: empty"), false);
            return 1;
        }

        StringJoiner joiner = new StringJoiner(" | ");
        requests.forEach(request -> joiner.add(
            "#" + request.id()
                + ";" + request.status()
                + ";" + request.itemId()
                + ";x" + request.quantity()
                + ";by=" + request.requestedBy()
        ));

        source.sendSuccess(
            () -> Component.literal("AE2 queue export count=" + requests.size() + " -> " + joiner),
            false
        );
        return 1;
    }
    private static int ae2QueuePurge(CommandSourceStack source, AIPlayerRuntime runtime, int limit) {
        int purged = runtime.purgeClosedAe2CraftRequests(limit);
        int pending = runtime.countPendingAe2CraftRequests();
        source.sendSuccess(
            () -> Component.literal("AE2 queue purge: purged=" + purged + " pending=" + pending),
            false
        );
        return 1;
    }

    private static int ae2QueueStats(CommandSourceStack source, AIPlayerRuntime runtime) {
        int pending = runtime.countAe2CraftRequestsByStatus("PENDING");
        int dispatched = runtime.countAe2CraftRequestsByStatus("DISPATCHED");
        int failed = runtime.countAe2CraftRequestsByStatus("FAILED");
        int done = runtime.countAe2CraftRequestsByStatus("DONE");
        int canceled = runtime.countAe2CraftRequestsByStatus("CANCELED");
        int total = pending + dispatched + failed + done + canceled;

        source.sendSuccess(
            () -> Component.literal(
                "AE2 queue stats total=" + total
                    + " pending=" + pending
                    + " dispatched=" + dispatched
                    + " failed=" + failed
                    + " done=" + done
                    + " canceled=" + canceled
            ),
            false
        );
        return 1;
    }


    private static int ae2QueueDelete(CommandSourceStack source, AIPlayerRuntime runtime, int requestId) {
        boolean deleted = runtime.deleteAe2CraftRequest(requestId);
        if (!deleted) {
            source.sendFailure(Component.literal("AE2 queue delete impossible: id=" + requestId + " (introuvable ou encore PENDING)"));
            return 0;
        }

        int pending = runtime.countPendingAe2CraftRequests();
        source.sendSuccess(
            () -> Component.literal("AE2 queue delete: id=" + requestId + " pending=" + pending),
            false
        );
        return 1;
    }
    private static int ae2QueueCancel(CommandSourceStack source, AIPlayerRuntime runtime, int requestId, String reason) {
        boolean canceled = runtime.cancelAe2CraftRequest(requestId, reason);
        if (!canceled) {
            source.sendFailure(Component.literal("AE2 queue cancel impossible: id=" + requestId + " (introuvable, deja DONE ou deja CANCELED)"));
            return 0;
        }

        int pending = runtime.countPendingAe2CraftRequests();
        source.sendSuccess(
            () -> Component.literal("AE2 queue cancel: id=" + requestId + " pending=" + pending + " reason=" + reason),
            false
        );
        return 1;
    }
    private static int ae2QueueDone(CommandSourceStack source, AIPlayerRuntime runtime, int requestId, String message) {
        boolean done = runtime.doneAe2CraftRequest(requestId, message);
        if (!done) {
            source.sendFailure(Component.literal("AE2 queue done impossible: id=" + requestId + " (introuvable)"));
            return 0;
        }

        int pending = runtime.countPendingAe2CraftRequests();
        source.sendSuccess(
            () -> Component.literal("AE2 queue done: id=" + requestId + " pending=" + pending + " message=" + message),
            false
        );
        return 1;
    }

    private static int ae2QueueFail(CommandSourceStack source, AIPlayerRuntime runtime, int requestId, String reason) {
        boolean failed = runtime.failAe2CraftRequest(requestId, reason);
        if (!failed) {
            source.sendFailure(Component.literal("AE2 queue fail impossible: id=" + requestId + " (introuvable)"));
            return 0;
        }

        int pending = runtime.countPendingAe2CraftRequests();
        source.sendSuccess(
            () -> Component.literal("AE2 queue fail: id=" + requestId + " pending=" + pending + " reason=" + reason),
            false
        );
        return 1;
    }

    private static int ae2QueueReplayAll(CommandSourceStack source, AIPlayerRuntime runtime, int limit) {
        int replayed = runtime.replayAe2CraftRequests(limit);
        int pending = runtime.countPendingAe2CraftRequests();
        source.sendSuccess(
            () -> Component.literal("AE2 queue replay all: replayed=" + replayed + " pending=" + pending),
            false
        );
        return 1;
    }

    private static int ae2QueueReplay(CommandSourceStack source, AIPlayerRuntime runtime, String statusValue, int limit) {
        String status = statusValue.toUpperCase(Locale.ROOT);
        if (!AE2_REQUEST_STATUS_SUGGESTIONS.contains(status)) {
            source.sendFailure(Component.literal("Statut AE2 invalide: " + statusValue + " (PENDING|DISPATCHED|FAILED|DONE|CANCELED)"));
            return 0;
        }

        if ("PENDING".equals(status)) {
            source.sendFailure(Component.literal("AE2 queue replay refuse: status=PENDING"));
            return 0;
        }

        int replayed = runtime.replayAe2CraftRequestsByStatus(status, limit);
        int pending = runtime.countPendingAe2CraftRequests();
        source.sendSuccess(
            () -> Component.literal("AE2 queue replay status=" + status + " replayed=" + replayed + " pending=" + pending),
            false
        );
        return 1;
    }
    private static int ae2QueueReplayFailed(CommandSourceStack source, AIPlayerRuntime runtime, int limit) {
        int replayed = runtime.replayAe2CraftRequestsByStatus("FAILED", limit);
        int pending = runtime.countPendingAe2CraftRequests();
        source.sendSuccess(
            () -> Component.literal("AE2 queue replay failed: replayed=" + replayed + " pending=" + pending),
            false
        );
        return 1;
    }

    private static int ae2QueueReplayDispatched(CommandSourceStack source, AIPlayerRuntime runtime, int limit) {
        int replayed = runtime.replayAe2CraftRequestsByStatus("DISPATCHED", limit);
        int pending = runtime.countPendingAe2CraftRequests();
        source.sendSuccess(
            () -> Component.literal("AE2 queue replay dispatched: replayed=" + replayed + " pending=" + pending),
            false
        );
        return 1;
    }

    private static int ae2QueueReplayCanceled(CommandSourceStack source, AIPlayerRuntime runtime, int limit) {
        int replayed = runtime.replayAe2CraftRequestsByStatus("CANCELED", limit);
        int pending = runtime.countPendingAe2CraftRequests();
        source.sendSuccess(
            () -> Component.literal("AE2 queue replay canceled: replayed=" + replayed + " pending=" + pending),
            false
        );
        return 1;
    }

    private static int ae2QueueReplayDone(CommandSourceStack source, AIPlayerRuntime runtime, int limit) {
        int replayed = runtime.replayAe2CraftRequestsByStatus("DONE", limit);
        int pending = runtime.countPendingAe2CraftRequests();
        source.sendSuccess(
            () -> Component.literal("AE2 queue replay done: replayed=" + replayed + " pending=" + pending),
            false
        );
        return 1;
    }

    private static int ae2QueueRetry(CommandSourceStack source, AIPlayerRuntime runtime, int requestId) {
        boolean retried = runtime.retryAe2CraftRequest(requestId);
        if (!retried) {
            source.sendFailure(Component.literal("AE2 queue retry impossible: id=" + requestId + " (introuvable ou deja PENDING)"));
            return 0;
        }

        int pending = runtime.countPendingAe2CraftRequests();
        source.sendSuccess(
            () -> Component.literal("AE2 queue retry: id=" + requestId + " pending=" + pending),
            false
        );
        return 1;
    }

    private static int ae2QueueClear(CommandSourceStack source, AIPlayerRuntime runtime, int limit) {
        int cleared = runtime.clearNonPendingAe2CraftRequests(limit);
        int pending = runtime.countPendingAe2CraftRequests();
        source.sendSuccess(
            () -> Component.literal("AE2 queue clear: cleared=" + cleared + " pending=" + pending),
            false
        );
        return 1;
    }

    private static int ae2Dispatch(CommandSourceStack source, AIPlayerRuntime runtime, int limit) {
        if (!runtime.isAe2Available()) {
            source.sendFailure(Component.literal("AE2 n'est pas charge"));
            return 0;
        }

        int before = runtime.countPendingAe2CraftRequests();
        int dispatched = runtime.dispatchPendingAe2CraftRequests(limit);
        int after = runtime.countPendingAe2CraftRequests();

        source.sendSuccess(
            () -> Component.literal("AE2 dispatch: dispatched=" + dispatched + " pending=" + before + " -> " + after),
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































