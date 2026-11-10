package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.cm.CmGate;
import com.mrgregles.bsp_mod_fixes.fix.cm.PreviewRules;
import com.mrgregles.bsp_mod_fixes.fix.cm.PreviewRules.Decision;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MF-29: the server half of Compact Machines' room preview (F-31). The decisions are pure; with {@code -PwithCm} a mock
 * player right-clicks a real machine bound to a real room that holds a full chest and a written sign, and the preview
 * packets on the player's channel are read back: how many, and what block entity data they carry.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class CompactPreviewRequestGameTests {
    private static final ResourceLocation CHANNEL = ResourceLocation.fromNamespaceAndPath("compactmachines", "room_tracking");

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void a_preview_request_is_left_refused_ignored_or_captured_by_the_rules(GameTestHelper h) throws Exception {
        long never = Long.MIN_VALUE;
        h.assertTrue(PreviewRules.decide(false, true, 100, never, 20) == Decision.LEAVE && PreviewRules.decide(false, false, 100, 99, 20) == Decision.LEAVE, "rule off: Compact Machines answers, whatever else is set");
        h.assertTrue(PreviewRules.decide(true, false, 100, never, 20) == Decision.OFF, "previews off: refused");
        h.assertTrue(PreviewRules.decide(true, true, 100, never, 20) == Decision.CAPTURE, "a first request is captured");
        h.assertTrue(PreviewRules.decide(true, true, 119, 100, 20) == Decision.TOO_SOON && PreviewRules.decide(true, true, 100, 100, 20) == Decision.TOO_SOON, "inside the interval: ignored");
        h.assertTrue(PreviewRules.decide(true, true, 120, 100, 20) == Decision.CAPTURE, "at the interval: captured");
        h.assertTrue(PreviewRules.decide(true, true, 100, 100, 0) == Decision.CAPTURE, "an interval of nothing never refuses");
        h.assertTrue(PreviewRules.decide(true, true, 5, 100, 20) == Decision.CAPTURE, "a clock that went back (a restart) does not lock a player out");
        h.assertTrue(PreviewRules.fits(1_000_000, 1_000_000) && !PreviewRules.fits(1_000_001, 1_000_000), "the size limit is inclusive");
        var counter = PreviewRules.counter();
        counter.write(new byte[1234]);
        h.assertTrue(counter.size() == 1234, "the counting stream counts");
        h.assertTrue(FixesConfig.CM_HARDEN_PREVIEW.get() && FixesConfig.CM_PREVIEWS.get() && FixesConfig.CM_PREVIEW_INTERVAL_SECONDS.get() == 1 && FixesConfig.CM_PREVIEW_HIDES_CONTENTS.get() && FixesConfig.CM_PREVIEW_MAX_KILOBYTES.get() == 1000,
                "defaults: the rule on, previews on, one second, contents hidden, 1,000 kilobytes");
        h.assertTrue(CmGate.gates("CmPreviewRequestMixin") && !CmGate.MIXINS.get("CmPreviewRequestMixin").clientOnly(), "the gate decides for the request mixin, on both sides");
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY, timeoutTicks = 400)
    public void a_right_click_sends_one_preview_without_the_chests_contents_and_not_again_inside_the_interval(GameTestHelper h) {
        if (!ModList.get().isLoaded(CmGate.MOD_ID)) {
            Map<String, String> skipped = Fixes.skipped();
            h.assertTrue(CmGate.Verdict.ABSENT.reason().equals(skipped.get(PreviewRules.ID)), "without Compact Machines the plugin skipped F-31 with the absent reason, got " + skipped);
            Fixes.LOGGER.info("BSP-Mod Fixes test: Compact Machines is not loaded, no preview is asked for (add -PwithCm)");
            h.succeed();
            return;
        }
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(9, 2, 9));
        level.setBlockAndUpdate(pos, ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath(CmGate.MOD_ID, "machine_normal")).defaultBlockState());
        ServerPlayer player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        player.moveTo(Vec3.atCenterOf(pos.above()));
        ChunkPos room = CmTestBridge.newFurnishedRoom(level, pos, player);   // a full chest and a sign that says "BSP"
        EmbeddedChannel channel = (EmbeddedChannel) player.connection.connection.channel();
        previews(channel);

        boolean fixOn = Fixes.active().stream().anyMatch(f -> f.id().equals(PreviewRules.ID));
        h.assertTrue(fixOn || Fixes.skipped().containsKey(PreviewRules.ID), "F-31 is applied or switched off for this run: " + Fixes.active() + " " + Fixes.skipped());
        Fixes.LOGGER.info("BSP-Mod Fixes test: a mock player right-clicks a Compact Machine {}", fixOn ? "with F-31" : "as shipped (F-31 switched off)");
        h.assertTrue(player.getMainHandItem().isEmpty(), "an empty hand");

        click(level, pos, player);
        List<CompoundTag> first = previews(channel);
        h.assertTrue(first.size() == 1, "one click, one preview packet; found " + first.size());
        CompoundTag chest = blockEntityData(first.get(0), "minecraft:chest");
        CompoundTag sign = blockEntityData(first.get(0), "minecraft:oak_sign");
        h.assertTrue(sign != null && sign.toString().contains("BSP"), "the sign's text travels either way (it is what a client in the room is sent): " + sign);
        if (!fixOn) {
            h.assertTrue(chest != null && chest.toString().contains("minecraft:diamond"), "as shipped the chest's contents are sent to whoever clicks: " + chest);
            for (int i = 0; i < 5; i++) {
                click(level, pos, player);
            }
            h.assertTrue(previews(channel).size() == 5, "and five more clicks in the same tick are five more captures");
            level.removeBlock(pos, false);
            h.succeed();
            return;
        }
        h.assertTrue(chest == null, "with F-31 the chest travels without its contents: " + chest);
        long tooSoon = PreviewRules.tooSoon();
        for (int i = 0; i < 5; i++) {
            click(level, pos, player);
        }
        h.assertTrue(previews(channel).isEmpty() && PreviewRules.tooSoon() == tooSoon + 5, "five more clicks inside the interval: nothing captured, nothing sent");

        h.runAfterDelay(25, () -> {
            click(level, pos, player);
            h.assertTrue(previews(channel).size() == 1, "after the interval the next click is answered");

            boolean previewsOn = FixesConfig.CM_PREVIEWS.get(), hides = FixesConfig.CM_PREVIEW_HIDES_CONTENTS.get(), rule = FixesConfig.CM_HARDEN_PREVIEW.get();
            int max = FixesConfig.CM_PREVIEW_MAX_KILOBYTES.get(), interval = FixesConfig.CM_PREVIEW_INTERVAL_SECONDS.get();
            try {
                FixesConfig.CM_PREVIEW_INTERVAL_SECONDS.set(0);
                FixesConfig.CM_PREVIEWS.set(false);
                long off = PreviewRules.off();
                click(level, pos, player);
                h.assertTrue(previews(channel).isEmpty() && PreviewRules.off() == off + 1, "previews switched off: nothing sent");
                FixesConfig.CM_PREVIEWS.set(true);

                FixesConfig.CM_PREVIEW_MAX_KILOBYTES.set(30);        // the bare room is about 16 kilobytes
                CmTestBridge.fillChest(level.getServer(), room, 27); // 27 written books, 20 kilobytes more if they were sent...
                click(level, pos, player);
                h.assertTrue(previews(channel).size() == 1, "...but with contents hidden the room still fits a thirty kilobyte limit");
                FixesConfig.CM_PREVIEW_HIDES_CONTENTS.set(false);
                long tooLarge = PreviewRules.tooLarge();
                click(level, pos, player);
                h.assertTrue(previews(channel).isEmpty() && PreviewRules.tooLarge() == tooLarge + 1, "sent whole it is over the limit and refused");
                FixesConfig.CM_PREVIEW_MAX_KILOBYTES.set(1000);
                click(level, pos, player);
                List<CompoundTag> whole = previews(channel);
                h.assertTrue(whole.size() == 1 && blockEntityData(whole.get(0), "minecraft:chest") != null, "previewHidesContents off sends the full save, as Compact Machines does");

                FixesConfig.CM_HARDEN_PREVIEW.set(false);
                for (int i = 0; i < 3; i++) {
                    click(level, pos, player);
                }
                h.assertTrue(previews(channel).size() == 3, "the rule off: Compact Machines answers every click itself");
            } finally {
                FixesConfig.CM_HARDEN_PREVIEW.set(rule);
                FixesConfig.CM_PREVIEWS.set(previewsOn);
                FixesConfig.CM_PREVIEW_HIDES_CONTENTS.set(hides);
                FixesConfig.CM_PREVIEW_MAX_KILOBYTES.set(max);
                FixesConfig.CM_PREVIEW_INTERVAL_SECONDS.set(interval);
            }
            level.removeBlock(pos, false);
            h.succeed();
        });
    }

    private static void click(ServerLevel level, BlockPos pos, ServerPlayer player) {
        level.getBlockState(pos).use(level, player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    /** The room data of every preview packet written to the player's channel so far; the channel's queue is cleared. */
    private static List<CompoundTag> previews(EmbeddedChannel channel) {
        channel.runPendingTasks();
        List<CompoundTag> found = new ArrayList<>();
        for (Object message : channel.outboundMessages()) {
            if (message instanceof ClientboundCustomPayloadPacket packet && packet.getIdentifier().equals(CHANNEL)) {
                FriendlyByteBuf data = packet.getData();
                data.readByte();          // the message's index on Compact Machines' channel
                data.readGlobalPos();     // the machine
                data.readChunkPos();      // the room
                found.add(data.readNbt());
                data.release();
            }
        }
        channel.outboundMessages().clear();
        return found;
    }

    /** The block entity data a captured room carries for the first block of the given id, or null when it carries none. */
    private static CompoundTag blockEntityData(CompoundTag template, String blockId) {
        ListTag palette = template.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocks = template.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            if (palette.getCompound(block.getInt("state")).getString("Name").equals(blockId)) {
                return block.contains("nbt", Tag.TAG_COMPOUND) ? block.getCompound("nbt") : null;
            }
        }
        throw new AssertionError("no " + blockId + " in the captured room");
    }
}
