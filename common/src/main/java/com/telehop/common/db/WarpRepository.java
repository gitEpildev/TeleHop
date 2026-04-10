package com.telehop.common.db;

import com.telehop.common.model.WarpRecord;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class WarpRepository {
    private final DataSource dataSource;

    public WarpRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void upsert(WarpRecord warp) {
        String sql = """
                INSERT INTO warps (name, server, world, x, y, z, yaw, pitch)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                server = VALUES(server), world = VALUES(world),
                x = VALUES(x), y = VALUES(y), z = VALUES(z), yaw = VALUES(yaw), pitch = VALUES(pitch)
                """;
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, warp.name().toLowerCase());
            ps.setString(2, warp.server());
            ps.setString(3, warp.world());
            ps.setDouble(4, warp.x());
            ps.setDouble(5, warp.y());
            ps.setDouble(6, warp.z());
            ps.setFloat(7, warp.yaw());
            ps.setFloat(8, warp.pitch());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to upsert warp", e);
        }
    }

    public Optional<WarpRecord> findByName(String name) {
        String sql = "SELECT * FROM warps WHERE name = ?";
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name.toLowerCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(map(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read warp", e);
        }
    }

    public void delete(String name) {
        String sql = "DELETE FROM warps WHERE name = ?";
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name.toLowerCase());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete warp", e);
        }
    }

    public List<WarpRecord> listAll() {
        String sql = "SELECT * FROM warps ORDER BY name";
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            List<WarpRecord> warps = new ArrayList<>();
            while (rs.next()) {
                warps.add(map(rs));
            }
            return warps;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list warps", e);
        }
    }

    private WarpRecord map(ResultSet rs) throws SQLException {
        return new WarpRecord(
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
