package com.mrgregles.bsp_mod_fixes.fix;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;

import javax.annotation.Nullable;

/**
 * F-1 (Modular Powersuits 2.3.13): the creative-install packet puts a fully charged module into a worn modular item for free.
 * Powersuits' server handler never checks that the sender is in creative mode, so a modified client can install any module
 * in survival. The guard lets the request through only for a creative sender and logs a WARN naming the player otherwise.
 * The mixin cancels the handler when {@link #allows} says no; the handler's own code is untouched.
 */
public final class CreativeInstallGuard {
    public static final String ID = "F-1";
    public static final String WHAT = "creative-install packet only works for a sender in creative mode";
    public static final String TARGET = "Modular Powersuits 2.3.13";

    private CreativeInstallGuard() {}

    /**
     * Whether a creative-install request may be carried out. The check runs where the packet arrives, before the handler
     * queues its work, so {@code sender} may be null when the connection has no player; that is refused too.
     */
    public static boolean allows(@Nullable ServerPlayer sender, EquipmentSlot slot, ResourceLocation module) {
        if (sender != null && sender.isCreative()) {
            return true;
        }
        String name = sender == null ? "a connection without a player" : sender.getGameProfile().getName();
        Fixes.warnThrottled(ID + ":" + name, "{}: refused a creative-install request from {} (not in creative mode) for module {} into the {} slot",
                ID, name, module, slot.getName());
        return false;
    }
}
