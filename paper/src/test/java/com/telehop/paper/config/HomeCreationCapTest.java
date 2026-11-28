package com.telehop.paper.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HomeCreationCapTest {
    @Test void freshAndStockProfilesDelegateToPermissions() {
        var config = new YamlConfiguration();
        assertEquals(0, PaperSettings.homeCreationCap(config));
        config.set("homes.max-slots", 10);
        assertEquals(0, PaperSettings.homeCreationCap(config));
    }

    @Test void smallerLegacyCapsArePreservedUntilExplicitlyReplaced() {
        var config = new YamlConfiguration();
        config.set("homes.max-slots", 3);
        assertEquals(3, PaperSettings.homeCreationCap(config));
        config.set("homes.creation-cap", 240);
        assertEquals(240, PaperSettings.homeCreationCap(config));
        config.set("homes.creation-cap", 0);
        assertEquals(0, PaperSettings.homeCreationCap(config));
    }
}
