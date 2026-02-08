package com.aiplayer.mod.core.ai;

import com.aiplayer.mod.integrations.OllamaClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class BotPlanner {
    private static final Logger LOGGER = LoggerFactory.getLogger(BotPlanner.class);

    private final OllamaClient ollamaClient;

    public BotPlanner(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    public BotActionPlan plan(BotPerception perception, BotGoal goal, int maxSteps) {
        return plan(perception, goal, "", maxSteps);
    }

    public BotActionPlan plan(BotPerception perception, BotGoal goal, String memorySummary, int maxSteps) {
        String systemPrompt = "You are a Minecraft pro player AI. Respond ONLY with JSON matching the schema.";
        String userPrompt = "Perception: " + perception.summary()
            + "\nGoal: " + goal.name() + " - " + goal.description()
            + (memorySummary == null || memorySummary.isBlank() ? "" : "\nMemory: " + memorySummary)
            + "\nSchema: {\"goal\":string,\"rationale\":string,\"steps\":[{\"type\":\"MOVE|MINE|PLACE|CRAFT|INTERACT|ATTACK|EQUIP|WAIT\",\"target\":{\"x\":0,\"y\":0,\"z\":0},\"item\":string,\"count\":1,\"timeout\":200}]}";

        Optional<String> response = ollamaClient.askStructured(systemPrompt, userPrompt);
        if (response.isEmpty()) {
            return fallbackPlan(goal, "ollama unavailable");
        }

        BotActionPlan parsed = parsePlan(response.get(), goal, maxSteps);
        if (parsed == null || parsed.isEmpty()) {
            return fallbackPlan(goal, "invalid plan");
        }

        return parsed;
    }

    private BotActionPlan parsePlan(String raw, BotGoal goal, int maxSteps) {
        String json = extractJson(raw);
        if (json == null) {
            LOGGER.warn("Unable to extract JSON plan from response: {}", raw);
            return null;
        }

        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String planGoal = getString(root, "goal", goal.name());
            String rationale = getString(root, "rationale", "");
            JsonArray stepsArray = root.has("steps") && root.get("steps").isJsonArray() ? root.getAsJsonArray("steps") : new JsonArray();

            List<BotActionStep> steps = new ArrayList<>();
            for (JsonElement element : stepsArray) {
                if (!element.isJsonObject()) {
                    continue;
                }
                BotActionStep step = parseStep(element.getAsJsonObject());
                if (step != null) {
                    steps.add(step);
                }
                if (steps.size() >= maxSteps) {
                    break;
                }
            }

            return new BotActionPlan(planGoal, rationale, steps);
        } catch (Exception exception) {
            LOGGER.warn("Failed to parse plan JSON", exception);
            return null;
        }
    }

    private BotActionStep parseStep(JsonObject object) {
        String typeRaw = getString(object, "type", "WAIT");
        BotActionType type = parseType(typeRaw);
        String itemId = getString(object, "item", "");
        int count = getInt(object, "count", 1);
        int timeout = getInt(object, "timeout", 200);

        if (object.has("target") && object.get("target").isJsonObject()) {
            JsonObject target = object.getAsJsonObject("target");
            int x = getInt(target, "x", 0);
            int y = getInt(target, "y", 0);
            int z = getInt(target, "z", 0);
            return new BotActionStep(type, new net.minecraft.core.BlockPos(x, y, z), itemId, count, timeout);
        }

        return new BotActionStep(type, null, itemId, count, timeout);
    }

    private BotActionType parseType(String raw) {
        try {
            return BotActionType.valueOf(raw.trim().toUpperCase());
        } catch (Exception ignored) {
            return BotActionType.WAIT;
        }
    }

    private String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return raw.substring(start, end + 1);
    }

    private String getString(JsonObject object, String key, String fallback) {
        if (object.has(key) && object.get(key).isJsonPrimitive()) {
            return object.get(key).getAsString();
        }
        return fallback;
    }

    private int getInt(JsonObject object, String key, int fallback) {
        if (object.has(key) && object.get(key).isJsonPrimitive()) {
            try {
                return object.get(key).getAsInt();
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private BotActionPlan fallbackPlan(BotGoal goal, String reason) {
        return new BotActionPlan(goal.name(), "fallback:" + reason, List.of());
    }
}
