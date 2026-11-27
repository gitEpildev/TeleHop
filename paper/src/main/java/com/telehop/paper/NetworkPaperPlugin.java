package com.telehop.paper;

import com.telehop.paper.config.CommandNames;
import com.telehop.paper.config.PaperSettings;
import com.telehop.paper.platform.BackendPlatform;
import com.telehop.paper.platform.PlatformText;
import com.telehop.paper.service.ServiceRegistry;
import com.telehop.paper.version.VersionAdapter;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * Plugin entry point. Lifecycle only. All wiring lives in {@link Bootstrap},
 * all services in {@link ServiceRegistry}.
 */
public class NetworkPaperPlugin extends JavaPlugin {
    private BackendPlatform platform;
    private ServiceRegistry services;
    private VersionAdapter versionAdapter;
    private CommandNames commandNames;
    private com.telehop.common.config.DeploymentConfig configuration;

    public com.telehop.common.config.DeploymentConfig configuration() { return configuration; }
    void setConfiguration(com.telehop.common.config.DeploymentConfig value) { configuration = value; }
    public java.nio.file.Path profileDirectory() { return configuration.profileDirectory(getDataFolder().toPath()); }

    @Override
    public void onEnable() {
        platform = BackendPlatform.detect(this);
        PlatformText.initialise(this);
        services = Bootstrap.init(this);
    }

    @Override
    public void onDisable() {
        try {
            Bootstrap.shutdown(this, services);
        } finally {
            PlatformText.shutdown();
        }
    }

    // ── public API used by commands, listeners, and the packet handler ──

    public BackendPlatform platform() { return platform; }
    public ServiceRegistry services() { return services; }
    public VersionAdapter versionAdapter() { return versionAdapter; }
    void setVersionAdapter(VersionAdapter adapter) { this.versionAdapter = adapter; }

    public CommandNames commandNames() { return commandNames; }
    void setCommandNames(CommandNames names) { this.commandNames = names; }

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

    /** Bukkit executor for completing player-facing work after an asynchronous storage operation. */
    public void onMainThread(Runnable task) {
        getServer().getScheduler().runTask(this, task);
    }

    public Component msg(String key) {
        return services.messageService().format(key);
    }

    public Component msg(String key, Map<String, String> replacements) {
        return services.messageService().format(key, replacements);
    }

    public boolean isFeatureEnabled(String feature) {
        return services.settings().isFeatureEnabled(feature);
    }

    private final java.util.concurrent.atomic.AtomicBoolean reloading = new java.util.concurrent.atomic.AtomicBoolean();
    public boolean reloadInProgress() { return reloading.get(); }

    public java.util.concurrent.CompletableFuture<Void> reload() {
        if (!reloading.compareAndSet(false, true)) throw new IllegalArgumentException("A configuration reload is already running");
        try { return Bootstrap.reload(this, services).whenComplete((ok, error) -> reloading.set(false)); }
        catch (RuntimeException error) { reloading.set(false); throw error; }
    }
}
