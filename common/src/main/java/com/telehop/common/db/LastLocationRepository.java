package com.telehop.common.db;

import com.telehop.common.model.LastLocationRecord;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;

/**
 * Data-access layer for persistent player logout locations
 * stored in the {@code last_locations} table.
 */
public class LastLocationRepository {
    private final DataSource dataSource;

    public LastLocationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void upsert(LastLocationRecord record) {
        String sql = """
                INSERT INTO last_locations (uuid, server, world, x, y, z, yaw, pitch)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE server=?, world=?, x=?, y=?, z=?, yaw=?, pitch=?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.uuid());
            ps.setString(2, record.server());
            ps.setString(3, record.world());
            ps.setDouble(4, record.x());
            ps.setDouble(5, record.y());
            ps.setDouble(6, record.z());
            ps.setFloat(7, record.yaw());
            ps.setFloat(8, record.pitch());
            ps.setString(9, record.server());
            ps.setString(10, record.world());
            ps.setDouble(11, record.x());
            ps.setDouble(12, record.y());
            ps.setDouble(13, record.z());
            ps.setFloat(14, record.yaw());
            ps.setFloat(15, record.pitch());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to upsert last location", e);
        }
    }

    public void delete(String uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM last_locations WHERE uuid = ?")) {
            ps.setString(1, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete last location", e);
        }
    }

    public Optional<LastLocationRecord> find(String uuid) {
        String sql = "SELECT uuid, server, world, x, y, z, yaw, pitch FROM last_locations WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new LastLocationRecord(
                            rs.getString("uuid"),
                            rs.getString("server"),
                            rs.getString("world"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find last location", e);
        }
        return Optional.empty();
    }
}
