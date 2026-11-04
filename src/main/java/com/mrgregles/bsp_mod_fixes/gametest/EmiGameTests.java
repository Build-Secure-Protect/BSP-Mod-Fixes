package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.emi.EmiGate;
import com.mrgregles.bsp_mod_fixes.fix.emi.NullStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * The EMI fixes (F-11 onward) on the headless server: the gate's verdict, and the startup report's view of them. The GameTest
 * server is a dedicated server, so the gate skips the fixes there whether or not {@code -PwithEmi} loads EMI; the pure
 * verdict is checked for the loaded version. The client classes themselves cannot load here.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class EmiGameTests {
    @GameTest(template = ModFixesGameTests.EMPTY)
    public void emi_gate_applies_the_fixes_for_the_exact_version_only(GameTestHelper h) {
        h.assertTrue(EmiGate.verdict(Optional.of("1.1.24+1.20.1+forge")) == EmiGate.Verdict.APPLY, "the pinned build applies");
        h.assertTrue(EmiGate.verdict(Optional.empty()) == EmiGate.Verdict.ABSENT, "no EMI: absent");
        h.assertTrue(EmiGate.verdict(Optional.of("1.1.23+1.20.1+forge")) == EmiGate.Verdict.OTHER_VERSION, "an older EMI is another version");
        h.assertTrue(EmiGate.verdict(Optional.of("1.1.25+1.20.1+forge")) == EmiGate.Verdict.OTHER_VERSION, "a newer EMI is another version");
        h.assertTrue(EmiGate.verdict(Optional.of("1.1.24+1.20.1+fabric")) == EmiGate.Verdict.OTHER_VERSION, "the fabric build is another version");
        h.assertTrue(!EmiGate.Verdict.ABSENT.applies() && !EmiGate.Verdict.OTHER_VERSION.applies(), "only APPLY applies");
        h.assertTrue(EmiGate.Verdict.ABSENT.reason().contains("not loaded"), "the absent reason says so");
        h.assertTrue(EmiGate.Verdict.OTHER_VERSION.reason().contains(EmiGate.VERSION), "the version reason names the pinned build");
        h.assertTrue(NullStack.isMissing(null) && !NullStack.isMissing(new Object()), "F-11 treats null as missing and nothing else");
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void emi_fixes_are_skipped_on_a_dedicated_server_and_the_gate_reads_the_loaded_version(GameTestHelper h) {
        h.assertTrue(Fixes.isEmiMixin("EmiNullStackMixin"), "the F-11 mixin is an EMI mixin");
        h.assertFalse(Fixes.isEmiMixin("NuminaFireClearMixin"), "a Numina mixin is not");
        h.assertTrue(Fixes.active().stream().noneMatch(f -> f.id().equals(NullStack.ID)), "F-11 is not active on a server");
        h.assertTrue(Fixes.known().stream().anyMatch(f -> f.id().equals(NullStack.ID)), "F-11 is known to the build");
        Map<String, String> skipped = Fixes.skipped();
        h.assertTrue(EmiGate.decide() == EmiGate.Verdict.SERVER, "on a dedicated server the verdict is SERVER whatever is installed, got " + EmiGate.decide());
        h.assertTrue(skipped.containsKey(NullStack.ID) && skipped.get(NullStack.ID).contains("dedicated server"), "F-11 was skipped by the plugin with the server reason, got " + skipped);
        if (ModList.get().isLoaded(EmiGate.MOD_ID)) {
            String found = BSPModFixes.version(EmiGate.MOD_ID);
            h.assertTrue(EmiGate.installedVersion().equals(Optional.of(found)), "the gate reads the loaded EMI version, " + found);
            h.assertTrue(EmiGate.verdict(EmiGate.installedVersion()) == (found.equals(EmiGate.VERSION) ? EmiGate.Verdict.APPLY : EmiGate.Verdict.OTHER_VERSION), "the pure verdict for the loaded EMI");
        } else {
            h.assertTrue(EmiGate.installedVersion().isEmpty(), "no EMI in the loading mod list");
            h.assertTrue(EmiGate.verdict(Optional.empty()) == EmiGate.Verdict.ABSENT, "the pure verdict without EMI");
        }
        h.succeed();
    }
}
