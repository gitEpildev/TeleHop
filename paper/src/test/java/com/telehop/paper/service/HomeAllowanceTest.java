package com.telehop.paper.service;

import com.telehop.common.PermissionNodes;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HomeAllowanceTest {
    @Test void usesTheHighestEffectiveGrantAndIgnoresDeniedAndMalformedNodes() {
        Player p = mock(Player.class);
        var twenty = grant(p, "20", true);
        var high = grant(p, "240", true);
        var denied = grant(p, "500", false);
        var malformed = grant(p, "50oops", true);
        when(p.getEffectivePermissions()).thenReturn(Set.of(twenty, high, denied, malformed));
        assertEquals(240, HomeAllowance.forPlayer(p, 0));
        assertEquals(100, HomeAllowance.forPlayer(p, 100));
    }

    @Test void zeroGrantsAndUnlimitedAreDistinct() {
        Player p = mock(Player.class);
        assertEquals(0, HomeAllowance.forPlayer(p, 0));
        when(p.hasPermission(PermissionNodes.HOMES_UNLIMITED)).thenReturn(true);
        assertEquals(Integer.MAX_VALUE, HomeAllowance.forPlayer(p, 0));
        assertEquals(12, HomeAllowance.forPlayer(p, 12));
    }

    private static PermissionAttachmentInfo grant(Player p, String suffix, boolean allowed) {
        String node = PermissionNodes.HOME_LIMIT_PREFIX + suffix;
        when(p.hasPermission(node)).thenReturn(allowed);
        return new PermissionAttachmentInfo(p, node, null, allowed);
    }
}
