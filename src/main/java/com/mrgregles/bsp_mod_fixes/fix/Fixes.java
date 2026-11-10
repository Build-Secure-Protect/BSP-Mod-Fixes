package com.mrgregles.bsp_mod_fixes.fix;

import com.mojang.logging.LogUtils;
import com.mrgregles.bsp_mod_fixes.fix.aae.QuantumAbsorption;
import com.mrgregles.bsp_mod_fixes.fix.cm.CmGate;
import com.mrgregles.bsp_mod_fixes.fix.cm.LoadSaves;
import com.mrgregles.bsp_mod_fixes.fix.cm.PreviewLeak;
import com.mrgregles.bsp_mod_fixes.fix.cm.PreviewRules;
import com.mrgregles.bsp_mod_fixes.fix.cm.RoomHolds;
import com.mrgregles.bsp_mod_fixes.fix.cm.RoomLoader;
import com.mrgregles.bsp_mod_fixes.fix.emi.AmountLine;
import com.mrgregles.bsp_mod_fixes.fix.emi.EmiGate;
import com.mrgregles.bsp_mod_fixes.fix.emi.ExtrasFix;
import com.mrgregles.bsp_mod_fixes.fix.emi.NullStack;
import com.mrgregles.bsp_mod_fixes.fix.emi.TextWidgets;
import com.mrgregles.bsp_mod_fixes.fix.emi.TooltipBuilderFix;
import com.mrgregles.bsp_mod_fixes.fix.gobber.DragonFlightSends;
import com.mrgregles.bsp_mod_fixes.fix.jei.JeiStartGate;
import com.mrgregles.bsp_mod_fixes.fix.proxy.DeferredDisconnectScreen;
import com.mrgregles.bsp_mod_fixes.fix.proxy.StalePacketDrop;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The list of fixes this build carries, so the log says once at startup what is in place. A fix with event handlers registers
 * itself from {@link #registerEvents()}; a fix made of a mixin is recorded by the mixin config plugin the moment the mixin is
 * applied to its target ({@link #mixinApplied}). Targets that the patched mods use at setup (packet handlers, event handlers)
 * are patched before the report; module classes load the first time such a module exists in a world, so the report says that
 * those fixes apply when their target loads, and the application is logged then. The EMI fixes are gated by the plugin
 * ({@link EmiGate}) and are reported as skipped, with the reason, when EMI is absent or another version; so is the JEI fix
 * ({@code fix/jei/JeiGate}) without a JEI 15.74 build, the AdvancedAE fix ({@code fix/aae/AaeGate}) without AdvancedAE at
 * the pinned version, the Gobber fix ({@code fix/gobber/GobberGate}) without Gobber 2 at its pinned version, the Compact Machines fixes ({@code fix/cm/CmGate}) without Compact Machines at its pinned version, and the fixes into client-only classes on a dedicated server ({@code fix/proxy/ClientGate}). Ids are
 * the F-n of the vault's Fix Log.
 */
public final class Fixes {
    /** The fixes' own logger, usable from the mixin plugin while classes are still being transformed. */
    public static final Logger LOGGER = LogUtils.getLogger();

    /** One fix: its id (F-n in the Fix Log), what it does, and the mod it patches. */
    public record Fix(String id, String what, String targetMod) {}

    /** The fix each mixin class (simple name) stands for. */
    private static final Map<String, Fix> MIXIN_FIXES = Map.ofEntries(
            Map.entry("CreativeInstallGuardMixin", new Fix(CreativeInstallGuard.ID, CreativeInstallGuard.WHAT, CreativeInstallGuard.TARGET)),
            Map.entry("TweakClampMixin", new Fix(TweakClamp.ID, TweakClamp.WHAT, TweakClamp.TARGET)),
            Map.entry("NuminaTickPhaseMixin", new Fix(TickPhaseFilter.ID, TickPhaseFilter.WHAT, TickPhaseFilter.TARGET)),
            Map.entry("PowersuitsLivingEventMixin", new Fix(LivingEventFilter.ID, LivingEventFilter.WHAT, LivingEventFilter.TARGET)),
            Map.entry("NightVisionTimingMixin", new Fix(NightVisionTiming.ID, NightVisionTiming.WHAT, NightVisionTiming.TARGET)),
            Map.entry("KineticGeneratorMixin", new Fix(KineticGenerator.ID, KineticGenerator.WHAT, KineticGenerator.TARGET)),
            Map.entry("MovementManagerFallMixin", new Fix(ShockAbsorber.ID, ShockAbsorber.WHAT, ShockAbsorber.TARGET)),
            Map.entry("EnergyShieldArmorMixin", new Fix(EnergyShield.ID, EnergyShield.WHAT, EnergyShield.TARGET)),
            Map.entry("EnergyShieldTickerMixin", new Fix(EnergyShield.ID, EnergyShield.WHAT, EnergyShield.TARGET)),
            Map.entry("NuminaFireClearMixin", new Fix(FireImmunityToggle.ID, FireImmunityToggle.WHAT, FireImmunityToggle.TARGET)),
            Map.entry("EmiNullStackMixin", new Fix(NullStack.ID, NullStack.WHAT, NullStack.TARGET)),
            Map.entry("EmiTooltipBuilderMixin", new Fix(TooltipBuilderFix.ID, TooltipBuilderFix.WHAT, TooltipBuilderFix.TARGET)),
            Map.entry("EmiSlotTooltipMixin", new Fix(AmountLine.ID, AmountLine.WHAT, AmountLine.TARGET)),
            Map.entry("EmiRecipeExtrasMixin", new Fix(ExtrasFix.ID, ExtrasFix.WHAT, ExtrasFix.TARGET)),
            Map.entry("EmiPlaceableMixin", new Fix(ExtrasFix.ID, ExtrasFix.WHAT, ExtrasFix.TARGET)),
            Map.entry("EmiTextWidgetMixin", new Fix(TextWidgets.ID, TextWidgets.WHAT, TextWidgets.TARGET)),
            Map.entry("ConnectionStalePacketMixin", new Fix(StalePacketDrop.ID, StalePacketDrop.WHAT, StalePacketDrop.TARGET)),
            Map.entry("DisconnectedScreenThreadMixin", new Fix(DeferredDisconnectScreen.ID, DeferredDisconnectScreen.WHAT, DeferredDisconnectScreen.TARGET)),
            Map.entry("JeiStartObserverMixin", new Fix(JeiStartGate.ID, JeiStartGate.WHAT, JeiStartGate.TARGET)),
            Map.entry("GliderFlyingSpeedMixin", new Fix(FlyingSpeedKeeper.ID, FlyingSpeedKeeper.WHAT, FlyingSpeedKeeper.TARGET)),
            Map.entry("SprintAssistFlyingSpeedMixin", new Fix(FlyingSpeedKeeper.ID, FlyingSpeedKeeper.WHAT, FlyingSpeedKeeper.TARGET)),
            Map.entry("JumpAssistFlyingSpeedMixin", new Fix(FlyingSpeedKeeper.ID, FlyingSpeedKeeper.WHAT, FlyingSpeedKeeper.TARGET)),
            Map.entry("AaeDamageAbsorptionMixin", new Fix(QuantumAbsorption.ID, QuantumAbsorption.WHAT, QuantumAbsorption.TARGET)),
            Map.entry("PowersuitsMeterMixin", new Fix(MeterGuard.ID, MeterGuard.WHAT, MeterGuard.TARGET)),
            Map.entry("GobberDragonFlightMixin", new Fix(DragonFlightSends.ID, DragonFlightSends.WHAT, DragonFlightSends.TARGET)),
            Map.entry("CmTunnelSaveMixin", new Fix(LoadSaves.ID, LoadSaves.WHAT, LoadSaves.TARGET)),
            Map.entry("CmChunkloadSaveMixin", new Fix(LoadSaves.ID, LoadSaves.WHAT, LoadSaves.TARGET)),
            Map.entry("CmRoomHoldMixin", new Fix(RoomHolds.ID, RoomHolds.WHAT, RoomHolds.TARGET)),
            Map.entry("CmChunkloadRuleMixin", new Fix(RoomHolds.ID, RoomHolds.WHAT, RoomHolds.TARGET)),
            Map.entry("CmPreviewCleanupMixin", new Fix(PreviewLeak.ID, PreviewLeak.WHAT, PreviewLeak.TARGET)),
            Map.entry("BufferBuilderAccessor", new Fix(PreviewLeak.ID, PreviewLeak.WHAT, PreviewLeak.TARGET)),
            Map.entry("CmPreviewRequestMixin", new Fix(PreviewRules.ID, PreviewRules.WHAT, PreviewRules.TARGET)));

    private static final List<Fix> ACTIVE = Collections.synchronizedList(new ArrayList<>());
    /** Fixes the mixin plugin skipped, by id, with the reason (the EMI fixes without EMI at the pinned version; client-only fixes on a dedicated server). */
    private static final Map<String, String> SKIPPED = Collections.synchronizedMap(new LinkedHashMap<>());
    private static volatile boolean reported;

    private Fixes() {}

    /** Wires the event handlers of every fix that has some, and records the fixes made of resources. Called once from the mod constructor. */
    public static void registerEvents() {
        active(Lang.ID, Lang.WHAT, Lang.TARGET);                     // F-9, the lang files in this jar
        MinecraftForge.EVENT_BUS.addListener(ShockAbsorber::onFall); // F-7, whose mixin cancels Powersuits' handler
        MinecraftForge.EVENT_BUS.addListener(EnergyShield::onHurt);  // F-8 (c)
        MinecraftForge.EVENT_BUS.addListener(FlyingSpeedKeeper::onPlayerTick); // F-24, the guard (the mixins do the rest)
        // F-29, only beside the Compact Machines build it was written for, and only when its mixins were not skipped (the plugin
        // has decided by now): without them Compact Machines forces rooms itself, and clearing its flags at start would undo that
        if (CmGate.installedVersion().map(CmGate.VERSION::equals).orElse(false) && !SKIPPED.containsKey(RoomHolds.ID)) {
            MinecraftForge.EVENT_BUS.addListener(RoomLoader::onServerTick);
            MinecraftForge.EVENT_BUS.addListener(RoomLoader::onServerStarted);
            MinecraftForge.EVENT_BUS.addListener(RoomLoader::onServerStopped);
        }
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

    /** Whether a mixin of the config (simple name) is one of the EMI fixes, which the plugin gates on EMI's presence and version. */
    public static boolean isEmiMixin(String mixinSimpleName) {
        Fix fix = MIXIN_FIXES.get(mixinSimpleName);
        return fix != null && fix.targetMod().equals(EmiGate.TARGET);
    }

    /** Called by the mixin config plugin when it skips a mixin; the startup report lists the fix as skipped with the reason. */
    public static void mixinSkipped(String mixinSimpleName, String reason) {
        Fix fix = MIXIN_FIXES.get(mixinSimpleName);
        if (fix != null) {
            SKIPPED.putIfAbsent(fix.id(), reason);
        }
    }

    /** The fixes skipped so far: id to reason. */
    public static Map<String, String> skipped() {
        synchronized (SKIPPED) {
            return new LinkedHashMap<>(SKIPPED);
        }
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

    /** F-2 before F-10; an id without a number sorts last, by text. */
    public static int byNumber(String a, String b) {
        int na = number(a), nb = number(b);
        return na != nb ? Integer.compare(na, nb) : a.compareTo(b);
    }

    private static int number(String id) {
        int start = 0;
        while (start < id.length() && !Character.isDigit(id.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < id.length() && Character.isDigit(id.charAt(end))) {
            end++;
        }
        return start == end ? Integer.MAX_VALUE : Integer.parseInt(id.substring(start, end));
    }

    /** How long a key's WARN lines are held back after one is printed: a hostile client cannot flood the log. */
    public static final long WARN_INTERVAL_MS = 10_000;
    private static final Map<String, long[]> WARN_LAST = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Whether a WARN line for this key (a fix id and a player name) may be printed now: the first one always, then one per
     * {@link #WARN_INTERVAL_MS}. The suppressed count since the last printed line is returned through {@code suppressed}.
     */
    public static boolean mayWarn(String key, long nowMs, int[] suppressed) {
        long[] state = WARN_LAST.computeIfAbsent(key, k -> new long[]{Long.MIN_VALUE, 0});
        synchronized (state) {
            if (nowMs - state[0] >= WARN_INTERVAL_MS || state[0] == Long.MIN_VALUE) {
                suppressed[0] = (int) state[1];
                state[0] = nowMs;
                state[1] = 0;
                return true;
            }
            state[1]++;
            return false;
        }
    }

    /** A WARN line throttled per key; when earlier lines were held back the count is appended. */
    public static void warnThrottled(String key, String message, Object... args) {
        int[] suppressed = new int[1];
        if (mayWarn(key, System.currentTimeMillis(), suppressed)) {
            if (suppressed[0] > 0) {
                LOGGER.warn(message + " ({} more like it held back in the last {} s)", concat(args, suppressed[0], WARN_INTERVAL_MS / 1000));
            } else {
                LOGGER.warn(message, args);
            }
        }
    }

    private static Object[] concat(Object[] args, Object... more) {
        Object[] all = java.util.Arrays.copyOf(args, args.length + more.length);
        System.arraycopy(more, 0, all, args.length, more.length);
        return all;
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
        Map<String, String> skipped = skipped();
        LOGGER.info("BSP-Mod Fixes: {} fixes in this build, {} already applied, {} skipped", fixes.size(), applied.size(), skipped.size());
        for (Fix f : fixes) {
            String state = applied.contains(f) ? ""
                    : skipped.containsKey(f.id()) ? " [skipped: " + skipped.get(f.id()) + "]"
                    : " [applies when its target class loads]";
            LOGGER.info("  {} ({}): {}{}", f.id(), f.targetMod(), f.what(), state);
        }
    }
}
