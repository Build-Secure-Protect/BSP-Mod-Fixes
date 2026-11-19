package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.MixinFixPlugin;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.emi.SlotViews;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * MF-38: EMI's JEI slot views gain JEI 15.34 to 15.53's members (F-41). The members themselves are checked on EMI's real slot
 * views by the dev-client probe ({@code -PjemiProbe}); here the list helper, and the registry's view of the two mixins, which
 * are EMI fixes and are skipped on this dedicated server (or by {@code -PskipFixes}).
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class EmiSlotViewGameTests {
    @GameTest(template = ModFixesGameTests.EMPTY)
    public void slot_view_members_are_emi_fixes_and_the_list_keeps_the_slots_order(GameTestHelper h) {
        List<String> list = SlotViews.list(Stream.of("book", "lapis", "amplifier"));
        h.assertTrue(list.equals(List.of("book", "lapis", "amplifier")), "the list is the stream in order: " + list);
        boolean mutable;
        try {
            list.add("x");
            mutable = true;
        } catch (UnsupportedOperationException e) {
            mutable = false;
        }
        h.assertTrue(!mutable, "the list is unmodifiable, as JEI documents its own");
        h.assertTrue(SlotViews.list(Stream.empty()).isEmpty(), "an empty slot gives an empty list");
        for (String mixin : List.of("EmiJemiSlotViewMixin", "EmiJemiSlotDrawableMixin")) {
            h.assertTrue(Fixes.isEmiMixin(mixin), mixin + " is gated as an EMI fix");
        }
        h.assertTrue(Fixes.known().stream().anyMatch(f -> f.id().equals(SlotViews.ID)), "F-41 is known");
        h.assertTrue(Fixes.active().stream().noneMatch(f -> f.id().equals(SlotViews.ID)), "F-41 is never active on a dedicated server");
        Map<String, String> skipped = Fixes.skipped();
        String reason = skipped.getOrDefault(SlotViews.ID, "");
        h.assertTrue(reason.contains("dedicated server") || reason.equals(MixinFixPlugin.SKIP_REASON), "F-41 is skipped here with the server reason (or switched off for the run): " + skipped);
        h.succeed();
    }
}
