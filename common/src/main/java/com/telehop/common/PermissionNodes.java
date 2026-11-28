package com.telehop.common;

/**
 * Central registry of all TeleHop permission node strings.
 * Keeps permission checks type-safe and refactor-friendly.
 */
public final class PermissionNodes {
    public static final String TPA = "telehop.tpa";
    public static final String TPA_HERE = "telehop.tpahere";
    public static final String TPA_ACCEPT = "telehop.tpa.accept";
    public static final String TPA_DENY = "telehop.tpa.deny";
    public static final String TPA_CANCEL = "telehop.tpa.cancel";
    public static final String TPA_TOGGLE = "telehop.tpa.toggle";
    public static final String RTP = "telehop.rtp";
    public static final String WARP = "telehop.warp";
    public static final String SPAWN = "telehop.spawn";
    public static final String TP = "telehop.tp";
    public static final String TP_COORDINATES_SELF = "telehop.tp.coordinates.self";
    public static final String TP_COORDINATES_OTHERS = "telehop.tp.coordinates.others";
    public static final String TPHERE = "telehop.tphere";
    public static final String ADMIN = "telehop.admin";
    public static final String PWARP = "telehop.pwarp";
    public static final String WARP_LIMIT_PREFIX = "telehop.warps.";
    public static final String RTP_BYPASS_COOLDOWN = "telehop.rtp.bypasscooldown";
    public static final String RTP_BYPASS_DELAY = "telehop.rtp.bypassdelay";
    public static final String TPA_BYPASS_COOLDOWN = "telehop.tpa.bypasscooldown";
    public static final String HOMES = "telehop.homes";
    public static final String HOMES_UNLIMITED = "telehop.homes.unlimited";
    public static final String HOME_LIMIT_PREFIX = "telehop.homes.";
    public static final String SETHOME = "telehop.sethome";
    public static final String DELHOME = "telehop.delhome";
    public static final String LAST_LOCATION = "telehop.lastlocation";
    public static final String BACK = "telehop.back";
    public static final String BACK_DEATH = "telehop.back.death";

    public static final String ADMIN_SPAWN_LIST = "telehop.admin.spawn.list";
    public static final String ADMIN_SPAWN_SET = "telehop.admin.spawn.set";
    public static final String ADMIN_SPAWN_DELETE = "telehop.admin.spawn.delete";
    public static final String ADMIN_HOMES_LIST = "telehop.admin.homes.list";
    public static final String ADMIN_HOMES_VISIT = "telehop.admin.homes.visit";
    public static final String ADMIN_HOMES_SET = "telehop.admin.homes.set";
    public static final String ADMIN_HOMES_DELETE = "telehop.admin.homes.delete";
    public static final String ADMIN_LASTLOCATION_VIEW = "telehop.admin.lastlocation.view";
    public static final String ADMIN_LASTLOCATION_VISIT = "telehop.admin.lastlocation.visit";
    public static final String ADMIN_LASTLOCATION_CLEAR = "telehop.admin.lastlocation.clear";
    public static final String ADMIN_WARPS_SET = "telehop.admin.warps.set";
    public static final String ADMIN_WARPS_LIST = "telehop.admin.warps.list";
    public static final String ADMIN_WARPS_DELETE = "telehop.admin.warps.delete";
    public static final String ADMIN_PLAYERWARPS_DELETE = "telehop.admin.playerwarps.delete";
    public static final String ADMIN_PLAYERINFO = "telehop.admin.playerinfo";
    public static final String ADMIN_RELOAD = "telehop.admin.reload";
    public static final String ADMIN_PERMISSIONS = "telehop.admin.permissions";
    public static final String HUB = "telehop.hub";
    public static final String SPAWN_NAMED = "telehop.spawn.named";
    public static final String SPAWN_DESTINATION = "telehop.spawn.destination.";
    public static final String TP_PLAYER_SELF = "telehop.tp.player.self";
    public static final String TP_PLAYER_OTHERS = "telehop.tp.player.others";

    public static final String COMMAND_PREFIX = "telehop.command.";
    public static final String ADMIN_COMMANDS_LIST = "telehop.admin.commands.list";
    public static final String ADMIN_COMMANDS_VALIDATE = "telehop.admin.commands.validate";
    public static final String ADMIN_COMMANDS_RELOAD = "telehop.admin.commands.reload";

    public static String rtpDestination(String scope, String id) {
        // Encode administrators' real world and backend names into a single, printable permission suffix.
        String suffix = java.net.URLEncoder.encode(id.toLowerCase(java.util.Locale.ROOT), java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20").replace("*", "%2a").replace(".", "%2e").toLowerCase(java.util.Locale.ROOT);
        return "telehop.rtp." + scope + "." + suffix;
    }

    public static final String ADMIN_CONFIG_VALIDATE = "telehop.admin.config.validate";
    public static final String ADMIN_PLATFORM_INFO = "telehop.admin.platform.info";
    public static final String ADMIN_MIGRATION_STATUS = "telehop.admin.migration.status";
    public static final String ADMIN_DIAGNOSTICS = "telehop.admin.diagnostics";

    private PermissionNodes() {
    }

    public static String warpNode(String warpName) {
        return "telehop.warp." + warpName.toLowerCase(java.util.Locale.ROOT);
    }
}
