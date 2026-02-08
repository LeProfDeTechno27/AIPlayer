package com.aiplayer.mod.integrations;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OllamaClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(OllamaClient.class);
    private static final Pattern CONTENT_PATTERN = Pattern.compile("\\\"content\\\"\\s*:\\s*\\\"(.*?)\\\"", Pattern.DOTALL);

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String model;
    private Instant lastFailureAt;
    private int failureStreak;

    public OllamaClient(String baseUrl, String model) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
        this.baseUrl = baseUrl;
        this.model = model;
    }

    public Optional<String> ask(String question, String objective) {
        String system = "Tu es un bot Minecraft utile et concis. Reponds en une phrase actionnable.";
        String user = "Objectif courant: " + objective + " | Question: " + question;
        return askWithPrompts(system, user, Duration.ofSeconds(8));
    }

    public Optional<String> askStructured(String systemPrompt, String userPrompt) {
        return askWithPrompts(systemPrompt, userPrompt, Duration.ofSeconds(12));
    }

    private Optional<String> askWithPrompts(String systemPrompt, String userPrompt, Duration timeout) {
        if (shouldBackoff()) {
            LOGGER.warn("Ollama backoff active, skip request");
            return Optional.empty();
        }

        List<String> endpoints = endpointCandidates();
        String payload = buildPayload(systemPrompt, userPrompt);
        String lastError = "unknown";

        for (String endpoint : endpoints) {
            Optional<String> response = requestEndpoint(endpoint, payload, timeout);
            if (response.isPresent()) {
                resetFailures();
                return response;
            }
            lastError = "endpoint=" + endpoint;
        }

        LOGGER.warn("All Ollama endpoints failed ({})", lastError);
        registerFailure();
        return Optional.empty();
    }

    private Optional<String> requestEndpoint(String endpoint, String payload, Duration timeout) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/api/chat"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("Ollama non-success endpoint={} status={} body={}", endpoint, response.statusCode(), response.body());
                return Optional.empty();
            }

            String content = extractContent(response.body());
            if (content == null || content.isBlank()) {
                LOGGER.warn("Ollama endpoint={} did not contain message content", endpoint);
                return Optional.empty();
            }

            return Optional.of(content.trim());
        } catch (Exception exception) {
            LOGGER.warn("Failed to query Ollama endpoint={}", endpoint, exception);
            return Optional.empty();
        }
    }

    private String buildPayload(String systemPrompt, String userPrompt) {
        String system = escapeJson(systemPrompt);
        String user = escapeJson(userPrompt);

        return "{" +
            "\"model\":\"" + escapeJson(this.model) + "\"," +
            "\"stream\":false," +
            "\"messages\":[" +
            "{\"role\":\"system\",\"content\":\"" + system + "\"}," +
            "{\"role\":\"user\",\"content\":\"" + user + "\"}" +
            "]" +
            "}";
    }

    private String extractContent(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("message") && root.get("message").isJsonObject()) {
                JsonObject message = root.getAsJsonObject("message");
                if (message.has("content") && message.get("content").isJsonPrimitive()) {
                    return message.get("content").getAsString();
                }
            }
            if (root.has("response") && root.get("response").isJsonPrimitive()) {
                return root.get("response").getAsString();
            }
        } catch (Exception ignored) {
            // Keep regex fallback for non-standard payloads.
        }

        Matcher matcher = CONTENT_PATTERN.matcher(body);
        if (!matcher.find()) {
            return null;
        }

        String raw = matcher.group(1);
        return raw
            .replace("\\\\n", "\n")
            .replace("\\\\r", "\r")
            .replace("\\\\t", "\t")
            .replace("\\\\\"", "\"")
            .replace("\\\\\\\\", "\\");
    }

    private List<String> endpointCandidates() {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        ordered.add(normalizeBaseUrl(this.baseUrl));
        ordered.add("http://ollama:11434");
        ordered.add("http://aiplayer-ollama:11434");
        ordered.add("http://localhost:11434");
        return new ArrayList<>(ordered);
    }

    private String normalizeBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return "http://ollama:11434";
        }
        String trimmed = raw.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String escapeJson(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private boolean shouldBackoff() {
        if (failureStreak == 0 || lastFailureAt == null) {
            return false;
        }
        Duration backoff = computeBackoff();
        return Duration.between(lastFailureAt, Instant.now()).compareTo(backoff) < 0;
    }

    private Duration computeBackoff() {
        int capped = Math.min(5, failureStreak);
        long seconds = (long) Math.pow(2, capped) * 2L;
        long bounded = Math.min(30L, seconds);
        return Duration.ofSeconds(bounded);
    }

    private void registerFailure() {
        failureStreak = Math.min(6, failureStreak + 1);
        lastFailureAt = Instant.now();
    }

    private void resetFailures() {
        failureStreak = 0;
        lastFailureAt = null;
    }
}
