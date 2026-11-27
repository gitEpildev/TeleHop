package com.telehop.paper.platform;

import com.telehop.paper.v1_21.Paper121Adapter;
import com.telehop.paper.v26_1.Paper261Adapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class BackendPlatformTest {
    private static final PlatformCapabilities BUKKIT = new PlatformCapabilities(false, false, false, true, true, false, false, false, true);

    @Test
    void sharedBackendCompilesAndProbesAgainstTheSpigotApiAlone() {
        var available = PlatformCapabilities.detect();
        assertTrue(available.keyedWorlds());
        assertTrue(available.worldHeights());
        assertFalse(available.asynchronousTeleport());
        assertFalse(available.asynchronousChunks());
        assertFalse(available.regionThreaded());
        assertFalse(available.regionScheduler());
        assertFalse(available.entityScheduler());
        assertFalse(available.nativeAdventure());
        assertTrue(available.spigotChat());
    }

    @Test
    void extractsGameReleaseFromRealPaperAndSpigotApiVersions() {
        assertEquals("26.3", BackendPlatform.gameVersion("26.3.build.263-stable"));
        assertEquals("26.1.2", BackendPlatform.gameVersion("26.1.2.build.52-beta"));
        assertEquals("1.21.11", BackendPlatform.gameVersion("1.21.11-R0.2-SNAPSHOT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.21", "1.21.9", "1.21.11"})
    void existingWorldNamesRemainStable(String version) {
        assertInstanceOf(Paper121Adapter.class, BackendPlatform.select(version, 21, BUKKIT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"26.1", "26.1.2", "26.2", "27.12.10"})
    void knownApiCapabilitiesCanSelectModernWorldKeysOnLaterReleases(String version) {
        assertInstanceOf(Paper261Adapter.class, BackendPlatform.select(version, 25, BUKKIT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.8.8", "1.20.6", "26.0", "26.future", "26.2-pre1", "unknown"})
    void unsupportedOrAmbiguousVersionsAreRejected(String version) {
        assertThrows(IllegalStateException.class, () -> BackendPlatform.select(version, 25, BUKKIT));
    }

    @Test
    void runtimeAndSchedulerConstraintsAreCheckedBeforeEnabling() {
        assertThrows(IllegalStateException.class, () -> BackendPlatform.select("1.21", 17, BUKKIT));
        assertThrows(IllegalStateException.class, () -> BackendPlatform.select("26.1", 21, BUKKIT));
        var folia = new PlatformCapabilities(true, true, true, true, true, true, true, true, true);
        assertThrows(IllegalStateException.class, () -> BackendPlatform.select("1.21.11", 21, folia));
        var incomplete = new PlatformCapabilities(false, false, false, false, true, false, false, false, true);
        assertThrows(IllegalStateException.class, () -> BackendPlatform.select("27.1", 25, incomplete));
    }
}
