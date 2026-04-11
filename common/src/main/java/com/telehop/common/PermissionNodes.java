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
    public static final String TPHERE = "telehop.tphere";
    public static final String ADMIN = "telehop.admin";
    public static final String PWARP = "telehop.pwarp";
    public static final String WARP_LIMIT_PREFIX = "telehop.warps.";
    public static final String RTP_BYPASS_COOLDOWN = "telehop.rtp.bypasscooldown";
    public static final String RTP_BYPASS_DELAY = "telehop.rtp.bypassdelay";
    public static final String TPA_BYPASS_COOLDOWN = "telehop.tpa.bypasscooldown";
    public static final String HOMES = "telehop.homes";
    public static final String HOME_LIMIT_PREFIX = "telehop.homes.";
    public static final String BACK = "telehop.back";
    public static final String BACK_DEATH = "telehop.back.death";

    private PermissionNodes() {
    }

    public static String warpNode(String warpName) {
        return "telehop.warp." + warpName.toLowerCase();
    }
}
