package com.aiplayer.mod.commands;

import com.aiplayer.mod.core.AIPlayerRuntime;
import com.aiplayer.mod.integrations.MineColoniesBridge;
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
        );
    }

    private static int showStatus(CommandSourceStack source, AIPlayerRuntime runtime) {
        StringJoiner joiner = new StringJoiner(", ");
        runtime.getEnabledModules().forEach(joiner::add);

        source.sendSuccess(
            () -> Component.literal("phase=" + runtime.getPhase() + " | modules=" + joiner),
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