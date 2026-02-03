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
}