package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.FixesClientConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.cm.CmGate;
import com.mrgregles.bsp_mod_fixes.fix.cm.PreviewLeak;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.MixinService;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * MF-28: the room preview's memory (F-30). The fix is client-side and its work is OpenGL, which a GameTest server has
 * none of; what is checked here is the gate (client only, skipped on this dedicated server) and, with {@code -PwithCm},
 * that the real Compact Machines and Gander classes still have every private member the cleanup reaches by name, read
 * from their bytecode without loading them. The preview itself is opened on the dev client by {@code PreviewProbe}.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class CompactPreviewGameTests {
    @GameTest(template = ModFixesGameTests.EMPTY)
    public void preview_cleanup_is_client_only_and_the_members_it_reaches_exist_in_the_real_jars(GameTestHelper h) throws Exception {
        Optional<String> right = Optional.of(CmGate.VERSION);
        h.assertTrue(CmGate.verdict(right, true, true, false).applies(), "on a client beside 6.0.3 the preview fix applies");
        h.assertTrue(CmGate.verdict(right, true, true, true) == CmGate.Verdict.SERVER, "on a dedicated server it is skipped as client-side");
        h.assertTrue(CmGate.verdict(right, true, false, true).applies(), "a server-side fix is not");
        h.assertTrue(CmGate.verdict(right, false, true, false) == CmGate.Verdict.CHANGED_TARGET, "a changed screen class is skipped");
        h.assertTrue(CmGate.verdict(Optional.empty(), true, true, false) == CmGate.Verdict.ABSENT && CmGate.verdict(Optional.of("7.0.81"), true, true, false) == CmGate.Verdict.OTHER_VERSION, "absent or another build is skipped before the side is asked");
        h.assertTrue(CmGate.gates("CmPreviewCleanupMixin") && CmGate.gates("BufferBuilderAccessor") && CmGate.MIXINS.get("BufferBuilderAccessor").clientOnly(), "the gate decides for the preview mixin and the buffer accessor, both client only");
        h.assertTrue(FixesClientConfig.freePreviewMemory(), "the client switch reads on (its default, and before a client config exists)");

        Map<String, String> skipped = Fixes.skipped();
        if (!ModList.get().isLoaded(CmGate.MOD_ID)) {
            h.assertTrue(CmGate.Verdict.ABSENT.reason().equals(skipped.get(PreviewLeak.ID)), "without Compact Machines the plugin skipped F-30 with the absent reason, got " + skipped);
            Fixes.LOGGER.info("BSP-Mod Fixes test: Compact Machines is not loaded, its preview classes are not read (add -PwithCm)");
            h.succeed();
            return;
        }
        h.assertTrue(skipped.containsKey(PreviewLeak.ID), "on this dedicated server F-30 is skipped, got " + skipped);
        int members = 0;
        for (Map.Entry<String, Set<String>> e : PreviewLeak.FIELDS.entrySet()) {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(e.getKey());
            for (String field : e.getValue()) {
                h.assertTrue(node.fields.stream().anyMatch(f -> f.name.equals(field)), e.getKey() + " has the field " + field);
                members++;
            }
        }
        for (Map.Entry<String, Set<String>> e : PreviewLeak.METHODS.entrySet()) {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(e.getKey());
            for (String method : e.getValue()) {
                h.assertTrue(node.methods.stream().anyMatch(m -> m.name.equals(method) && m.desc.equals("()V")), e.getKey() + " has the method " + method + "()");
                members++;
            }
        }
        ClassNode chain = MixinService.getService().getBytecodeProvider().getClassNode(PreviewLeak.CHAIN);
        h.assertTrue(chain.interfaces.contains("java/lang/AutoCloseable"), "the framebuffer chain is closeable");
        Fixes.LOGGER.info("BSP-Mod Fixes test: the {} members the preview cleanup reaches by name exist in Compact Machines {} and its Gander", members, CmGate.VERSION);
        h.succeed();
    }
}
