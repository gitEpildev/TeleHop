package com.telehop.common.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Data-access layer for the {@code players} table.
 * Tracks which server each player was last seen on, enabling
 * cross-server teleport routing.
 */
public class PlayerRepository {
    private final DataSource dataSource;

    public PlayerRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void upsert(UUID uuid, String currentServer, long lastSeen) {
        String sql = """
                INSERT INTO players (uuid, current_server, last_seen)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE current_server = VALUES(current_server), last_seen = VALUES(last_seen)
                """;
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, currentServer);
            ps.setLong(3, lastSeen);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to upsert player", e);
        }
    }

    public Optional<String> getCurrentServer(UUID uuid) {
        String sql = "SELECT current_server FROM players WHERE uuid = ?";
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.ofNullable(rs.getString("current_server"));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read player server", e);
        }
    }
}
