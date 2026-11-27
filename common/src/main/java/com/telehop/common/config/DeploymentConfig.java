package com.telehop.common.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/** Schema and deployment choices independent of the detected Minecraft implementation. */
public record DeploymentConfig(Mode mode, boolean customNames, boolean additionalCommands,
                               boolean automaticMigration, boolean backups, int retention,
                               boolean debug, boolean logValidation, boolean logMigration, boolean logPlatform) {
    public enum Mode { SINGLE, MULTI, CUSTOM }

    public Path profileDirectory(Path dataDirectory) {
        return dataDirectory.resolve("config").resolve(mode.name().toLowerCase(Locale.ROOT));
    }

    public static DeploymentConfig read(Path dataDirectory) throws IOException {
        return parse(ConfigYaml.read(dataDirectory.resolve("config.yml")));
    }

    public static DeploymentConfig parse(Map<String, Object> document) {
        if (!Integer.valueOf(1).equals(document.getOrDefault("config-version", 1))) {
            throw new IllegalArgumentException("Unsupported config-version in config.yml; restore a matching plugin and configuration");
        }
        Mode selected;
        try { selected = Mode.valueOf(String.valueOf(document.get("mode")).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("config.yml: choose SINGLE, MULTI or CUSTOM for mode"); }
        if (!"AUTO".equals(ConfigYaml.get(document, "platform.detection", "AUTO"))) {
            throw new IllegalArgumentException("config.yml: platform.detection must be AUTO");
        }
        Object count = ConfigYaml.get(document, "migration.backup-retention", 5);
        if (!(count instanceof Integer retention) || retention < 1 || retention > 100) {
            throw new IllegalArgumentException("migration.backup-retention must be between 1 and 100");
        }
        return new DeploymentConfig(selected, ConfigYaml.flag(document, "commands.custom-names.enabled", false),
                ConfigYaml.flag(document, "commands.additional-commands.enabled", false),
                ConfigYaml.flag(document, "migration.automatic", true),
                ConfigYaml.flag(document, "migration.backup-before-update", true), retention,
                ConfigYaml.flag(document, "logging.debug", false), ConfigYaml.flag(document, "logging.configuration-validation", true),
                ConfigYaml.flag(document, "logging.migration", true), ConfigYaml.flag(document, "logging.platform-detection", true));
    }

    public static String initial(Mode selected, boolean customNames) {
        return """
                # TeleHop deployment settings. Stop the server before switching profiles.
                config-version: 1
                mode: %s
                platform:
                  detection: AUTO
                commands:
                  custom-names:
                    enabled: %s
                  additional-commands:
                    enabled: false
                migration:
                  automatic: true
                  backup-before-update: true
                  backup-retention: 5
                logging:
                  debug: false
                  configuration-validation: true
                  migration: true
                  platform-detection: true
                """.formatted(selected, customNames);
    }
}
