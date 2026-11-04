package com.mrgregles.bsp_mod_fixes.fix;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * F-2 (Numina 2.3.13): a module tweak request carries a raw double that the server stores unchecked, and Powersuits' module
 * properties scale linearly with it. The client's slider only ever sends 0..1, so anything else comes from a modified client
 * and is clamped to that range before the handler runs; NaN and infinities become 0 (no bonus). A WARN names the player.
 */
public final class TweakClamp {
    public static final String ID = "F-2";
    public static final String WHAT = "module tweak values are clamped to 0..1 (NaN and infinities become 0)";
    public static final String TARGET = "Numina 2.3.13";

    private TweakClamp() {}

    /** True for the values a stock client can send: finite and within 0..1. False for NaN. */
    public static boolean inRange(double value) {
        return value >= 0.0 && value <= 1.0;
    }

    /** The value the server stores instead: unchanged when in range, the nearer bound otherwise, 0 when not finite. */
    public static double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    public static void warn(@Nullable ServerPlayer sender, ResourceLocation module, String tweak, double raw, double clamped) {
        String name = sender == null ? "a connection without a player" : sender.getGameProfile().getName();
        Fixes.warnThrottled(ID + ":" + name, "{}: clamped tweak '{}' on module {} from {} to {} (sent by {})", ID, tweak, module, raw, clamped, name);
    }
}
