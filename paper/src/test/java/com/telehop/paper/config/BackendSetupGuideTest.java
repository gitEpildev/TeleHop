package com.telehop.paper.config;

import com.telehop.common.config.DeploymentConfig;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BackendSetupGuideTest {
    @TempDir Path data;

    private JavaPlugin plugin() {
        var plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(data.toFile());
        when(plugin.getResource(anyString())).thenAnswer(call -> getClass().getClassLoader().getResourceAsStream(call.getArgument(0)));
        return plugin;
    }

    @Test void oldGeneratedSelectorGainsInstructionsAndKeepsTheChosenModeAndCommandNames() throws Exception {
        Path selector = data.resolve("config.yml");
        Files.writeString(selector, DeploymentConfig.initial(DeploymentConfig.Mode.MULTI, true));
        var selected = PaperProfiles.prepareFiles(data, plugin(), false);
        assertEquals(DeploymentConfig.Mode.MULTI, selected.mode());
        assertTrue(selected.customNames());
        assertTrue(Files.readString(selector).contains("config/multi/regions.yml"));
    }

    @Test void ownerNotesInASelectorAndGuideSurviveConfigurationRepair() throws Exception {
        var plugin = plugin();
        Path selector = data.resolve("config.yml");
        String settings = "# The events server\n" + DeploymentConfig.initial(DeploymentConfig.Mode.SINGLE, false);
        Files.writeString(selector, settings);
        Path guide = Files.createDirectories(data.resolve("config")).resolve("WIKI.md");
        String localGuide = "# Reply to the server owner before changing the hub\n";
        Files.writeString(guide, localGuide);
        PaperProfiles.prepareFiles(data, plugin, false);
        assertEquals(settings, Files.readString(selector));
        assertEquals(localGuide, Files.readString(guide));
    }

    @Test void freshSelectorAndMissingGuideMatchTheEditableResourcesByteForByte() throws Exception {
        var plugin = plugin();
        PaperProfiles.prepareFiles(data, plugin, true);
        assertEquals(PaperProfiles.resource(plugin, "config.yml"), Files.readString(data.resolve("config.yml")));
        var guide = data.resolve("config/WIKI.md");
        Files.delete(guide);
        assertTrue(ConfigMigrator.needsGuideUpdate(data));
        ConfigMigrator.refreshBundledGuide(plugin, data);
        assertEquals(PaperProfiles.resource(plugin, "config/WIKI.md"), Files.readString(guide));
        assertFalse(ConfigMigrator.needsGuideUpdate(data));
    }
}
