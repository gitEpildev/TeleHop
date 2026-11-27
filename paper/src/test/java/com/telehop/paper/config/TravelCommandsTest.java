package com.telehop.paper.config;

import com.telehop.common.PermissionNodes;
import com.telehop.paper.service.PermissionService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TravelCommandsTest {
    @TempDir Path data;
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final PaperSettings settings = mock(PaperSettings.class);
    private Path profile;

    @BeforeEach void setUp() throws Exception {
        profile = Files.createDirectories(data.resolve("config/single"));
        Files.writeString(profile.resolve("rtp.yml"), """
                rtp:
                  max-radius: 800
                  regions: {islands: {radius: 500}}
                  dimensions: {caves: deep_caves, overworld: coast}
                """);
        when(plugin.getResource(anyString())).thenAnswer(invocation ->
                getClass().getClassLoader().getResourceAsStream(invocation.<String>getArgument(0)));
        when(plugin.getDataFolder()).thenReturn(data.toFile());
        when(settings.servers()).thenReturn(Map.of("islands", "isle"));
        when(settings.regions()).thenReturn(Map.of());
        when(settings.serverName()).thenReturn("home");
        when(settings.isFeatureEnabled(anyString())).thenReturn(true);
    }

    private CommandNames load(boolean rename, boolean additional) {
        return CommandNames.load(plugin, settings, profile.toFile(), rename, additional, true);
    }

    private void rtp(String variant) throws Exception {
        CommandNames.installMissing(plugin, data, true);
        Files.writeString(data.resolve("commands/rtp.yml"), """
                rtp:
                  additional-commands:
                    enabled: true
                    commands:
                      island-trip:
                """ + variant.indent(8));
    }

    @Test void freshCommandGroupsKeepTheirPackagedBytes() throws Exception {
        CommandNames.installMissing(plugin, data, true);
        for (String id : CommandNames.GROUPS) {
            String resource = "commands/" + id + ".yml";
            try (var expected = plugin.getResource(resource)) {
                assertArrayEquals(expected.readAllBytes(), Files.readAllBytes(data.resolve(resource)), resource);
            }
        }
    }

    @Test void runtimeValidationReportsMissingGroupsWithoutInstallingDefaultsOrLegacyAliases() throws Exception {
        Path directory = data.resolve("commands");
        Files.writeString(data.resolve("config/commands.yml"), "hub: [lobby, hub]\n");
        assertThrows(IllegalArgumentException.class, () -> load(true, true));
        assertFalse(Files.exists(directory));

        CommandNames.installMissing(plugin, data, true);
        var snapshot = load(true, true);
        var missing = directory.resolve("hub.yml");
        Files.delete(missing);
        var error = assertThrows(IllegalArgumentException.class, () -> load(true, true));
        assertTrue(error.getMessage().contains("hub.yml"));
        assertFalse(Files.exists(missing));
        assertEquals("lobby", snapshot.primary("hub"));
    }

    @Test void migrationRetainsEveryLegacyAliasAndOperatorEditsOnRestart() throws Exception {
        Files.writeString(data.resolve("config/commands.yml"), "tp: [travel, jumpto]\nspawn: [spawn]\n");
        CommandNames.installMissing(plugin, data, true);
        assertEquals(List.of("travel", "jumpto"), load(true,false).labels().get("tp"));
        assertTrue(Files.readString(data.resolve("commands/teleport.yml")).contains("enabled: true"));
        try (var expected = plugin.getResource("commands/spawn.yml")) {
            assertArrayEquals(expected.readAllBytes(), Files.readAllBytes(data.resolve("commands/spawn.yml")));
        }
        Files.writeString(data.resolve("commands/teleport.yml"), "tp: {custom-name: {enabled: true, name: goto, aliases: [go]}}\n");
        assertEquals(List.of("goto","go"), load(true,false).labels().get("tp"));
        assertEquals(List.of("tp"), load(false,false).labels().get("tp"));
    }

    @Test void adminRecoveryLabelSurvivesCustomNamesAndCannotBeReassigned() throws Exception {
        CommandNames.installMissing(plugin, data, true);
        Files.writeString(data.resolve("commands/admin.yml"), "telehop: {custom-name: {enabled: true, name: network}}\n");
        assertEquals(List.of("network","telehop"), load(true,true).labels().get("telehop"));
        Files.writeString(data.resolve("commands/teleport.yml"), "tp: {custom-name: {enabled: true, name: telehop}}\n");
        assertThrows(IllegalArgumentException.class, () -> load(true,true));
    }

    @Test void directVariantInheritsRegionalLimitsAndOnlyChangesExplicitSettings() throws Exception {
        rtp("""
                name: isle
                aliases: [islandjump]
                destination: {region: islands, dimension: caves}
                settings: {inherit-defaults: true, overrides: {delay-seconds: 0}}
                permissions: {require-command-permission: true, require-region-permission: true}
                """);
        var active = load(true,true);
        var action = active.variants().get("island-trip");
        assertEquals(List.of("isle","islandjump"), action.labels());
        assertEquals(0, action.delay());
        assertNull(action.cooldown());
        assertEquals(new RtpPolicy.Choice("islands","caves","isle","deep_caves",500), active.rtp().resolve(action,"islands","caves"));
        assertNull(active.rtp().resolve(action,"islands","overworld"));
        assertNull(active.rtp().resolve(action,"unrelated","caves"));
        assertTrue(load(true,false).variants().isEmpty());
    }

    @Test void arrivalsAndRegionOnlyCommandsUseTheConfiguredDefaults() throws Exception {
        Files.writeString(profile.resolve("rtp.yml"), """
                rtp:
                  default-region: islands
                  default-dimension: grove
                  regions: {pier: {}, islands: {world: shoreline, radius: 90}}
                  dimensions: {mines: old_quarry, grove: hillside}
                  gui: {dimension-items: {mines: {material: STONE, name: Quarry}}}
                """);
        rtp("name: islandjump\ndestination: {region: islands}");
        var active = load(true, true);
        var policy = active.rtp();
        var expected = new RtpPolicy.Choice("islands", "grove", "isle", "shoreline", 90);
        assertEquals(expected, policy.resolve(null, null, null));
        assertEquals(expected, policy.resolve(active.variants().get("island-trip"), "islands", policy.defaultDimension()));
        assertEquals("old_quarry", policy.resolve(null, null, "mines").world());
        assertEquals("STONE", policy.dimensionItem("mines", "mines").material());
        assertNull(policy.resolve(null, "missing", null));
        assertNull(policy.resolve(null, null, "missing"));
    }

    @Test void discoversLoadedWorldsAndRejectsAnUnknownDefault() throws Exception {
        var server = mock(org.bukkit.Server.class);
        var first = mock(org.bukkit.World.class);
        var second = mock(org.bukkit.World.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorlds()).thenReturn(List.of(first, second));
        when(first.getName()).thenReturn("Harbour");
        when(second.getName()).thenReturn("Deep Mines");
        CommandNames.installMissing(plugin, data, true);
        Files.writeString(profile.resolve("rtp.yml"), "rtp: {dimensions: {}, regions: {}}\n");
        var policy = load(true, true).rtp();
        assertFalse(policy.selectDimensionWithoutRegion());
        assertEquals("Harbour", policy.resolve(null, null, null).world());
        assertEquals("Deep Mines", policy.resolve(null, null, "deep-mines").world());
        rtp("name: excavate\ndestination: {world: Deep Mines}");
        var direct = load(true, true);
        assertEquals("Deep Mines", direct.rtp().resolve(direct.variants().get("island-trip"), "", "").world());
        assertEquals("telehop.rtp.world.deep%20mines", PermissionNodes.rtpDestination("world", "Deep Mines"));
        Files.writeString(profile.resolve("rtp.yml"), "rtp: {default-dimension: missing}\n");
        assertThrows(IllegalArgumentException.class, () -> load(true, true));
    }

    @Test void invalidAndAmbiguousTargetsAndOutOfRangeOverridesAreRejected() throws Exception {
        for (String dest : List.of("{region: missing}", "{dimension: moon}", "{server: unlisted}",
                "{world: missing}", "{region: islands, server: isle}", "{world: coast, dimension: caves}")) {
            rtp("name: test\ndestination: "+dest);
            assertThrows(IllegalArgumentException.class, () -> load(true,true), dest);
        }
        rtp("name: test\ndestination: {dimension: caves}\nsettings: {overrides: {max-radius: -2}}");
        assertThrows(IllegalArgumentException.class, () -> load(true,true));
    }

    @Test void explicitServerAndWorldRemainBoundToTheirConfiguredDestination() throws Exception {
        rtp("name: test\ndestination: {server: isle, world: deep_caves}\nsettings: {overrides: {max-radius: 900}}");
        var active = load(true,true);
        var variant = active.variants().get("island-trip");
        assertEquals(new RtpPolicy.Choice("","","isle","deep_caves",800), active.rtp().resolve(variant,"",""));
        assertNull(active.rtp().resolve(variant,"islands",""));
        assertNull(active.rtp().resolve(variant,"","overworld"));
    }

    @Test void duplicateAliasAcrossVariantAndDefaultRejectsTheCandidate() throws Exception {
        rtp("name: island\naliases: [tpaaccept]\ndestination: {region: islands, dimension: caves}");
        assertThrows(IllegalArgumentException.class, () -> load(true,true));
    }

    @Test void individualWarpGrantAndDenialTakePrecedenceOverGroupGrant() {
        var permissions = new PermissionService();
        var player = mock(Player.class);
        when(player.isPermissionSet("telehop.warp.island")).thenReturn(true);
        when(player.hasPermission("telehop.warp.island")).thenReturn(true);
        assertTrue(permissions.warp(player, "Island"));
        assertFalse(permissions.warp(player,"other"));
        when(player.hasPermission(PermissionNodes.WARP)).thenReturn(true);
        when(player.hasPermission("telehop.warp.island")).thenReturn(false);
        assertFalse(permissions.warp(player,"Island"));
        assertTrue(permissions.warp(player,"other"));
    }

    @Test void stableIdAndDestinationPermissionsApplyToAllAliasesAndRespectChangedWorldContext() throws Exception {
        rtp("name: jump\naliases: [alt]\ndestination: {dimension: caves}\npermissions: {require-command-permission: true}");
        var commands = load(true,true);
        var variant = commands.variants().get("island-trip");
        var player = mock(Player.class);
        var permissions = new PermissionService();
        when(player.hasPermission(PermissionNodes.RTP)).thenReturn(true);
        var choice = commands.rtp().resolve(variant,"","caves");
        assertFalse(RtpPolicy.allowed(player,variant,choice,permissions));
        when(player.isPermissionSet("telehop.command.island-trip")).thenReturn(true);
        when(player.hasPermission("telehop.command.island-trip")).thenReturn(true);
        assertTrue(RtpPolicy.allowed(player,variant,choice,permissions));
        when(player.isPermissionSet("telehop.rtp.world.deep_caves")).thenReturn(true);
        assertFalse(RtpPolicy.allowed(player,variant,choice,permissions));
        assertTrue(commands.rtp().eligibleDimensions(player, variant, "", permissions).isEmpty());
    }

    @Test void partialMigrationResumesWithoutTouchingAnExistingGroup() throws Exception {
        Path folder = Files.createDirectories(data.resolve("commands"));
        String before = "# Operator notes\nrtp: {}\n";
        Files.writeString(folder.resolve("rtp.yml"), before);
        Files.writeString(data.resolve("config/commands.yml"), "hub: [lobby, hub]\n");
        CommandNames.installMissing(plugin, data, true);
        assertEquals(List.of("lobby","hub"), load(true, true).labels().get("hub"));
        assertEquals(before, Files.readString(folder.resolve("rtp.yml")));
        Files.writeString(data.resolve("config/commands.yml"), "orphan: [\n");
        assertEquals(List.of("lobby","hub"), load(true,true).labels().get("hub"));
    }

    @Test void invalidYamlAndUnexpectedOptionsCannotChangeAnExistingSnapshot() throws Exception {
        CommandNames.installMissing(plugin, data, true);
        var original = load(true,true);
        var file = data.resolve("commands/hub.yml");
        Files.writeString(file, "hub: {custom-name: {enabled: true, name: lobby}}\nhub: {}\n");
        assertThrows(IllegalArgumentException.class, () -> load(true,true));
        Files.writeString(file, "hub: {custom-name: {enabled: true, name: lobby}, run-command: example}\n");
        assertThrows(IllegalArgumentException.class, () -> load(true,true));
        assertEquals("hub",original.primary("hub"));
    }

    @Test void namedSpawnAndHubEachAcceptOnlyTheirOwnDestinationType() throws Exception {
        CommandNames.installMissing(plugin, data, true);
        var file = data.resolve("commands/spawn.yml");
        Files.writeString(file, """
                spawn:
                  additional-commands:
                    enabled: true
                    commands:
                      garden-trip: {name: garden, destination: {type: NAMED_SPAWN, target: orchard}}
                """);
        assertEquals("orchard", load(true,true).variants().get("garden-trip").spawn());
        Files.writeString(file, Files.readString(file).replace("NAMED_SPAWN","HUB"));
        assertThrows(IllegalArgumentException.class, () -> load(true,true));
    }
}
