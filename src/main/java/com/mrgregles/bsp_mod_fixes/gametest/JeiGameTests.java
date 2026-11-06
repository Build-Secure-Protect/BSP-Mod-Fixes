package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.jei.JeiGate;
import com.mrgregles.bsp_mod_fixes.fix.jei.JeiStartGate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

/**
 * F-19 on the headless server: the pure start gate (held until this connection's tags, whatever the event order; a new
 * connection holds again), the JEI version gate, and the startup report's view of the fix. The GameTest server is a
 * dedicated server, so the mixin is skipped there whether or not JEI is loaded; JEI's client start code cannot load here.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class JeiGameTests {
    @GameTest(template = ModFixesGameTests.EMPTY)
    public void jei_start_is_held_until_the_connections_tags_arrive_whatever_the_order(GameTestHelper h) {
        JeiStartGate.Session s = new JeiStartGate.Session();
        Object first = new Object();
        Object second = new Object();
        h.assertTrue(s.holdsStart(null), "no connection yet: held");
        h.assertTrue(s.holdsStart(first), "login and recipes before the tags: held");
        h.assertTrue(JeiStartGate.holdStart(first) && JeiStartGate.heldCount() == 1, "the static gate holds and logs once for a login");
        h.assertTrue(JeiStartGate.holdStart(first) && JeiStartGate.heldCount() == 1, "a second ask for the same login is held without a second line");
        JeiStartGate.tagsSynced(first);
        h.assertTrue(JeiStartGate.releasedCount() == 1 && !JeiStartGate.holdStart(first), "the tags release it once");
        JeiStartGate.tagsSynced(first);
        h.assertTrue(JeiStartGate.releasedCount() == 1, "a later tag packet of the same login (a /reload) releases nothing again");
        s.tagsSynced(first);
        h.assertFalse(s.holdsStart(first), "the tags arrived: JEI may start");
        h.assertFalse(s.holdsStart(first), "and may start again later on the same connection (a recipe sync after /reload)");
        h.assertTrue(s.holdsStart(second), "a new connection (a new login or a server switch): held again");
        s.tagsSynced(second);
        h.assertFalse(s.holdsStart(second), "released by its own tags");
        h.assertTrue(s.holdsStart(first), "the old connection no longer counts");
        JeiStartGate.Session tagsFirst = new JeiStartGate.Session();
        Object third = new Object();
        tagsFirst.tagsSynced(third);
        h.assertFalse(tagsFirst.holdsStart(third), "tags before the recipe sync (the order after /reload): not held");
        h.assertTrue(JeiStartGate.countsAsSync(true) && !JeiStartGate.countsAsSync(false), "only a client tag packet counts, not the integrated server's data load");
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void jei_gate_applies_the_fix_to_any_15_74_build_and_skips_it_on_a_dedicated_server(GameTestHelper h) {
        h.assertTrue(JeiGate.verdict(Optional.of("15.74.0.234")) == JeiGate.Verdict.APPLY, "the pack's build applies");
        h.assertTrue(JeiGate.verdict(Optional.of("15.74.0.229")) == JeiGate.Verdict.APPLY, "the servers' build applies (D33: any 15.74)");
        h.assertTrue(JeiGate.verdict(Optional.of("15.74.1.300")) == JeiGate.Verdict.APPLY, "a later 15.74 build applies");
        h.assertTrue(JeiGate.verdict(Optional.of("15.20.0.106")) == JeiGate.Verdict.OTHER_VERSION, "an older series is another version");
        h.assertTrue(JeiGate.verdict(Optional.of("15.75.0.1")) == JeiGate.Verdict.OTHER_VERSION, "a newer series is another version");
        h.assertTrue(JeiGate.verdict(Optional.of("15.7.4.1")) == JeiGate.Verdict.OTHER_VERSION, "the prefix needs its dots");
        h.assertTrue(JeiGate.verdict(Optional.empty()) == JeiGate.Verdict.ABSENT, "no JEI: absent");
        h.assertTrue(JeiGate.verdict(Optional.of("15.74.0.234"), false) == JeiGate.Verdict.CHANGED_TARGET, "a 15.74 build without the patched methods costs the fix, not the client");
        h.assertTrue(JeiGate.verdict(Optional.of("15.20.0.106"), false) == JeiGate.Verdict.OTHER_VERSION, "another series is reported as such first");
        ClassNode observer = new ClassNode();
        observer.methods = new ArrayList<>();
        h.assertFalse(JeiGate.hasMethods(observer, JeiGate.TARGET_METHODS), "an empty class lacks the methods");
        observer.methods.add(new MethodNode(0, "startIfReady", "()V", null, null));
        h.assertFalse(JeiGate.hasMethods(observer, JeiGate.TARGET_METHODS), "one of two is not enough");
        observer.methods.add(new MethodNode(0, "register", "(Lmezz/jei/forge/events/PermanentEventSubscriptions;)V", null, null));
        h.assertTrue(JeiGate.hasMethods(observer, JeiGate.TARGET_METHODS), "both methods with their descriptors: the target is as expected");
        observer.methods.set(0, new MethodNode(0, "startIfReady", "(Z)V", null, null));
        h.assertFalse(JeiGate.hasMethods(observer, JeiGate.TARGET_METHODS), "a changed descriptor counts as missing");
        h.assertFalse(JeiGate.hasMethods(null, JeiGate.TARGET_METHODS), "no class: missing");
        h.assertTrue(JeiGate.gates("JeiStartObserverMixin") && !JeiGate.gates("EmiNullStackMixin"), "the gate decides for the JEI mixin only");
        h.assertTrue(Fixes.known().stream().anyMatch(f -> f.id().equals(JeiStartGate.ID)), "F-19 is known to the build");
        h.assertTrue(Fixes.active().stream().noneMatch(f -> f.id().equals(JeiStartGate.ID)), "F-19 is not active on a server");
        h.assertTrue(JeiGate.decide() == JeiGate.Verdict.SERVER, "on a dedicated server the verdict is SERVER, got " + JeiGate.decide());
        Map<String, String> skipped = Fixes.skipped();
        h.assertTrue(skipped.containsKey(JeiStartGate.ID) && skipped.get(JeiStartGate.ID).contains("dedicated server"), "F-19 was skipped by the plugin with the server reason, got " + skipped);
        if (ModList.get().isLoaded(JeiGate.MOD_ID)) {
            String found = BSPModFixes.version(JeiGate.MOD_ID);
            h.assertTrue(JeiGate.installedVersion().equals(Optional.of(found)), "the gate reads the loaded JEI version, " + found);
        } else {
            h.assertTrue(JeiGate.installedVersion().isEmpty(), "no JEI in the loading mod list");
        }
        h.succeed();
    }
}
