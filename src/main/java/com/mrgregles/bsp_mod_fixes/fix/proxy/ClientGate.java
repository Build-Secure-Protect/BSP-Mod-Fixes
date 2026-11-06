package com.mrgregles.bsp_mod_fixes.fix.proxy;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.Set;

/**
 * The gate for the mixins that target a client-only class ({@code net.minecraft.client.Minecraft} for F-17). The mixin
 * config is {@code required}, so a target Mixin cannot find would stop a dedicated server at startup; the mixin config
 * plugin asks this gate first and skips those mixins on a dedicated server before Mixin looks their target up, recording
 * the skip for the startup report. Pure {@link #applies(boolean)} for the tests.
 */
public final class ClientGate {
    /** The mixins of bsp_mod_fixes.mixins.json whose target class exists on a client only (simple names). */
    public static final Set<String> MIXINS = Set.of("DisconnectedScreenThreadMixin");
    public static final String SERVER_REASON = "client-side only, nothing to patch on a dedicated server";

    private ClientGate() {}

    /** Whether the gate decides for this mixin (simple name). */
    public static boolean gates(String mixinSimpleName) {
        return MIXINS.contains(mixinSimpleName);
    }

    /** Whether a client-only mixin applies: on a client (including its single-player server), never on a dedicated server. */
    public static boolean applies(boolean dedicatedServer) {
        return !dedicatedServer;
    }

    /** Called by the mixin config plugin for each client-only mixin: whether to apply it, recording a skip for the startup report. */
    public static boolean allows(String mixinSimpleName) {
        boolean applies = applies(FMLEnvironment.dist.isDedicatedServer());
        if (!applies) {
            Fixes.mixinSkipped(mixinSimpleName, SERVER_REASON);
        }
        return applies;
    }
}
