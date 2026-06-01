package com.telehop.common.db;

import com.telehop.common.model.HomeRecord;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data-access layer for player homes stored in the {@code homes} table.
 * All methods obtain their own connection from the pool and are safe to
 * call from the {@link DatabaseManager} async executor.
 */
public class HomeRepository {
    private final DataSource dataSource;

    public HomeRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void upsert(HomeRecord home) {
        String sql = """
                INSERT INTO homes (uuid, name, server, world, x, y, z, yaw, pitch)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE server=?, world=?, x=?, y=?, z=?, yaw=?, pitch=?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, home.uuid());
            ps.setString(2, home.name());
            ps.setString(3, home.server());
            ps.setString(4, home.world());
            ps.setDouble(5, home.x());
            ps.setDouble(6, home.y());
            ps.setDouble(7, home.z());
            ps.setFloat(8, home.yaw());
            ps.setFloat(9, home.pitch());
            ps.setString(10, home.server());
            ps.setString(11, home.world());
            ps.setDouble(12, home.x());
            ps.setDouble(13, home.y());
            ps.setDouble(14, home.z());
            ps.setFloat(15, home.yaw());
            ps.setFloat(16, home.pitch());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to upsert home", e);
        }
    }

    public List<HomeRecord> listByPlayer(String uuid) {
        String sql = "SELECT uuid, name, server, world, x, y, z, yaw, pitch FROM homes WHERE uuid = ? ORDER BY LENGTH(name), name";
        List<HomeRecord> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(fromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list homes", e);
        }
        return result;
    }

    public Optional<HomeRecord> find(String uuid, String name) {
        String sql = "SELECT uuid, name, server, world, x, y, z, yaw, pitch FROM homes WHERE uuid = ? AND LOWER(name) = LOWER(?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(fromResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find home", e);
        }
        return Optional.empty();
    }

    public void delete(String uuid, String name) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM homes WHERE uuid = ? AND LOWER(name) = LOWER(?)")) {
            ps.setString(1, uuid);
            ps.setString(2, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete home", e);
        }
    }

    public int countByPlayer(String uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM homes WHERE uuid = ?")) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count homes", e);
        }
        return 0;
    }

    private HomeRecord fromResultSet(ResultSet rs) throws SQLException {
        return new HomeRecord(
                rs.getString("uuid"),
                rs.getString("name"),
                rs.getString("server"),
                rs.getString("world"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("yaw"),
                rs.getFloat("pitch")
        );
    }
}
