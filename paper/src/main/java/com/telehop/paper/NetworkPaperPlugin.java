package com.telehop.paper;

import com.telehop.paper.config.PaperSettings;
import com.telehop.paper.service.ServiceRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * Plugin entry point. Lifecycle only — all wiring lives in {@link Bootstrap},
 * all services in {@link ServiceRegistry}.
 */
public class NetworkPaperPlugin extends JavaPlugin {
    private ServiceRegistry services;

    @Override
    public void onEnable() {
        services = Bootstrap.init(this);
    }

    @Override
    public void onDisable() {
        Bootstrap.shutdown(services);
    }

    // ── public API used by commands, listeners, and the packet handler ──

    public ServiceRegistry services() { return services; }

    public PaperSettings settings()                                          { return services.settings(); }
    public com.telehop.paper.service.MessageService messageService()         { return services.messageService(); }
    public com.telehop.paper.service.PermissionService permissionService()   { return services.permissionService(); }
    public com.telehop.paper.service.AuditLogger auditLogger()              { return services.auditLogger(); }
    public com.telehop.common.service.PlayerService playerService()          { return services.playerService(); }
    public com.telehop.common.service.WarpService warpService()              { return services.warpService(); }
    public com.telehop.common.service.PlayerWarpService playerWarpService()  { return services.playerWarpService(); }
    public com.telehop.common.service.TpaService tpaService()                { return services.tpaService(); }
    public com.telehop.paper.service.RtpManager rtpManager()                 { return services.rtpManager(); }
    public com.telehop.paper.service.TpaRuntimeManager tpaRuntimeManager()   { return services.tpaRuntimeManager(); }
    public com.telehop.paper.messaging.PaperMessagingManager messaging()     { return services.messaging(); }

    public Component msg(String key) {
        return services.messageService().format(key);
    }

    public Component msg(String key, Map<String, String> replacements) {
        return services.messageService().format(key, replacements);
    }

    public boolean isFeatureEnabled(String feature) {
        return services.settings().isFeatureEnabled(feature);
    }

    public void reload() {
        Bootstrap.reload(this, services);
    }
}
