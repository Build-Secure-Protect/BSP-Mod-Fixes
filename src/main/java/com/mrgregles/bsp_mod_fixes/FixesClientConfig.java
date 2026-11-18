package com.mrgregles.bsp_mod_fixes;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client config, {@code bsp_mod_fixes-client.toml} in the game's config folder: what the player chose on their own screen.
 * Documented in docs/CONFIG.md. Registered on both sides (Forge ignores a client config on a dedicated server).
 */
public final class FixesClientConfig {
    public static final ForgeConfigSpec SPEC;
    /** D51: whether the quantum suit readout (F-25) is collapsed to its tab. Set by the key or the tab, saved at once. */
    public static final ForgeConfigSpec.BooleanValue QUANTUM_READOUT_COLLAPSED;
    /** F-30: whether Compact Machines' room preview releases its memory when it closes. */
    public static final ForgeConfigSpec.BooleanValue FREE_PREVIEW_MEMORY;
    /** F-37: whether EMI's JEI transfer layout clamps a shaped recipe's input bounds to the list it has. */
    public static final ForgeConfigSpec.BooleanValue CLAMP_JEI_TRANSFER_SLOTS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("quantumReadout");
        QUANTUM_READOUT_COLLAPSED = b.comment(
                "The quantum suit readout (the plate above the hotbar's left end while AdvancedAE's quantum armor is worn) is",
                "collapsed to its tab. Changed by the readout's key binding or by clicking the tab while a screen is open; kept",
                "until changed again, through taking the armor off and on and across sessions.")
                .define("collapsed", false);
        b.pop();
        b.push("compactMachines");
        FREE_PREVIEW_MEMORY = b.comment(
                "Compact Machines' room preview (right click on a machine with an empty hand) keeps most of the video and native",
                "memory it takes for the rest of the session, more with every preview and every window resize while one is open.",
                "true releases it when the preview closes; false leaves Compact Machines' behaviour as it is. Only read while",
                "exactly Compact Machines 6.0.3 is installed.")
                .define("freePreviewMemory", true);
        b.pop();
        b.push("emi");
        CLAMP_JEI_TRANSFER_SLOTS = b.comment(
                "EMI lays a shaped crafting recipe out for a JEI transfer handler (Refined Storage's grid, Mekanism's QIO crafting",
                "window and Formulaic Assemblicator) as if it always had nine inputs; a shaped recipe of another mod's class with",
                "fewer (Mekanism's Laser Tractor Beam, EnderStorage's recolour recipes, Quark's dirty glass) crashes the game to",
                "the desktop when viewed with one of those screens open. true holds EMI's bounds within the recipe's input list;",
                "false leaves EMI's behaviour as it is. Only read while exactly EMI 1.1.24+1.20.1+forge is installed.")
                .define("clampJeiTransferSlots", true);
        b.pop();
        SPEC = b.build();
    }

    private FixesClientConfig() {}

    /** Whether the readout is collapsed, false before the config has loaded. */
    public static boolean collapsed() {
        return SPEC.isLoaded() && QUANTUM_READOUT_COLLAPSED.get();
    }

    /** Whether the room preview's memory is released on close (F-30); true before the config has loaded. */
    public static boolean freePreviewMemory() {
        return !SPEC.isLoaded() || FREE_PREVIEW_MEMORY.get();
    }

    /** Whether EMI's JEI transfer layout bounds are clamped (F-37); true before the config has loaded. */
    public static boolean clampJeiTransferSlots() {
        return !SPEC.isLoaded() || CLAMP_JEI_TRANSFER_SLOTS.get();
    }

    /** Sets and saves the collapsed state. */
    public static void setCollapsed(boolean collapsed) {
        if (SPEC.isLoaded()) {
            QUANTUM_READOUT_COLLAPSED.set(collapsed);
            SPEC.save();
        }
    }
}
