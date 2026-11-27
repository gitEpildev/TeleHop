package com.telehop.paper.config;

import com.telehop.common.config.DeploymentConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RegionalRoutingConfigTest {
    @TempDir Path root;

    private DeploymentConfig profile(DeploymentConfig.Mode mode) throws Exception {
        var config = DeploymentConfig.parse(Map.of("mode", mode.name()));
        var path = Files.createDirectories(config.profileDirectory(root));
        Files.writeString(root.resolve("config/database.yml"), "{}\n");
        Files.writeString(path.resolve("general.yml"),
                "server-name: quartz\nhub-server: main_hub\nmulti-proxy: {enabled: true}\n");
        for (var name : List.of("messaging", "regions", "features", "teleport", "tpa", "rtp", "respawn")) {
            if (mode != DeploymentConfig.Mode.SINGLE || !(name.equals("messaging") || name.equals("regions"))) {
                Files.writeString(path.resolve(name + ".yml"), "{}\n");
            }
        }
        // Avoid a server material registry for this routing-only configuration fixture.
        Files.writeString(path.resolve("home.yml"), "homes: {bed-set: unknown, bed-empty: unknown, bed-locked: unknown}\n");
        return config;
    }

    @Test void malformedRegionalSettingsIdentifyTheActiveFileAndTheInvalidField() throws Exception {
        var config = profile(DeploymentConfig.Mode.MULTI);
        var file = config.profileDirectory(root).resolve("regions.yml");
        var invalid = Map.ofEntries(
                Map.entry("regions: []", "regions must be a mapping"),
                Map.entry("regions: null", "regions must be a mapping"),
                Map.entry("regions: {north: disabled}", "regions.north must be a mapping"),
                Map.entry("regions: {north: {servers: [mine]}}", "regions.north.hub"),
                Map.entry("regions: {north: {hub: ' ', servers: [mine]}}", "regions.north.hub"),
                Map.entry("regions: {north: {hub: 123, servers: [mine]}}", "regions.north.hub"),
                Map.entry("regions: {north: {hub: depot}}", "regions.north.servers"),
                Map.entry("regions: {north: {hub: depot, servers: mine}}", "regions.north.servers"),
                Map.entry("regions: {north: {hub: depot, servers: []}}", "regions.north.servers"),
                Map.entry("regions: {north: {hub: depot, servers: [mine, ' ']}}", "regions.north.servers"),
                Map.entry("regions: {north: {hub: depot, servers: [mine, true]}}", "regions.north.servers"),
                Map.entry("regions: {north: {hub: depot, servers: [mine]}, NORTH: {hub: other, servers: [town]}}",
                        "regions.NORTH duplicates another region"));
        for (var entry : invalid.entrySet()) {
            Files.writeString(file, entry.getKey());
            var error = assertThrows(IllegalArgumentException.class, () -> PaperSettings.load(root, config), entry.getKey());
            assertTrue(error.getMessage().startsWith("config/multi/regions.yml: " + entry.getValue()), error::getMessage);
        }
    }

    @Test void absentAndExplicitlyEmptyRegionalRoutesKeepTheGlobalHub() throws Exception {
        var config = profile(DeploymentConfig.Mode.MULTI);
        for (var contents : List.of("{}\n", "regions: {}\n")) {
            Files.writeString(config.profileDirectory(root).resolve("regions.yml"), contents);
            var settings = PaperSettings.load(root, config);
            assertTrue(settings.regions().isEmpty());
            assertEquals("main_hub", settings.getRegionalHub("quartz"));
        }
    }

    @Test void validRegionalRoutesRetainMembershipAndUseTheOperatorsHub() throws Exception {
        var config = profile(DeploymentConfig.Mode.CUSTOM);
        var file = config.profileDirectory(root).resolve("regions.yml");
        Files.writeString(file, "regions: {North: {hub: north_hub, servers: [Quartz, pine]}, south: {hub: pier, servers: [island]}}");
        var settings = PaperSettings.load(root, config);
        assertEquals(List.of("Quartz", "pine"), settings.regions().get("north").servers());
        assertEquals("north_hub", settings.getRegionalHub("QUARTZ"));
        assertEquals("pier", settings.getRegionalHub("Island"));
        assertEquals("main_hub", settings.getRegionalHub("unknown"));
    }

    @Test void customRoutingSwitchRequiresABooleanAndDefaultsToFalse() throws Exception {
        var config = profile(DeploymentConfig.Mode.CUSTOM);
        Path file = config.profileDirectory(root).resolve("general.yml");
        String general = "server-name: quartz\nhub-server: main_hub\n";
        for (var value : List.of("'true'", "on-please", "null", "3", "[]")) {
            Files.writeString(file, general + "multi-proxy: {enabled: " + value + "}\n");
            var error = assertThrows(IllegalArgumentException.class, () -> PaperSettings.load(root, config));
            assertEquals("config/custom/general.yml: multi-proxy.enabled must be true or false", error.getMessage());
        }
        Files.writeString(file, general);
        assertFalse(PaperSettings.load(root, config).multiProxyEnabled());
        Files.writeString(file, general + "multi-proxy: {enabled: false}\n");
        assertFalse(PaperSettings.load(root, config).multiProxyEnabled());
        Files.writeString(file, general + "multi-proxy: {enabled: true}\n");
        assertTrue(PaperSettings.load(root, config).multiProxyEnabled());
    }

    @Test void singleProfileDiagnosesItsInlineRegionSection() throws Exception {
        var config = profile(DeploymentConfig.Mode.SINGLE);
        Files.writeString(config.profileDirectory(root).resolve("general.yml"), "server-name: quartz\nhub-server: main_hub\nregions: []\n");
        var error = assertThrows(IllegalArgumentException.class, () -> PaperSettings.load(root, config));
        assertTrue(error.getMessage().contains("config/single/general.yml: regions must be a mapping"), error::getMessage);
    }
}
