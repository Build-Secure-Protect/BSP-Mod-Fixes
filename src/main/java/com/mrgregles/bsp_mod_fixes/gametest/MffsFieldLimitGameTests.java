package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.mffs.FieldLimit;
import com.mrgregles.bsp_mod_fixes.fix.mffs.MffsGate;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * MF-32: a force field over the configured size is not projected (F-35). The rule, the recipients and the gate are pure, and
 * the action-bar line is read off a mock player's channel; with {@code -PwithMffs} a real powered projector with the cube mode
 * and three Scale modules builds its 218-block field under no limit, then is refused under a limit of 200: MFFS's own
 * teardown and recalculation lead to an empty field, a block placed by hand at a field position is removed at the next notice,
 * and with {@code -PskipFixes=MffsFieldLimitMixin} the field is simply built again.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class MffsFieldLimitGameTests {
    /** A cube of scale radius 3: 7^3 - 5^3 positions on its shell. */
    static final int SHELL = 218;

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void a_field_over_either_limit_is_refused_and_players_within_fifteen_blocks_are_told(GameTestHelper h) {
        h.assertTrue(FieldLimit.radius(new BlockPos(1, 2, 3), new BlockPos(4, 0, 2)) == 4 && FieldLimit.radius(new BlockPos(7, 0, 0), BlockPos.ZERO) == 7, "the radius is the largest Scale count on any side");
        h.assertTrue(!FieldLimit.judge(100_000, 64, 100_000, 0).refused(), "exactly at the block limit passes");
        h.assertTrue(FieldLimit.judge(100_001, 64, 100_000, 0).why().equals("blocks"), "one over the block limit is refused for blocks");
        h.assertTrue(FieldLimit.judge(50, 129, 100_000, 128).why().equals("radius") && !FieldLimit.judge(50, 128, 100_000, 128).refused(), "the radius limit, exactly at and over");
        h.assertTrue(!FieldLimit.judge(5_000_000, 999, 0, 0).refused(), "0 and 0: no limit");
        h.assertTrue(FieldLimit.judge(300, 10, 200, 20).why().equals("blocks"), "blocks are judged first");
        h.assertTrue(FieldLimit.noticeDue(1, Long.MIN_VALUE) && !FieldLimit.noticeDue(1000, 500) && FieldLimit.noticeDue(1700, 500), "a notice at once, then once a minute");
        h.assertTrue(FixesConfig.SPEC.isLoaded() && FixesConfig.MFFS_MAX_FIELD_BLOCKS.get() == 100_000 && FixesConfig.MFFS_MAX_FIELD_RADIUS.get() == 0, "defaults: 100,000 blocks, no radius limit");
        h.assertTrue(MffsGate.gates("MffsFieldLimitMixin"), "the gate decides for the field limit mixin");

        BlockPos projector = h.absolutePos(new BlockPos(12, 2, 12));
        ServerPlayer near = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        near.moveTo(Vec3.atCenterOf(projector).add(10, 0, 0));
        ServerPlayer far = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        far.moveTo(Vec3.atCenterOf(projector).add(16, 0, 0));
        List<ServerPlayer> told = FieldLimit.within(List.of(near, far), projector, FieldLimit.RANGE);
        h.assertTrue(told.equals(List.of(near)), "only the player within 15 blocks is told");
        FieldLimit.notify(near);
        EmbeddedChannel channel = (EmbeddedChannel) near.connection.connection.channel();
        // 1.20.1 carries an action-bar line as a system chat packet with the overlay flag
        boolean actionBar = channel.outboundMessages().stream().anyMatch(m -> m instanceof ClientboundSystemChatPacket chat && chat.overlay());
        h.assertTrue(actionBar, "the line goes to the action bar");
        h.succeed();
    }

    // Flips the field limit for a moment and builds a real field: a batch of its own.
    @GameTest(template = ModFixesGameTests.EMPTY, timeoutTicks = 7000, batch = "mffs_field_limit")
    public void a_real_projector_builds_its_field_under_no_limit_and_projects_nothing_over_it(GameTestHelper h) {
        if (!ModList.get().isLoaded(MffsGate.MOD_ID)) {
            Fixes.LOGGER.info("BSP-Mod Fixes test: MFFS is not loaded, no projector is placed (add -PwithMffs)");
            h.succeed();
            return;
        }
        boolean fixOn = Fixes.active().stream().anyMatch(f -> f.id().equals(FieldLimit.ID));
        ServerLevel level = h.getLevel();
        BlockPos center = h.absolutePos(new BlockPos(12, 4, 12));
        FieldLimit.overrideMaxFieldBlocks(0); // no limit while the field is built; an override, so a failed run never writes the dev world's config
        level.setBlockAndUpdate(center, MffsTestBridge.projector());
        BlockEntity projector = level.getBlockEntity(center);
        h.assertTrue(projector != null, "the projector's block entity exists");
        MffsTestBridge.setCubeOfRadius(projector, 3);
        MffsTestBridge.power(projector);
        // The selection and projection steps run on a background thread while this server ticks hundreds of times a second,
        // so the build takes far more ticks than the 28 blocks per 10 ticks MFFS paces it at; the override is cleared whatever happens.
        h.runAfterDelay(1200, () -> {
            try {
                int built = shellBlocks(level, center);
                h.assertTrue(built == SHELL, "under no limit the powered projector built its whole shell: " + built + " of " + SHELL);
                long refusalsBefore = FieldLimit.refusals();
                FieldLimit.overrideMaxFieldBlocks(200);
                MffsTestBridge.destroyField(projector); // MFFS clears the field and recalculates: 218 > 200
                h.runAfterDelay(600, () -> {
                    try {
                        int after = shellBlocks(level, center);
                        if (!fixOn) {
                            h.assertTrue(after == SHELL, "with the fix off MFFS builds the field again under the limit: " + after);
                            FieldLimit.overrideMaxFieldBlocks(null);
                            h.succeed();
                            return;
                        }
                        h.assertTrue(after == 0, "refused: nothing projected after the recalculation, found " + after);
                        h.assertTrue(FieldLimit.refusals() == refusalsBefore + 1, "one refusal recorded: " + refusalsBefore + " before, " + FieldLimit.refusals() + " after");
                        // A field block that already stood there (an older field): removed at the next notice, a minute on.
                        BlockPos stray = center.offset(3, 0, 0);
                        level.setBlock(stray, MffsTestBridge.forceField(), 3);
                        long tornBefore = FieldLimit.tornDownBlocks();
                        h.runAfterDelay(FieldLimit.NOTICE_TICKS + 40, () -> {
                            try {
                                h.assertTrue(!MffsTestBridge.isForceField(level.getBlockState(stray)), "the stray field block was torn down at the next notice");
                                h.assertTrue(FieldLimit.tornDownBlocks() == tornBefore + 1, "and counted");
                                h.assertTrue(shellBlocks(level, center) == 0, "still nothing projected");
                            } catch (RuntimeException e) {
                                FieldLimit.overrideMaxFieldBlocks(null);
                                throw e;
                            }
                            // Unpowered, MFFS tears the field down every tick and the refusal is forgotten: no more notices or warnings.
                            MffsTestBridge.unpower(projector);
                            long warningsBefore = FieldLimit.warnings();
                            long refusalsBefore2 = FieldLimit.refusals();
                            h.runAfterDelay(FieldLimit.NOTICE_TICKS + 100, () -> {
                                try {
                                    h.assertTrue(FieldLimit.warnings() == warningsBefore, "an unpowered projector warns no more (" + (FieldLimit.warnings() - warningsBefore) + " new)");
                                    h.assertTrue(FieldLimit.refusals() == refusalsBefore2, "and is not judged again until it has power");
                                } finally {
                                    FieldLimit.overrideMaxFieldBlocks(null);
                                }
                                h.succeed();
                            });
                        });
                    } catch (RuntimeException e) {
                        FieldLimit.overrideMaxFieldBlocks(null);
                        throw e;
                    }
                });
            } catch (RuntimeException e) {
                FieldLimit.overrideMaxFieldBlocks(null);
                throw e;
            }
        });
    }

    /** Force field blocks on the shell of the radius-3 cube around the centre. */
    private static int shellBlocks(ServerLevel level, BlockPos center) {
        int n = 0;
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    if (Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z))) == 3 && MffsTestBridge.isForceField(level.getBlockState(center.offset(x, y, z)))) {
                        n++;
                    }
                }
            }
        }
        return n;
    }
}
