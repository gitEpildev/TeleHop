package com.telehop.paper.service;

import org.bukkit.plugin.java.JavaPlugin;

public class AuditLogger {
    private final JavaPlugin plugin;
    private final boolean enabled;

    public AuditLogger(JavaPlugin plugin, boolean enabled) {
        this.plugin = plugin;
        this.enabled = enabled;
    }

    public void log(String message) {
        if (enabled) {
            plugin.getLogger().info("[AUDIT] " + message);
        }
    }
}
