package com.telehop.common.db;

import com.telehop.common.model.HomeRecord;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class HomeRepository {
    private final DataSource dataSource;

    public HomeRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void upsert(HomeRecord home) {
        String sql = """
                INSERT INTO homes (uuid, slot, server, world, x, y, z, yaw, pitch)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE server=?, world=?, x=?, y=?, z=?, yaw=?, pitch=?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, home.uuid());
            ps.setInt(2, home.slot());
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
        String sql = "SELECT uuid, slot, server, world, x, y, z, yaw, pitch FROM homes WHERE uuid = ? ORDER BY slot";
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

    public Optional<HomeRecord> find(String uuid, int slot) {
        String sql = "SELECT uuid, slot, server, world, x, y, z, yaw, pitch FROM homes WHERE uuid = ? AND slot = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setInt(2, slot);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(fromResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find home", e);
        }
        return Optional.empty();
    }

    public void delete(String uuid, int slot) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM homes WHERE uuid = ? AND slot = ?")) {
            ps.setString(1, uuid);
            ps.setInt(2, slot);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete home", e);
        }
    }

    private HomeRecord fromResultSet(ResultSet rs) throws SQLException {
        return new HomeRecord(
                rs.getString("uuid"),
                rs.getInt("slot"),
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
