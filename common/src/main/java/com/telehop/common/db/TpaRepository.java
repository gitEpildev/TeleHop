package com.telehop.common.db;

import com.telehop.common.model.TpaRequestRecord;
import com.telehop.common.model.TpaType;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class TpaRepository {
    private final DataSource dataSource;

    public TpaRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void upsert(TpaRequestRecord request) {
        String sql = """
                INSERT INTO tpa_requests (sender_uuid, target_uuid, type, expiry)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE type = VALUES(type), expiry = VALUES(expiry)
                """;
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, request.senderUuid().toString());
            ps.setString(2, request.targetUuid().toString());
            ps.setString(3, request.type().name());
            ps.setLong(4, request.expiry().toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to upsert tpa request", e);
        }
    }

    public Optional<TpaRequestRecord> find(UUID sender, UUID target) {
        String sql = "SELECT * FROM tpa_requests WHERE sender_uuid = ? AND target_uuid = ?";
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sender.toString());
            ps.setString(2, target.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(map(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get tpa request", e);
        }
    }

    public void delete(UUID sender, UUID target) {
        String sql = "DELETE FROM tpa_requests WHERE sender_uuid = ? AND target_uuid = ?";
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sender.toString());
            ps.setString(2, target.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete tpa request", e);
        }
    }

    public List<TpaRequestRecord> findExpired(Instant now) {
        String sql = "SELECT * FROM tpa_requests WHERE expiry <= ?";
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, now.toEpochMilli());
            ResultSet rs = ps.executeQuery();
            List<TpaRequestRecord> requests = new ArrayList<>();
            while (rs.next()) {
                requests.add(map(rs));
            }
            return requests;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list expired tpa requests", e);
        }
    }

    private TpaRequestRecord map(ResultSet rs) throws SQLException {
        return new TpaRequestRecord(
                UUID.fromString(rs.getString("sender_uuid")),
                UUID.fromString(rs.getString("target_uuid")),
                TpaType.valueOf(rs.getString("type")),
                Instant.ofEpochMilli(rs.getLong("expiry"))
        );
    }
}
