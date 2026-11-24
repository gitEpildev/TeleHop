package com.telehop.paper.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnTranslationsTest {
    @ParameterizedTest
    @ValueSource(strings = {"en", "de", "es", "nl", "pl", "zh"})
    void spawnMessagesAndTheirPlaceholdersAreBundled(String language) throws Exception {
        var english = language("en");
        var translated = language(language);
        for (String key : english.getKeys(false)) {
            if (!(key.startsWith("spawn-") || key.startsWith("homes-") || key.endsWith("spawn-routes")
                    || key.equals("reload-invalid"))) continue;
            assertTrue(translated.isString(key), language + ": " + key);
            for (String token : List.of("<name>", "<count>", "<server>", "<revision>")) {
                assertEquals(english.getString(key).contains(token), translated.getString(key).contains(token), key);
            }
        }
    }

    private YamlConfiguration language(String code) throws Exception {
        try (var reader = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(
                "/languages/" + code + ".yml")), StandardCharsets.UTF_8)) {
            var yaml = new YamlConfiguration();
            yaml.load(reader);
            return yaml;
        }
    }
}
