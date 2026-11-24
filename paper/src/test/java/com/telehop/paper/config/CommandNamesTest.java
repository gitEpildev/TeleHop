package com.telehop.paper.config;

import co.aikar.commands.CommandReplacements;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CommandNamesTest {
    @Test
    void renamedCommandsDriveRegistrationHelpAndClickEvents() {
        var defaults = new YamlConfiguration();
        defaults.set("tp", List.of("tp"));
        defaults.set("tpaaccept", List.of("tpaaccept"));
        var overrides = new YamlConfiguration();
        overrides.set("tp", List.of("jump", "travel"));
        overrides.set("tpaaccept", List.of("yesplease"));
        var names = new CommandNames(defaults, overrides);
        var replacements = mock(CommandReplacements.class);
        names.register(replacements);
        verify(replacements).addReplacement("cmd-tp", "jump|travel");
        assertEquals("<click:run_command:'/yesplease'>/jump</click> https://example.org/tp",
                names.inMessage("<click:run_command:'/tpaaccept'>/tp</click> https://example.org/tp"));
    }

    @Test
    void disabledHubPreservesExistingHubAliasOnAnotherCommand() {
        var defaults = new YamlConfiguration();
        defaults.set("spawn", List.of("spawn"));
        defaults.set("hub", List.of("hub"));
        var overrides = new YamlConfiguration();
        overrides.set("spawn", List.of("hub"));
        assertEquals("hub", new CommandNames(defaults, overrides, false).primary("spawn"));
        assertThrows(IllegalArgumentException.class, () -> new CommandNames(defaults, overrides, true));
    }

    @Test
    void duplicateLabelsConflictWithUnmodifiedDefaults() {
        var defaults = new YamlConfiguration();
        defaults.set("tp", List.of("tp"));
        defaults.set("home", List.of("home"));
        var overrides = new YamlConfiguration();
        overrides.set("tp", List.of("home"));
        assertThrows(IllegalArgumentException.class, () -> new CommandNames(defaults, overrides));
    }
}
