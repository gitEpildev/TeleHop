package com.telehop.paper.service;

import com.telehop.paper.config.PaperSettings;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Plays configurable particle and sound effects at teleport origin and destination.
 */
public final class TeleportEffectPlayer {
    private final PaperSettings settings;

    public TeleportEffectPlayer(PaperSettings settings) {
        this.settings = settings;
    }

    /**
     * @param player the teleporting player
     * @param from   origin location (may be null for cross-server arrivals)
     * @param to     destination location
     * @param type   one of: spawn, tpa, rtp, warp, home, back
     */
    public void play(Player player, Location from, Location to, String type) {
        PaperSettings.TeleportEffect effect = settings.effectFor(type);

        if (effect.particleEnabled() && effect.particleType() != null) {
            if (from != null && from.getWorld() != null && from.getWorld().equals(to.getWorld())) {
                from.getWorld().spawnParticle(effect.particleType(), from.clone().add(0, 1, 0),
                        effect.particleCount(), 0.5, 1.0, 0.5, 0.02);
            }
            if (to.getWorld() != null) {
                to.getWorld().spawnParticle(effect.particleType(), to.clone().add(0, 1, 0),
                        effect.particleCount(), 0.5, 1.0, 0.5, 0.02);
            }
        }

        if (effect.soundEnabled() && effect.soundType() != null) {
            player.playSound(to, effect.soundType(), effect.soundVolume(), effect.soundPitch());
        }
    }
}
