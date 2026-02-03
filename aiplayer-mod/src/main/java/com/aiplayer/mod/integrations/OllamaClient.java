package com.aiplayer.mod.integrations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OllamaClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(OllamaClient.class);
    private static final Pattern CONTENT_PATTERN = Pattern.compile("\\\"content\\\"\\s*:\\s*\\\"(.*?)\\\"", Pattern.DOTALL);

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String model;

    public OllamaClient(String baseUrl, String model) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
        this.baseUrl = baseUrl;
        this.model = model;
    }

    public Optional<String> ask(String question, String objective) {
        try {
            String payload = buildPayload(question, objective);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(this.baseUrl + "/api/chat"))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("Ollama returned non-success status={} body={}", response.statusCode(), response.body());
                return Optional.empty();
            }

            String content = extractContent(response.body());
            if (content == null || content.isBlank()) {
                LOGGER.warn("Ollama response did not contain message content");
                return Optional.empty();
            }

            return Optional.of(content.trim());
        } catch (Exception exception) {
            LOGGER.warn("Failed to query Ollama", exception);
            return Optional.empty();
        }
    }

    private String buildPayload(String question, String objective) {
        String system = escapeJson("Tu es un bot Minecraft utile et concis. Reponds en une phrase actionnable.");
        String user = escapeJson("Objectif courant: " + objective + " | Question: " + question);

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
        Matcher matcher = CONTENT_PATTERN.matcher(body);
        if (!matcher.find()) {
            return null;
        }

        String raw = matcher.group(1);
        return raw
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\");
    }

    private String escapeJson(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}