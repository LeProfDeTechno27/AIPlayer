package com.aiplayer.mod.core;

import com.aiplayer.mod.integrations.OllamaClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

public final class BotBrain {
    public enum BotState {
        IDLE,
        SURVIVAL,
        COLONY,
        INTERACT
    }

    public enum BotAction {
        IDLE,
        MOVE,
        MINE,
        CRAFT,
        BUILD,
        INTERACT,
        SLEEP
    }

    private final OllamaClient ollamaClient;
    private final Duration decisionInterval;
    private final Duration cacheTtl;
    private final Duration decisionWindow;
    private final int maxDecisionsPerWindow;
    private Instant lastDecisionAt;
    private BotDecision lastDecision;
    private BotState currentState = BotState.IDLE;
    private Instant decisionWindowStart;
    private int decisionsInWindow;
    private String lastQuestion;
    private String lastResponse;
    private Instant lastResponseAt;
    private int cacheHits;
    private int rateLimitSkips;
    private int totalDecisions;

    public BotBrain(OllamaClient ollamaClient, Duration decisionInterval) {
        this.ollamaClient = ollamaClient;
        this.decisionInterval = decisionInterval;
        this.cacheTtl = resolveCacheTtl();
        this.decisionWindow = resolveDecisionWindow();
        this.maxDecisionsPerWindow = resolveMaxDecisionsPerWindow();
    }

    public Optional<BotDecision> tick(BotDecisionContext context) {
        Instant now = Instant.now();
        if (!allowDecision(now)) {
            rateLimitSkips++;
            return Optional.empty();
        }

        if (lastDecisionAt != null && Duration.between(lastDecisionAt, now).compareTo(decisionInterval) < 0) {
            return Optional.empty();
        }

        String question = "Etat: phase=" + context.phase() + " objective=" + context.objective()
            + ". Prochaine action (move/mine/craft/build/interact/sleep) ?";
        String raw = null;
        if (lastResponseAt != null
            && Duration.between(lastResponseAt, now).compareTo(cacheTtl) < 0
            && question.equals(lastQuestion)) {
            raw = lastResponse;
            cacheHits++;
        }

        if (raw == null) {
            Optional<String> response = ollamaClient.ask(question, context.objective());
            raw = response.orElse(lastResponse != null ? lastResponse : "idle");
            lastQuestion = question;
            lastResponse = raw;
            lastResponseAt = now;
        }

        BotAction action = parseAction(raw);
        currentState = transitionState(action);

        BotDecision decision = new BotDecision(action, currentState, raw, now);
        registerDecision(now);
        lastDecisionAt = now;
        lastDecision = decision;
        return Optional.of(decision);
    }

    public Optional<BotDecision> getLastDecision() {
        return Optional.ofNullable(lastDecision);
    }

    private BotAction parseAction(String rawResponse) {
        String value = rawResponse.toLowerCase(Locale.ROOT);
        if (value.contains("mine")) {
            return BotAction.MINE;
        }
        if (value.contains("craft") || value.contains("forge") || value.contains("smelt")) {
            return BotAction.CRAFT;
        }
        if (value.contains("build") || value.contains("place")) {
            return BotAction.BUILD;
        }
        if (value.contains("sleep") || value.contains("bed")) {
            return BotAction.SLEEP;
        }
        if (value.contains("interact") || value.contains("talk") || value.contains("chat")) {
            return BotAction.INTERACT;
        }
        if (value.contains("move") || value.contains("walk") || value.contains("go")) {
            return BotAction.MOVE;
        }
        return BotAction.IDLE;
    }

    private BotState transitionState(BotAction action) {
        return switch (action) {
            case MINE, CRAFT, BUILD, SLEEP -> BotState.SURVIVAL;
            case INTERACT -> BotState.INTERACT;
            case MOVE -> BotState.IDLE;
            case IDLE -> currentState;
        };
    }

    private boolean allowDecision(Instant now) {
        if (decisionWindowStart == null) {
            return true;
        }
        if (Duration.between(decisionWindowStart, now).compareTo(decisionWindow) >= 0) {
            return true;
        }
        return decisionsInWindow < maxDecisionsPerWindow;
    }

    private void registerDecision(Instant now) {
        totalDecisions++;
        if (decisionWindowStart == null || Duration.between(decisionWindowStart, now).compareTo(decisionWindow) >= 0) {
            decisionWindowStart = now;
            decisionsInWindow = 1;
            return;
        }
        decisionsInWindow = Math.min(maxDecisionsPerWindow + 1, decisionsInWindow + 1);
    }

    public DecisionStats getDecisionStats() {
        return new DecisionStats(cacheHits, rateLimitSkips, totalDecisions);
    }

    public record DecisionStats(int cacheHits, int rateLimitSkips, int totalDecisions) {
    }

    private Duration resolveCacheTtl() {
        String env = System.getenv("AIPLAYER_DECISION_CACHE_SECONDS");
        long seconds = 120;
        if (env != null && !env.isBlank()) {
            try {
                seconds = Long.parseLong(env.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (seconds < 30) {
            seconds = 30;
        }
        if (seconds > 600) {
            seconds = 600;
        }
        return Duration.ofSeconds(seconds);
    }

    private Duration resolveDecisionWindow() {
        String env = System.getenv("AIPLAYER_DECISION_WINDOW_SECONDS");
        long seconds = 60;
        if (env != null && !env.isBlank()) {
            try {
                seconds = Long.parseLong(env.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (seconds < 30) {
            seconds = 30;
        }
        if (seconds > 300) {
            seconds = 300;
        }
        return Duration.ofSeconds(seconds);
    }

    private int resolveMaxDecisionsPerWindow() {
        String env = System.getenv("AIPLAYER_DECISION_MAX_PER_WINDOW");
        int max = 4;
        if (env != null && !env.isBlank()) {
            try {
                max = Integer.parseInt(env.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (max < 1) {
            max = 1;
        }
        if (max > 10) {
            max = 10;
        }
        return max;
    }

    public record BotDecision(BotAction action, BotState state, String rawResponse, Instant decidedAt) {
    }


    public record BotDecisionContext(String phase, String objective) {
    }
}

















