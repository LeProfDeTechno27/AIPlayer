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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class BotMemoryRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(BotMemoryRepository.class);

    private final Path databasePath;

    public BotMemoryRepository(Path databasePath) {
        this.databasePath = databasePath;
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
        String sql = "INSERT INTO bot_actions(action, result, created_at) VALUES (?, ?, ?)";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, action);
            statement.setString(2, result);
            statement.setString(3, Instant.now().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            LOGGER.warn("Failed to record action '{}'", action, exception);
        }
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
                "CREATE TABLE IF NOT EXISTS bot_actions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "action TEXT NOT NULL," +
                    "result TEXT NOT NULL," +
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

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + this.databasePath.toAbsolutePath());
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