package com.telehop.paper.service;

import com.telehop.common.PermissionNodes;
import com.telehop.common.model.WarpRecord;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DestinationContextTest {
    @Test void localWarpChecksTargetWorldAndRechecksPermissionAfterAnUpdate() {
        var world = mock(World.class);
        var member = mock(Player.class);
        when(member.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("lobby");
        when(member.hasPermission(PermissionNodes.WARP)).thenReturn(true);
        var permission = mock(PermissionService.WorldDecision.class);
        var service = new PermissionService(permission);
        var warp = new WarpRecord("cave", "survival", "caves", 0, 64, 0, 0, 0);
        when(permission.query(member, "caves", PermissionNodes.warpNode("cave"))).thenReturn(false, true);
        assertFalse(service.warp(member, warp,"survival"));
        assertTrue(service.warp(member, warp,"survival"));
        assertTrue(service.warp(member, warp,"another-server"));
        verify(permission, times(2)).query(member, "caves", PermissionNodes.warpNode("cave"));
    }

    @Test void anUnsetTargetContextRequiresAnExplicitGrantForRestrictedWorlds() {
        var world = mock(World.class);
        var member = mock(Player.class);
        when(member.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("surface");
        var service = new PermissionService((player, target, node) -> null);
        assertFalse(service.inWorld(member, "depths", "telehop.command.descend", false));
        assertTrue(service.inWorld(member, "depths", "telehop.rtp.dimension.depths", true));
    }
}
