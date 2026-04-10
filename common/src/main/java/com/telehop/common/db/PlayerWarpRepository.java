package com.telehop.common.db;

import com.telehop.common.model.PlayerWarpRecord;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PlayerWarpRepository {
    private final DataSource dataSource;

    public PlayerWarpRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void upsert(PlayerWarpRecord warp) {
        String sql = """
                INSERT INTO player_warps (owner_uuid, name, server, world, x, y, z, yaw, pitch, is_public)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                server = VALUES(server), world = VALUES(world),
                x = VALUES(x), y = VALUES(y), z = VALUES(z),
                yaw = VALUES(yaw), pitch = VALUES(pitch), is_public = VALUES(is_public)
                """;
        try (var conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, warp.ownerUuid());
            ps.setString(2, warp.name().toLowerCase());
            ps.setString(3, warp.server());
            ps.setString(4, warp.world());
            ps.setDouble(5, warp.x());
            ps.setDouble(6, warp.y());
            ps.setDouble(7, warp.z());
            ps.setFloat(8, warp.yaw());
            ps.setFloat(9, warp.pitch());
            ps.setBoolean(10, warp.isPublic());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to upsert player warp", e);
        }
    }

    public Optional<PlayerWarpRecord> find(String ownerUuid, String name) {
        String sql = "SELECT * FROM player_warps WHERE owner_uuid = ? AND name = ?";
        try (var conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ownerUuid);
            ps.setString(2, name.toLowerCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find player warp", e);
        }
    }

    public Optional<PlayerWarpRecord> findPublic(String ownerUuid, String name) {
        String sql = "SELECT * FROM player_warps WHERE owner_uuid = ? AND name = ? AND is_public = 1";
        try (var conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ownerUuid);
            ps.setString(2, name.toLowerCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find public player warp", e);
        }
    }

    public void delete(String ownerUuid, String name) {
        String sql = "DELETE FROM player_warps WHERE owner_uuid = ? AND name = ?";
        try (var conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ownerUuid);
            ps.setString(2, name.toLowerCase());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete player warp", e);
        }
    }

    public List<PlayerWarpRecord> listByOwner(String ownerUuid) {
        String sql = "SELECT * FROM player_warps WHERE owner_uuid = ? ORDER BY name";
        try (var conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ownerUuid);
            ResultSet rs = ps.executeQuery();
            List<PlayerWarpRecord> warps = new ArrayList<>();
            while (rs.next()) warps.add(map(rs));
            return warps;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list player warps", e);
        }
    }

    public int countByOwner(String ownerUuid) {
        String sql = "SELECT COUNT(*) FROM player_warps WHERE owner_uuid = ?";
        try (var conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ownerUuid);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count player warps", e);
        }
    }

    public List<PlayerWarpRecord> listAll() {
        String sql = "SELECT * FROM player_warps ORDER BY owner_uuid, name";
        try (var conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            List<PlayerWarpRecord> warps = new ArrayList<>();
            while (rs.next()) warps.add(map(rs));
            return warps;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list all player warps", e);
        }
    }

    public void setPublic(String ownerUuid, String name, boolean isPublic) {
        String sql = "UPDATE player_warps SET is_public = ? WHERE owner_uuid = ? AND name = ?";
        try (var conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, isPublic);
            ps.setString(2, ownerUuid);
            ps.setString(3, name.toLowerCase());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update player warp visibility", e);
        }
    }

    private PlayerWarpRecord map(ResultSet rs) throws SQLException {
        return new PlayerWarpRecord(
                rs.getString("owner_uuid"),
                rs.getString("name"),
                rs.getString("server"),
                rs.getString("world"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("yaw"),
                rs.getFloat("pitch"),
                rs.getBoolean("is_public")
        );
    }
}
