package com.mrgregles.bsp_mod_fixes.fix;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The list of fixes this build carries, so the log says once at startup what is in place. A fix with event handlers registers
 * itself from {@link #registerEvents()}; a fix made of a mixin is recorded by the mixin config plugin the moment the mixin is
 * applied to its target ({@link #mixinApplied}). Targets that the patched mods use at setup (packet handlers, event handlers)
 * are patched before the report; module classes load the first time such a module exists in a world, so the report says that
 * those fixes apply when their target loads, and the application is logged then. Ids are the F-n of the vault's Fix Log.
 */
public final class Fixes {
    /** The fixes' own logger, usable from the mixin plugin while classes are still being transformed. */
    public static final Logger LOGGER = LogUtils.getLogger();

    /** One fix: its id (F-n in the Fix Log), what it does, and the mod it patches. */
    public record Fix(String id, String what, String targetMod) {}

    /** The fix each mixin class (simple name) stands for. */
    private static final Map<String, Fix> MIXIN_FIXES = Map.of(
            "CreativeInstallGuardMixin", new Fix(CreativeInstallGuard.ID, CreativeInstallGuard.WHAT, CreativeInstallGuard.TARGET),
            "TweakClampMixin", new Fix(TweakClamp.ID, TweakClamp.WHAT, TweakClamp.TARGET),
            "NuminaTickPhaseMixin", new Fix(TickPhaseFilter.ID, TickPhaseFilter.WHAT, TickPhaseFilter.TARGET),
            "PowersuitsLivingEventMixin", new Fix(LivingEventFilter.ID, LivingEventFilter.WHAT, LivingEventFilter.TARGET),
            "NightVisionTimingMixin", new Fix(NightVisionTiming.ID, NightVisionTiming.WHAT, NightVisionTiming.TARGET),
            "KineticGeneratorMixin", new Fix(KineticGenerator.ID, KineticGenerator.WHAT, KineticGenerator.TARGET),
            "MovementManagerFallMixin", new Fix(ShockAbsorber.ID, ShockAbsorber.WHAT, ShockAbsorber.TARGET),
            "EnergyShieldArmorMixin", new Fix(EnergyShield.ID, EnergyShield.WHAT, EnergyShield.TARGET),
            "EnergyShieldTickerMixin", new Fix(EnergyShield.ID, EnergyShield.WHAT, EnergyShield.TARGET),
            "NuminaFireClearMixin", new Fix(FireImmunityToggle.ID, FireImmunityToggle.WHAT, FireImmunityToggle.TARGET));

    private static final List<Fix> ACTIVE = Collections.synchronizedList(new ArrayList<>());
    private static volatile boolean reported;

    private Fixes() {}

    /** Wires the event handlers of every fix that has some, and records the fixes made of resources. Called once from the mod constructor. */
    public static void registerEvents() {
        active(Lang.ID, Lang.WHAT, Lang.TARGET);                     // F-9, the lang files in this jar
        MinecraftForge.EVENT_BUS.addListener(ShockAbsorber::onFall); // F-7, whose mixin cancels Powersuits' handler
        MinecraftForge.EVENT_BUS.addListener(EnergyShield::onHurt);  // F-8 (c)
    }

    /** Called by the mixin config plugin after a mixin of bsp_mod_fixes.mixins.json has been applied. */
    public static void mixinApplied(String mixinClassName, String targetClassName) {
        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        Fix fix = MIXIN_FIXES.get(simpleName);
        if (fix == null) {
            LOGGER.warn("BSP-Mod Fixes: mixin {} was applied to {} but no fix is recorded for it", simpleName, targetClassName);
            return;
        }
        if (reported) {
            LOGGER.info("BSP-Mod Fixes: {} applied to {} (first use of its target)", fix.id(), targetClassName);
        } else {
            LOGGER.debug("BSP-Mod Fixes: {} applied to {}", fix.id(), targetClassName);
        }
        active(fix.id(), fix.what(), fix.targetMod());
    }

    /** Records a fix as active (a second call with the same id is ignored). */
    public static void active(String id, String what, String targetMod) {
        synchronized (ACTIVE) {
            if (ACTIVE.stream().noneMatch(f -> f.id().equals(id))) {
                ACTIVE.add(new Fix(id, what, targetMod));
            }
        }
    }

    /** The fixes recorded so far, by id (mixins are recorded in whatever order their targets load). */
    public static List<Fix> active() {
        synchronized (ACTIVE) {
            return ACTIVE.stream().sorted(Comparator.comparing(Fix::id, Fixes::byNumber)).toList();
        }
    }

    /** F-2 before F-10. */
    private static int byNumber(String a, String b) {
        return Integer.compare(Integer.parseInt(a.substring(2)), Integer.parseInt(b.substring(2)));
    }

    /** Every fix this build knows: the applied ones first, then the mixins whose target has not loaded yet, by id. */
    public static List<Fix> known() {
        List<Fix> all = new ArrayList<>(active());
        for (Fix f : MIXIN_FIXES.values()) {
            if (all.stream().noneMatch(a -> a.id().equals(f.id()))) {
                all.add(f);
            }
        }
        return all.stream().sorted(Comparator.comparing(Fix::id, Fixes::byNumber)).toList();
    }

    /** One log line per fix, once loading is complete (the patched mods register their packets during common setup). */
    public static void report() {
        List<Fix> fixes = known();
        List<Fix> applied = active();
        reported = true;
        if (fixes.isEmpty()) {
            LOGGER.info("BSP-Mod Fixes: no fixes are wired up in this build");
            return;
        }
        LOGGER.info("BSP-Mod Fixes: {} fixes in this build, {} already applied", fixes.size(), applied.size());
        for (Fix f : fixes) {
            LOGGER.info("  {} ({}): {}{}", f.id(), f.targetMod(), f.what(), applied.contains(f) ? "" : " [applies when its target class loads]");
        }
    }
}
