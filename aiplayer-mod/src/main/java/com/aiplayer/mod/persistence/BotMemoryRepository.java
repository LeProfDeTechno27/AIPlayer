package com.aiplayer.mod.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class BotMemoryRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(BotMemoryRepository.class);

    private final Path databasePath;
    private final Duration actionFlushInterval;
    private final int actionBatchSize;
    private final int actionBufferWarnThreshold;
    private final List<ActionRecord> actionBuffer = new ArrayList<>();
    private Instant lastActionFlushAt;

    public BotMemoryRepository(Path databasePath) {
        this.databasePath = databasePath;
        this.actionFlushInterval = resolveActionFlushInterval();
        this.actionBatchSize = resolveActionBatchSize();
        this.actionBufferWarnThreshold = resolveActionBufferWarnThreshold();
    }

    public void initializeSchema() {
        try {
            ensureParentDirectory();
            createSchema();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to initialize SQLite schema", exception);
        }
    }

    public Optional<String> loadCurrentPhase() {
        String sql = "SELECT phase FROM bot_state WHERE id = 1";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            if (resultSet.next()) {
                return Optional.ofNullable(resultSet.getString("phase"));
            }
        } catch (SQLException exception) {
            LOGGER.warn("Failed to load bot phase", exception);
        }

        return Optional.empty();
    }

    public Optional<Integer> loadBotXp() {
        String sql = "SELECT config_value FROM bot_config WHERE config_key = ''bot_xp''";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            if (resultSet.next()) {
                String raw = resultSet.getString("config_value");
                if (raw == null || raw.isBlank()) {
                    return Optional.empty();
                }

                try {
                    return Optional.of(Integer.parseInt(raw.trim()));
                } catch (NumberFormatException exception) {
                    LOGGER.warn("Invalid bot_xp value {}", raw, exception);
                }
            }
        } catch (SQLException exception) {
            LOGGER.warn("Failed to load bot xp", exception);
        }

        return Optional.empty();
    }

    public void saveBotXp(int xp) {
        String sql = """
            INSERT INTO bot_config (config_key, config_value, updated_at)
            VALUES ('bot_xp', ?, ?)
            ON CONFLICT(config_key) DO UPDATE SET
              config_value = excluded.config_value,
              updated_at = excluded.updated_at
            """;

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, Integer.toString(xp));
            statement.setString(2, Instant.now().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            LOGGER.warn("Failed to persist bot xp", exception);
        }
    }
    public Optional<List<String>> loadEnabledModules() {
        String sql = "SELECT config_value FROM bot_config WHERE config_key = 'enabled_modules'";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                String raw = resultSet.getString("config_value");
                if (raw == null || raw.isBlank()) {
                    return Optional.of(List.of());
                }

                List<String> modules = Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .toList();

                return Optional.of(modules);
            }
        } catch (SQLException exception) {
            LOGGER.warn("Failed to load enabled modules", exception);
        }

        return Optional.empty();
    }


    public boolean clearEnabledModules() {
        String sql = "DELETE FROM bot_config WHERE config_key = 'enabled_modules'";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            LOGGER.warn("Failed to clear enabled modules", exception);
        }

        return false;
    }
    public void saveEnabledModules(List<String> moduleNames) {
        String sql = """
            INSERT INTO bot_config (config_key, config_value, updated_at)
            VALUES ('enabled_modules', ?, ?)
            ON CONFLICT(config_key) DO UPDATE SET
              config_value = excluded.config_value,
              updated_at = excluded.updated_at
            """;

        String payload = String.join(",", moduleNames);
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, payload);
            statement.setString(2, Instant.now().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            LOGGER.warn("Failed to persist enabled modules", exception);
        }
    }
    public void saveCurrentPhase(String phase) {
        String sql = """
            INSERT INTO bot_state (id, phase, updated_at)
            VALUES (1, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
              phase = excluded.phase,
              updated_at = excluded.updated_at
            """;

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, phase);
            statement.setString(2, Instant.now().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            LOGGER.warn("Failed to persist bot phase", exception);
        }
    }

    public void recordAction(String action, String result) {
        Instant now = Instant.now();
        ActionRecord record = new ActionRecord(action, result, now.toString());
        synchronized (actionBuffer) {
            actionBuffer.add(record);
            if (shouldFlushActions(now)) {
                flushActionBuffer(now, false);
            }
        }
    }

    public void flushActions() {
        Instant now = Instant.now();
        synchronized (actionBuffer) {
            flushActionBuffer(now, true);
        }
    }

    public int getActionBufferSize() {
        synchronized (actionBuffer) {
            return actionBuffer.size();
        }
    }

    public int getActionBufferWarnThreshold() {
        return actionBufferWarnThreshold;
    }

    public boolean isActionBufferHot() {
        synchronized (actionBuffer) {
            return actionBuffer.size() >= actionBufferWarnThreshold;
        }
    }

    public long enqueueBotTask(String objective, String requestedBy) {
        String sql = "INSERT INTO bot_tasks(objective, status, requested_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?)";
        String now = Instant.now().toString();

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, objective);
            statement.setString(2, "PENDING");
            statement.setString(3, requestedBy);
            statement.setString(4, now);
            statement.setString(5, now);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        } catch (SQLException exception) {
            LOGGER.warn("Failed to enqueue bot task objective={}", objective, exception);
        }

        return -1L;
    }

    public List<BotTask> loadOpenBotTasks(int limit) {
        int safeLimit = Math.max(1, Math.min(100, limit));
        String sql = """
            SELECT id, objective, status, requested_by, created_at, updated_at
            FROM bot_tasks
            WHERE status IN ('PENDING','ACTIVE')
            ORDER BY id ASC
            LIMIT ?
            """;

        List<BotTask> tasks = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, safeLimit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    tasks.add(new BotTask(
                        resultSet.getLong("id"),
                        resultSet.getString("objective"),
                        resultSet.getString("status"),
                        resultSet.getString("requested_by"),
                        resultSet.getString("created_at"),
                        resultSet.getString("updated_at")
                    ));
                }
            }
        } catch (SQLException exception) {
            LOGGER.warn("Failed to load open bot tasks", exception);
        }

        return tasks;
    }

    
    public List<BotTask> loadBotTasks(int limit) {
        int safeLimit = Math.max(1, Math.min(100, limit));
        String sql = """
            SELECT id, objective, status, requested_by, created_at, updated_at
            FROM bot_tasks
            ORDER BY id DESC
            LIMIT ?
            """;

        List<BotTask> tasks = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, safeLimit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    tasks.add(new BotTask(
                        resultSet.getLong("id"),
                        resultSet.getString("objective"),
                        resultSet.getString("status"),
                        resultSet.getString("requested_by"),
                        resultSet.getString("created_at"),
                        resultSet.getString("updated_at")
                    ));
                }
            }
        } catch (SQLException exception) {
            LOGGER.warn("Failed to load bot tasks", exception);
        }

        return tasks;
    }

    public List<BotTask> loadBotTasksByStatus(String status, int limit) {
        int safeLimit = Math.max(1, Math.min(100, limit));
        String sql = """
            SELECT id, objective, status, requested_by, created_at, updated_at
            FROM bot_tasks
            WHERE status = ?
            ORDER BY id DESC
            LIMIT ?
            """;

        List<BotTask> tasks = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setInt(2, safeLimit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    tasks.add(new BotTask(
                        resultSet.getLong("id"),
                        resultSet.getString("objective"),
                        resultSet.getString("status"),
                        resultSet.getString("requested_by"),
                        resultSet.getString("created_at"),
                        resultSet.getString("updated_at")
                    ));
                }
            }
        } catch (SQLException exception) {
            LOGGER.warn("Failed to load bot tasks by status={}", status, exception);
        }

        return tasks;
    }

    public Optional<BotTask> loadBotTaskById(long taskId) {
        String sql = """
            SELECT id, objective, status, requested_by, created_at, updated_at
            FROM bot_tasks
            WHERE id = ?
            LIMIT 1
            """;

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, taskId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new BotTask(
                        resultSet.getLong("id"),
                        resultSet.getString("objective"),
                        resultSet.getString("status"),
                        resultSet.getString("requested_by"),
                        resultSet.getString("created_at"),
                        resultSet.getString("updated_at")
                    ));
                }
            }
        } catch (SQLException exception) {
            LOGGER.warn("Failed to load bot task by id={}", taskId, exception);
        }

        return Optional.empty();
    }
    public Optional<BotTask> loadCurrentBotTask() {
        String sql = """
            SELECT id, objective, status, requested_by, created_at, updated_at
            FROM bot_tasks
            WHERE status IN ('PENDING','ACTIVE')
            ORDER BY CASE status WHEN 'ACTIVE' THEN 0 ELSE 1 END, id ASC
            LIMIT 1
            """;

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return Optional.of(new BotTask(
                    resultSet.getLong("id"),
                    resultSet.getString("objective"),
                    resultSet.getString("status"),
                    resultSet.getString("requested_by"),
                    resultSet.getString("created_at"),
                    resultSet.getString("updated_at")
                ));
            }
        } catch (SQLException exception) {
            LOGGER.warn("Failed to load current bot task", exception);
        }

        return Optional.empty();
    }

    public int countOpenBotTasks() {
        String sql = "SELECT COUNT(*) AS count FROM bot_tasks WHERE status IN ('PENDING','ACTIVE')";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getInt("count");
            }
        } catch (SQLException exception) {
            LOGGER.warn("Failed to count open bot tasks", exception);
        }

        return 0;
    }

    
    public int countBotTasks() {
        String sql = "SELECT COUNT(*) AS count FROM bot_tasks";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getInt("count");
            }
        } catch (SQLException exception) {
            LOGGER.warn("Failed to count bot tasks", exception);
        }

        return 0;
    }

    public int countBotTasksByStatus(String status) {
        String sql = "SELECT COUNT(*) AS count FROM bot_tasks WHERE status = ?";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("count");
                }
            }
        } catch (SQLException exception) {
            LOGGER.warn("Failed to count bot tasks by status={}", status, exception);
        }

        return 0;
    }


    public boolean deleteBotTask(long taskId) {
        String sql = "DELETE FROM bot_tasks WHERE id = ? AND status IN ('DONE','CANCELED')";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, taskId);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            LOGGER.warn("Failed to delete bot task id={}", taskId, exception);
        }

        return false;
    }
    public int deleteClosedBotTasks(int limit) {
        int safeLimit = Math.max(1, Math.min(1000, limit));
        String sql = """
            DELETE FROM bot_tasks
            WHERE id IN (
                SELECT id
                FROM bot_tasks
                WHERE status IN ('DONE','CANCELED')
                ORDER BY id ASC
                LIMIT ?
            )
            """;

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, safeLimit);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            LOGGER.warn("Failed to delete closed bot tasks limit={}", safeLimit, exception);
        }

        return 0;
    }

    public boolean updateBotTaskObjective(long taskId, String objective) {
        String sql = """
            UPDATE bot_tasks
            SET objective = ?, updated_at = ?
            WHERE id = ?
            """;

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, objective);
            statement.setString(2, Instant.now().toString());
            statement.setLong(3, taskId);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            LOGGER.warn("Failed to update bot task objective id={}", taskId, exception);
        }

        return false;
    }
    public boolean updateBotTaskStatus(long taskId, String status) {
        String sql = """
            UPDATE bot_tasks
            SET status = ?, updated_at = ?
            WHERE id = ?
            """;

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setString(2, Instant.now().toString());
            statement.setLong(3, taskId);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            LOGGER.warn("Failed to update bot task id={} status={}", taskId, status, exception);
        }

        return false;
    }
    public long recordInteraction(String playerId, String question, String response) {
        String sql = "INSERT INTO interactions(player_id, question, response, created_at) VALUES (?, ?, ?, ?)";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, playerId);
            statement.setString(2, question);
            statement.setString(3, response);
            statement.setString(4, Instant.now().toString());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        } catch (SQLException exception) {
            LOGGER.warn("Failed to record interaction player={}", playerId, exception);
        }

        return -1L;
    }

    public List<InteractionRecord> loadRecentInteractions(int limit) {
        int safeLimit = Math.max(1, Math.min(50, limit));
        String sql = """
            SELECT id, player_id, question, response, created_at
            FROM interactions
            ORDER BY id DESC
            LIMIT ?
            """;

        List<InteractionRecord> records = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, safeLimit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(new InteractionRecord(
                        resultSet.getLong("id"),
                        resultSet.getString("player_id"),
                        resultSet.getString("question"),
                        resultSet.getString("response"),
                        resultSet.getString("created_at")
                    ));
                }
            }
        } catch (SQLException exception) {
            LOGGER.warn("Failed to load recent interactions", exception);
        }

        return records;
    }

    public long enqueueAe2CraftRequest(String itemId, int quantity, String requestedBy) {
        String sql = "INSERT INTO ae2_craft_requests(item_id, quantity, status, requested_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)";
        String now = Instant.now().toString();

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, itemId);
            statement.setInt(2, quantity);
            statement.setString(3, "PENDING");
            statement.setString(4, requestedBy);
            statement.setString(5, now);
            statement.setString(6, now);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        } catch (SQLException exception) {
            LOGGER.warn("Failed to enqueue AE2 craft request item={} qty={}", itemId, quantity, exception);
        }

        return -1L;
    }

    public List<AE2CraftRequest> loadPendingAe2CraftRequests(int limit) {
        int safeLimit = Math.max(1, Math.min(100, limit));
        String sql = """
            SELECT id, item_id, quantity, status, requested_by, created_at, updated_at, result_message
            FROM ae2_craft_requests
            WHERE status = 'PENDING'
            ORDER BY id ASC
            LIMIT ?
            """;

        List<AE2CraftRequest> requests = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, safeLimit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    requests.add(new AE2CraftRequest(
                        resultSet.getLong("id"),
                        resultSet.getString("item_id"),
                        resultSet.getInt("quantity"),
                        resultSet.getString("status"),
                        resultSet.getString("requested_by"),
                        resultSet.getString("created_at"),
                        resultSet.getString("updated_at"),
                        resultSet.getString("result_message")
                    ));
                }
            }
        } catch (SQLException exception) {
            LOGGER.warn("Failed to load pending AE2 craft requests", exception);
        }

        return requests;
    }

    public List<AE2CraftRequest> loadAe2CraftRequestHistory(int limit) {
        int safeLimit = Math.max(1, Math.min(200, limit));
        String sql = """
            SELECT id, item_id, quantity, status, requested_by, created_at, updated_at, result_message
            FROM ae2_craft_requests
            ORDER BY id DESC
            LIMIT ?
            """;

        List<AE2CraftRequest> requests = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, safeLimit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    requests.add(new AE2CraftRequest(
                        resultSet.getLong("id"),
                        resultSet.getString("item_id"),
                        resultSet.getInt("quantity"),
                        resultSet.getString("status"),
                        resultSet.getString("requested_by"),
                        resultSet.getString("created_at"),
                        resultSet.getString("updated_at"),
                        resultSet.getString("result_message")
                    ));
                }
            }
        } catch (SQLException exception) {
            LOGGER.warn("Failed to load AE2 craft request history", exception);
        }

        return requests;
    }

    public List<AE2CraftRequest> loadAe2CraftRequestHistoryByStatus(String status, int limit) {
        int safeLimit = Math.max(1, Math.min(200, limit));
        String sql = """
            SELECT id, item_id, quantity, status, requested_by, created_at, updated_at, result_message
            FROM ae2_craft_requests
            WHERE UPPER(status) = ?
            ORDER BY id DESC
            LIMIT ?
            """;

        List<AE2CraftRequest> requests = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.toUpperCase());
            statement.setInt(2, safeLimit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    requests.add(new AE2CraftRequest(
                        resultSet.getLong("id"),
                        resultSet.getString("item_id"),
                        resultSet.getInt("quantity"),
                        resultSet.getString("status"),
                        resultSet.getString("requested_by"),
                        resultSet.getString("created_at"),
                        resultSet.getString("updated_at"),
                        resultSet.getString("result_message")
                    ));
                }
            }
        } catch (SQLException exception) {
            LOGGER.warn("Failed to load AE2 craft request history by status={}", status, exception);
        }

        return requests;
    }

    public int countAe2CraftRequestsByStatus(String status) {
        String sql = "SELECT COUNT(*) AS count FROM ae2_craft_requests WHERE UPPER(status) = ?";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.toUpperCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("count");
                }
            }
        } catch (SQLException exception) {
            LOGGER.warn("Failed to count AE2 craft requests by status={}", status, exception);
        }

        return 0;
    }

    public int countPendingAe2CraftRequests() {
        String sql = "SELECT COUNT(*) AS count FROM ae2_craft_requests WHERE status = 'PENDING'";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getInt("count");
            }
        } catch (SQLException exception) {
            LOGGER.warn("Failed to count pending AE2 craft requests", exception);
        }

        return 0;
    }

    public int purgeClosedAe2CraftRequests(int limit) {
        int safeLimit = Math.max(1, Math.min(1000, limit));
        String sql = """
            DELETE FROM ae2_craft_requests
            WHERE id IN (
                SELECT id
                FROM ae2_craft_requests
                WHERE status IN ('DONE','FAILED','CANCELED')
                ORDER BY id ASC
                LIMIT ?
            )
            """;

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, safeLimit);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            LOGGER.warn("Failed to purge closed AE2 craft requests limit={}", safeLimit, exception);
        }

        return 0;
    }

    public int clearNonPendingAe2CraftRequests(int limit) {
        int safeLimit = Math.max(1, Math.min(500, limit));
        String sql = """
            DELETE FROM ae2_craft_requests
            WHERE id IN (
                SELECT id
                FROM ae2_craft_requests
                WHERE status <> 'PENDING'
                ORDER BY id ASC
                LIMIT ?
            )
            """;

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, safeLimit);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            LOGGER.warn("Failed to clear non-pending AE2 craft requests limit={}", safeLimit, exception);
        }

        return 0;
    }

    public boolean deleteAe2CraftRequest(long requestId) {
        String sql = "DELETE FROM ae2_craft_requests WHERE id = ? AND status <> 'PENDING'";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            LOGGER.warn("Failed to delete AE2 craft request id={}", requestId, exception);
        }

        return false;
    }
    public int replayAe2CraftRequestsByStatus(String status, int limit) {
        int safeLimit = Math.max(1, Math.min(200, limit));
        String safeStatus = status.toUpperCase();
        String sql = """
            UPDATE ae2_craft_requests
            SET status = 'PENDING', result_message = ?, updated_at = ?
            WHERE id IN (
                SELECT id
                FROM ae2_craft_requests
                WHERE UPPER(status) = ? AND status <> 'PENDING'
                ORDER BY id ASC
                LIMIT ?
            )
            """;

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "Replay requested from " + safeStatus);
            statement.setString(2, Instant.now().toString());
            statement.setString(3, safeStatus);
            statement.setInt(4, safeLimit);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            LOGGER.warn("Failed to replay AE2 craft requests status={} limit={}", safeStatus, safeLimit, exception);
        }

        return 0;
    }
    public boolean retryAe2CraftRequest(long requestId) {
        String sql = """
            UPDATE ae2_craft_requests
            SET status = 'PENDING', result_message = ?, updated_at = ?
            WHERE id = ? AND status <> 'PENDING'
            """;

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "Retry requested");
            statement.setString(2, Instant.now().toString());
            statement.setLong(3, requestId);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            LOGGER.warn("Failed to retry AE2 craft request id={}", requestId, exception);
        }

        return false;
    }
    public boolean cancelAe2CraftRequest(long requestId, String reason) {
        String sql = """
            UPDATE ae2_craft_requests
            SET status = 'CANCELED', result_message = ?, updated_at = ?
            WHERE id = ? AND status IN ('PENDING', 'DISPATCHED', 'FAILED')
            """;

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reason);
            statement.setString(2, Instant.now().toString());
            statement.setLong(3, requestId);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            LOGGER.warn("Failed to cancel AE2 craft request id={}", requestId, exception);
        }

        return false;
    }
    public boolean updateAe2CraftRequestStatus(long requestId, String status, String resultMessage) {
        String sql = """
            UPDATE ae2_craft_requests
            SET status = ?, result_message = ?, updated_at = ?
            WHERE id = ?
            """;

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setString(2, resultMessage);
            statement.setString(3, Instant.now().toString());
            statement.setLong(4, requestId);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            LOGGER.warn("Failed to update AE2 craft request id={}", requestId, exception);
        }

        return false;
    }

    private void createSchema() throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {

            statement.execute(
                "CREATE TABLE IF NOT EXISTS bot_state (" +
                    "id INTEGER PRIMARY KEY CHECK (id = 1)," +
                    "phase TEXT NOT NULL," +
                    "updated_at TEXT NOT NULL" +
                    ")"
            );

            statement.execute(
                "CREATE TABLE IF NOT EXISTS bot_config (" +
                    "config_key TEXT PRIMARY KEY," +
                    "config_value TEXT NOT NULL," +
                    "updated_at TEXT NOT NULL" +
                    ")"
            );
            statement.execute(
                "CREATE TABLE IF NOT EXISTS bot_actions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "action TEXT NOT NULL," +
                    "result TEXT NOT NULL," +
                    "created_at TEXT NOT NULL" +
                    ")"
            );

            statement.execute(
                "CREATE TABLE IF NOT EXISTS bot_tasks (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "objective TEXT NOT NULL," +
                    "status TEXT NOT NULL," +
                    "requested_by TEXT NOT NULL," +
                    "created_at TEXT NOT NULL," +
                    "updated_at TEXT NOT NULL" +
                    ")"
            );

            statement.execute(
                "CREATE TABLE IF NOT EXISTS interactions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_id TEXT NOT NULL," +
                    "question TEXT NOT NULL," +
                    "response TEXT NOT NULL," +
                    "created_at TEXT NOT NULL" +
                    ")"
            );
            statement.execute(
                "CREATE TABLE IF NOT EXISTS ae2_craft_requests (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "item_id TEXT NOT NULL," +
                    "quantity INTEGER NOT NULL CHECK (quantity > 0)," +
                    "status TEXT NOT NULL," +
                    "requested_by TEXT NOT NULL," +
                    "created_at TEXT NOT NULL," +
                    "updated_at TEXT NOT NULL," +
                    "result_message TEXT" +
                    ")"
            );
        }
    }

    private boolean shouldFlushActions(Instant now) {
        if (actionBuffer.size() >= actionBatchSize) {
            return true;
        }
        if (lastActionFlushAt == null) {
            return true;
        }
        return Duration.between(lastActionFlushAt, now).compareTo(actionFlushInterval) >= 0;
    }

    private void flushActionBuffer(Instant now, boolean force) {
        if (actionBuffer.isEmpty()) {
            lastActionFlushAt = now;
            return;
        }
        if (!force && !shouldFlushActions(now)) {
            return;
        }
        String sql = "INSERT INTO bot_actions(action, result, created_at) VALUES (?, ?, ?)";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (ActionRecord record : actionBuffer) {
                statement.setString(1, record.action());
                statement.setString(2, record.result());
                statement.setString(3, record.createdAt());
                statement.addBatch();
            }
            statement.executeBatch();
            actionBuffer.clear();
            lastActionFlushAt = now;
        } catch (SQLException exception) {
            int bufferSize = actionBuffer.size();
            LOGGER.warn("Failed to flush {} bot actions", bufferSize, exception);
            if (bufferSize > actionBufferWarnThreshold) {
                actionBuffer.clear();
                lastActionFlushAt = now;
                LOGGER.warn("Action buffer cleared to avoid memory pressure (size={})", bufferSize);
            }
        }
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + this.databasePath.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA temp_store=MEMORY");
            statement.execute("PRAGMA busy_timeout=3000");
        } catch (SQLException exception) {
            LOGGER.warn("Failed to apply SQLite pragmas", exception);
        }
        return connection;
    }

    private void ensureParentDirectory() {
        Path parent = this.databasePath.toAbsolutePath().getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to create database directory", exception);
            }
        }
    }

    private Duration resolveActionFlushInterval() {
        String env = System.getenv("AIPLAYER_ACTION_FLUSH_SECONDS");
        long seconds = 30;
        if (env != null && !env.isBlank()) {
            try {
                seconds = Long.parseLong(env.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (seconds < 10) {
            seconds = 10;
        }
        if (seconds > 300) {
            seconds = 300;
        }
        return Duration.ofSeconds(seconds);
    }

    private int resolveActionBatchSize() {
        String env = System.getenv("AIPLAYER_ACTION_BATCH_SIZE");
        int size = 50;
        if (env != null && !env.isBlank()) {
            try {
                size = Integer.parseInt(env.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (size < 10) {
            size = 10;
        }
        if (size > 200) {
            size = 200;
        }
        return size;
    }

    private int resolveActionBufferWarnThreshold() {
        String env = System.getenv("AIPLAYER_ACTION_BUFFER_WARN");
        int size = 300;
        if (env != null && !env.isBlank()) {
            try {
                size = Integer.parseInt(env.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (size < 100) {
            size = 100;
        }
        if (size > 2000) {
            size = 2000;
        }
        return size;
    }

    public record ActionRecord(String action, String result, String createdAt) {
    }

    public record BotTask(
        long id,
        String objective,
        String status,
        String requestedBy,
        String createdAt,
        String updatedAt
    ) {
    }

    public record InteractionRecord(
        long id,
        String playerId,
        String question,
        String response,
        String createdAt
    ) {
    }

    public record AE2CraftRequest(
        long id,
        String itemId,
        int quantity,
        String status,
        String requestedBy,
        String createdAt,
        String updatedAt,
        String resultMessage
    ) {
    }
}





