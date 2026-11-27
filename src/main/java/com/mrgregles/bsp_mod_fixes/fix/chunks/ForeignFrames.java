package com.mrgregles.bsp_mod_fixes.fix.chunks;

import java.util.ArrayList;
import java.util.List;

/**
 * The frames of a stack that belong to neither Minecraft, Forge, the JDK, the mod loader nor this mod's fixes: the ones that
 * name the mod making a call. This mod's GameTest package is kept, so a test sees itself as the caller. Pure; shared by F-51
 * and F-52.
 */
final class ForeignFrames {
    private static final String[] SKIPPED_PREFIXES = {
            "net.minecraft.", "net.minecraftforge.", "java.", "jdk.", "sun.", "com.mojang.", "org.spongepowered.", "cpw.mods.",
            "com.mrgregles.bsp_mod_fixes.fix.", "com.mrgregles.bsp_mod_fixes.mixin."
    };

    private ForeignFrames() {}

    /** Whether a frame of this class is kept. */
    static boolean foreign(String className) {
        for (String prefix : SKIPPED_PREFIXES) {
            if (className.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    /** Up to {@code max} kept frames, outermost call last, each as {@code StackTraceElement.toString()} prints it. */
    static List<String> of(StackTraceElement[] stack, int max) {
        List<String> out = new ArrayList<>();
        for (StackTraceElement frame : stack) {
            if (out.size() >= max) {
                break;
            }
            if (foreign(frame.getClassName())) {
                out.add(frame.toString());
            }
        }
        return out;
    }

    /** The innermost kept frame's class, or the given fallback when every frame is skipped. */
    static String firstClass(StackTraceElement[] stack, String fallback) {
        for (StackTraceElement frame : stack) {
            if (foreign(frame.getClassName())) {
                return frame.getClassName();
            }
        }
        return fallback;
    }
}
