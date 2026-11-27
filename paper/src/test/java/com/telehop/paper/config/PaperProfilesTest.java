package com.telehop.paper.config;

import com.telehop.common.config.ConfigYaml;
import com.telehop.common.config.DeploymentConfig;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PaperProfilesTest {
    @TempDir Path dir;

    private JavaPlugin plugin() {
        var plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dir.toFile());
        when(plugin.getResource(anyString())).thenAnswer(call -> getClass().getClassLoader().getResourceAsStream(call.getArgument(0)));
        return plugin;
    }

    @Test void freshInstallationHasThreeProfilesAndOnlySharedFilesInTheConfigRoot() throws Exception {
        var selected = PaperProfiles.prepareFiles(dir, plugin(), true);
        assertEquals(DeploymentConfig.Mode.SINGLE, selected.mode());
        assertFalse(ConfigMigrator.needsMigration(plugin()));
        var selector = Files.readString(dir.resolve("config.yml"));
        ConfigMigrator.migrate(plugin());
        assertEquals(selector, Files.readString(dir.resolve("config.yml")));
        assertFalse(Files.exists(dir.resolve("config.yml.old")));
        var identity = ConfigYaml.read(selected.profileDirectory(dir).resolve("general.yml"));
        assertNotEquals("lobby", identity.get("server-name"));
        assertEquals(identity.get("server-name"), identity.get("hub-server"));
        assertEquals(java.util.Map.of(), identity.get("servers"));
        assertEquals(java.util.Map.of(), identity.get("regions"));
        assertTrue(ConfigYaml.flag(identity, "messaging.network-enabled", false));
        try (var entries = Files.list(dir.resolve("config"))) {
            assertEquals(java.util.Set.of("single", "multi", "custom", "database.yml", "WIKI.md"),
                    entries.map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()));
        }
        for (String profile : java.util.List.of("single", "multi", "custom")) {
            var respawn = ConfigYaml.read(dir.resolve("config").resolve(profile).resolve("respawn.yml"));
            assertEquals("", ConfigYaml.get(respawn, "random-respawn.world", null));
            assertEquals(java.util.List.of(), ConfigYaml.get(respawn, "random-respawn.excluded-worlds", null));
            try (var files = Files.list(dir.resolve("config").resolve(profile))) {
                var expected = new java.util.HashSet<>(java.util.Set.of("general.yml", "features.yml", "spawns.yml",
                        "teleport.yml", "tpa.yml", "rtp.yml", "respawn.yml", "home.yml"));
                if (!profile.equals("single")) expected.addAll(java.util.Set.of("messaging.yml", "regions.yml"));
                assertEquals(expected,
                        files.map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(DeploymentConfig.Mode.class)
    void everyInstalledProfileUsesItsPackagedTemplates(DeploymentConfig.Mode active) throws Exception {
        var plugin = plugin();
        Files.writeString(dir.resolve("config.yml"), "mode: " + active + "\n");
        PaperProfiles.prepareFiles(dir, plugin, true);
        for (var mode : DeploymentConfig.Mode.values()) {
            Path profile = dir.resolve("config").resolve(mode.name().toLowerCase(java.util.Locale.ROOT));
            try (var installed = Files.list(profile)) {
                for (var file : installed.toList()) {
                    String name = file.getFileName().toString();
                    String contents = PaperProfiles.resource(plugin, PaperProfiles.template(mode, name));
                    if (!name.equals("general.yml")) {
                        assertEquals(contents, Files.readString(file), file.toString());
                        continue;
                    }
                    var expected = ConfigYaml.parse(contents, name);
                    var generated = ConfigYaml.read(file);
                    var backend = generated.get("server-name");
                    assertNotEquals("", backend);
                    expected.put("server-name", backend);
                    expected.put("hub-server", backend);
                    assertEquals(expected, generated, file.toString());
                    assertEquals(mode == DeploymentConfig.Mode.MULTI, ConfigYaml.flag(generated, "multi-proxy.enabled", false));
                }
            }
        }
    }

    @Test void existingFlatStandaloneMessagingOverridesTheBundledInlineDefaults() throws Exception {
        var old = Files.createDirectories(dir.resolve("config"));
        Files.writeString(old.resolve("messaging.yml"), "messaging: {network-enabled: false, request-timeout-ms: 18300}\n");
        var profile = PaperProfiles.prepareFiles(dir, plugin(), true).profileDirectory(dir);
        var general = ConfigYaml.read(profile.resolve("general.yml"));
        assertFalse(ConfigYaml.flag(general, "messaging.network-enabled", true));
        assertEquals(18300, ConfigYaml.get(general, "messaging.request-timeout-ms", null));
        assertFalse(Files.exists(profile.resolve("messaging.yml")));
    }

    @Test void explicitReplacementWinsWhileObsoleteHubFlagIsRemoved() throws Exception {
        Path old = Files.createDirectories(dir.resolve("config"));
        Files.writeString(old.resolve("features.yml"), "features: {hub: false, rtp: false}\nextra: retained\n");
        String spawns = "# keep routes\nhub-command: {enabled: true}\ndestinations: {}\n";
        Files.writeString(old.resolve("spawns.yml"), spawns);
        Files.writeString(old.resolve("general.yml"), "server-name: station\nmulti-proxy: {enabled: true}\ncustom: preserved\n"
                + "regions: {one: {hub: station, servers: [station]}}\nmessaging: {request-timeout-ms: 3400}\n");
        var selected = PaperProfiles.prepareFiles(dir, plugin(), true);
        assertEquals(DeploymentConfig.Mode.MULTI, selected.mode());
        var profile = selected.profileDirectory(dir);
        assertFalse(ConfigYaml.flag(ConfigYaml.read(profile.resolve("features.yml")), "features.hub", true));
        assertFalse(ConfigYaml.flag(ConfigYaml.read(profile.resolve("features.yml")), "features.rtp", true));
        assertFalse(ConfigYaml.read(profile.resolve("spawns.yml")).containsKey("hub-command"));
        assertEquals("preserved", ConfigYaml.read(profile.resolve("general.yml")).get("custom"));
        assertEquals(3400, ConfigYaml.get(ConfigYaml.read(profile.resolve("messaging.yml")), "messaging.request-timeout-ms", null));
        assertEquals("station", ConfigYaml.get(ConfigYaml.read(profile.resolve("regions.yml")), "regions.one.hub", null));
        assertEquals(spawns, Files.readString(old.resolve("spawns.yml")));
        assertTrue(Files.isRegularFile(dir.resolve("config/single/spawns.yml")));
    }

    @Test void incompleteSelectorMustNotBeTreatedAsAnOldMonolithicFile() throws Exception {
        var selector = dir.resolve("config.yml");
        Files.writeString(selector, "config-version: 1\n");
        assertThrows(IllegalArgumentException.class, () -> PaperProfiles.prepareFiles(dir, plugin(), true));
        assertEquals("config-version: 1\n", Files.readString(selector));
        assertFalse(Files.exists(dir.resolve("config.yml.old")));
    }

    @Test void missingLegacySelectorsKeepTheirWorldsAndMenuMaterials() throws Exception {
        var old = Files.createDirectories(dir.resolve("config"));
        Files.writeString(old.resolve("respawn.yml"), "random-respawn: {excluded-worlds: [private_gardens]}\n");
        Files.writeString(old.resolve("rtp.yml"), "rtp: {max-radius: 1200}\n");
        var profile = PaperProfiles.prepareFiles(dir, plugin(), true).profileDirectory(dir);
        var yaml = ConfigYaml.read(profile.resolve("rtp.yml"));
        var respawn = ConfigYaml.read(profile.resolve("respawn.yml"));
        assertEquals("world", ConfigYaml.get(respawn, "random-respawn.world", null));
        assertEquals(java.util.List.of("private_gardens"), ConfigYaml.get(respawn, "random-respawn.excluded-worlds", null));
        assertEquals("world_nether", ConfigYaml.get(yaml, "rtp.dimensions.nether", null));
        assertEquals("overworld", ConfigYaml.get(yaml, "rtp.default-dimension", null));
        assertEquals("END_STONE", ConfigYaml.get(yaml, "rtp.gui.dimension-items.end.material", null));
        assertEquals(1200, ConfigYaml.get(yaml, "rtp.max-radius", null));
    }

    @Test void inheritsHubSettingWhenReplacementIsAbsentAndMigratesCustomAliases() throws Exception {
        Path old = Files.createDirectories(dir.resolve("config"));
        Files.writeString(old.resolve("spawns.yml"), "hub-command: {enabled: true}\nrouting: {enabled: false}\n");
        Files.writeString(old.resolve("commands.yml"), "spawn: [station, base]\n");
        var result = PaperProfiles.prepareFiles(dir, plugin(), true);
        assertTrue(result.customNames());
        assertTrue(ConfigYaml.flag(ConfigYaml.read(result.profileDirectory(dir).resolve("features.yml")), "features.hub", false));
        assertFalse(Files.readString(result.profileDirectory(dir).resolve("spawns.yml")).contains("hub-command"));
    }

    @Test void languageBackfillKeepsCustomTextAndRejectsLostSubstitutions() throws Exception {
        var plugin = plugin();
        Path messages = Files.createDirectories(dir.resolve("languages")).resolve("en.yml");
        Files.writeString(messages, "# Team wording\nprefix: 'Example '\n");
        LanguageFiles.complete(plugin, dir);
        assertEquals("Example ", LanguageFiles.load(dir, "en", plugin).getString("prefix"));
        assertFalse(LanguageFiles.incomplete(plugin, dir));
        // A bundled language file is a more realistic fixture than a hard-coded example key.
        String packaged = PaperProfiles.resource(plugin, "languages/en.yml");
        String found = packaged.lines().filter(line -> !line.startsWith("#") && line.contains(":") && line.contains("<target>")).findFirst().orElseThrow();
        String key = found.substring(0, found.indexOf(':'));
        Files.writeString(messages, key + ": 'missing the target'\n");
        assertThrows(IllegalArgumentException.class, () -> LanguageFiles.load(dir, "en", plugin));
    }
}
