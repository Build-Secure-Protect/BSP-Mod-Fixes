package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.gobber.DragonFlightSends;
import com.mrgregles.bsp_mod_fixes.fix.gobber.GobberGate;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MF-25: Gobber 2's dragon armour sends the player's abilities only when they changed (F-27). The rule and the gate are
 * pure; with {@code -PwithGobber} a dressed mock player's inventory ticks through the real item and the packets it sends
 * are counted on the mock connection's channel. No Gobber class is referenced here: the pieces come from the registry.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class GobberGameTests {
    /** Gobber's four dragon pieces, in the order of the slots below. */
    private static final ResourceLocation[] PIECES = {
            ResourceLocation.fromNamespaceAndPath(GobberGate.MOD_ID, "gobber2_helmet_dragon"),
            ResourceLocation.fromNamespaceAndPath(GobberGate.MOD_ID, "gobber2_chestplate_dragon"),
            ResourceLocation.fromNamespaceAndPath(GobberGate.MOD_ID, "gobber2_leggings_dragon"),
            ResourceLocation.fromNamespaceAndPath(GobberGate.MOD_ID, "gobber2_boots_dragon")};
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void dragon_flight_sends_only_on_a_change_on_the_server_and_never_from_the_client_and_the_gobber_gate_holds(GameTestHelper h) {
        int none = DragonFlightSends.flags(false, false);
        int may = DragonFlightSends.flags(true, false);
        int flying = DragonFlightSends.flags(true, true);
        h.assertTrue(none != may && may != flying && none != flying, "the three states differ");
        h.assertTrue(DragonFlightSends.shouldSend(false, false, may, may) && DragonFlightSends.shouldSend(false, true, may, may), "rule off: every call goes out, on both sides (D58)");
        h.assertTrue(DragonFlightSends.shouldSend(true, false, DragonFlightSends.NONE, may), "no head recorded: the call goes out");
        h.assertTrue(!DragonFlightSends.shouldSend(true, true, none, may) && !DragonFlightSends.shouldSend(true, true, may, may), "the client never sends from the armour tick (D54)");
        h.assertTrue(DragonFlightSends.shouldSend(true, false, none, may), "the server sends the grant");
        h.assertTrue(DragonFlightSends.shouldSend(true, false, flying, none), "and the revoke when the armour comes off");
        h.assertTrue(!DragonFlightSends.shouldSend(true, false, may, may) && !DragonFlightSends.shouldSend(true, false, flying, flying), "unchanged flags: held");
        h.assertTrue(FixesConfig.SPEC.isLoaded() && FixesConfig.FIX_DRAGON_FLIGHT.get() && DragonFlightSends.enabled(), "the switch is on by default");

        h.assertTrue(GobberGate.verdict(Optional.of(GobberGate.VERSION)).applies(), "exactly " + GobberGate.VERSION + " applies");
        h.assertTrue(GobberGate.verdict(Optional.of("2.8.7")) == GobberGate.Verdict.OTHER_VERSION, "the older build, which does not have the bug, is skipped");
        h.assertTrue(GobberGate.verdict(Optional.of("2.8.10")) == GobberGate.Verdict.OTHER_VERSION, "a newer build is skipped");
        h.assertTrue(GobberGate.verdict(Optional.empty()) == GobberGate.Verdict.ABSENT, "absent is skipped");
        h.assertTrue(GobberGate.verdict(Optional.of(GobberGate.VERSION), false) == GobberGate.Verdict.CHANGED_TARGET, "2.8.9 without the method is skipped");
        h.assertTrue(GobberGate.gates("GobberDragonFlightMixin") && !GobberGate.gates("AaeDamageAbsorptionMixin"), "the gate decides for its own mixin only");
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void dragon_armour_sends_one_abilities_update_instead_of_four_a_tick_and_the_switch_restores_gobbers_own(GameTestHelper h) {
        if (!ModList.get().isLoaded(GobberGate.MOD_ID)) {
            Map<String, String> skipped = Fixes.skipped();
            h.assertTrue(skipped.containsKey(DragonFlightSends.ID) && skipped.get(DragonFlightSends.ID).equals(GobberGate.Verdict.ABSENT.reason()),
                    "without Gobber the plugin skipped F-27 with the absent reason, got " + skipped);
            Fixes.LOGGER.info("BSP-Mod Fixes test: Gobber 2 is not loaded, the dragon armour tick is not run (add -PwithGobber)");
            h.succeed();
            return;
        }
        h.assertTrue(BSPModFixes.version(GobberGate.MOD_ID).equals(GobberGate.VERSION), "Gobber 2 " + GobberGate.VERSION + ", found " + BSPModFixes.version(GobberGate.MOD_ID));
        // With -PskipFixes=GobberDragonFlightMixin the armour ticks as Gobber ships it: four updates a tick.
        boolean fixOn = Fixes.active().stream().anyMatch(f -> f.id().equals(DragonFlightSends.ID));
        h.assertTrue(fixOn || Fixes.skipped().containsKey(DragonFlightSends.ID), "F-27 is applied (the dragon armour class loads at Gobber's registration) or switched off for this run: " + Fixes.active() + " " + Fixes.skipped());
        Fixes.LOGGER.info("BSP-Mod Fixes test: the dragon armour ticks {}", fixOn ? "with F-27" : "as Gobber ships it (F-27 switched off)");

        ServerPlayer player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        player.moveTo(h.absoluteVec(new Vec3(12, 2, 12)));
        EmbeddedChannel channel = (EmbeddedChannel) player.connection.connection.channel();
        for (int i = 0; i < PIECES.length; i++) {
            ItemStack piece = new ItemStack(ForgeRegistries.ITEMS.getValue(PIECES[i]));
            h.assertTrue(!piece.isEmpty(), PIECES[i] + " exists");
            player.setItemSlot(SLOTS[i], piece);
        }
        h.assertTrue(!player.getAbilities().mayfly && !player.isCreative(), "a survival player who may not fly yet");
        abilitiesPackets(channel);
        long sent = DragonFlightSends.sent(), held = DragonFlightSends.held();

        // twenty inventory ticks (what Player.aiStep runs every tick, on both sides): Forge calls onArmorTick for each of the four pieces
        for (int t = 0; t < 20; t++) {
            player.getInventory().tick();
        }
        int packets = abilitiesPackets(channel);
        h.assertTrue(player.getAbilities().mayfly, "the full set grants creative flight");
        if (fixOn) {
            h.assertTrue(packets == 1, "one abilities update, for the grant, over twenty ticks of four pieces; found " + packets);
            h.assertTrue(DragonFlightSends.sent() == sent + 1 && DragonFlightSends.held() == held + 79, "the mixin sent one call and held 79, found " + (DragonFlightSends.sent() - sent) + " and " + (DragonFlightSends.held() - held));
        } else {
            h.assertTrue(packets == 80, "Gobber's own: four abilities updates a tick, 80 over twenty ticks (the echo loop's server half); found " + packets);
        }

        if (fixOn) {
            // the switch (D58): off, Gobber's calls pass through
            boolean saved = FixesConfig.FIX_DRAGON_FLIGHT.get();
            try {
                FixesConfig.FIX_DRAGON_FLIGHT.set(false);
                h.assertTrue(!DragonFlightSends.enabled(), "the switch reads off");
                for (int t = 0; t < 5; t++) {
                    player.getInventory().tick();
                }
                h.assertTrue(abilitiesPackets(channel) == 20, "switched off, Gobber's four updates a tick are back");
            } finally {
                FixesConfig.FIX_DRAGON_FLIGHT.set(saved);
            }
            h.assertTrue(DragonFlightSends.enabled(), "the switch is back on");
        }

        // the chestplate comes off: the next piece to tick revokes the flight, one update either way, and the others have nothing to say
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        for (int t = 0; t < 5; t++) {
            player.getInventory().tick();
        }
        h.assertTrue(!player.getAbilities().mayfly && !player.getAbilities().flying, "without the full set the flight is revoked");
        h.assertTrue(abilitiesPackets(channel) == 1, "one update for the revoke over five ticks");
        h.succeed();
    }


    /**
     * The player's symptom, played through: the real server side (a dressed mock player, Gobber's armour tick, vanilla's
     * {@code handlePlayerAbilities}) against a modelled client (vanilla's two rules: an incoming abilities packet overwrites
     * {@code flying} and {@code mayfly}; the double tap sets {@code flying} and sends one packet) with a two-tick latency each
     * way. Gobber's client half, four packets a tick from the armour tick, is modelled as Gobber ships it when the fix is off
     * and left out when it is on, which is what F-27 does on a real client (the pure rule above).
     */
    @GameTest(template = ModFixesGameTests.EMPTY)
    public void dragon_flight_holds_through_a_latency_loop_with_the_fix_and_flickers_without_it(GameTestHelper h) {
        if (!ModList.get().isLoaded(GobberGate.MOD_ID)) {
            Fixes.LOGGER.info("BSP-Mod Fixes test: Gobber 2 is not loaded, the latency loop is not run (add -PwithGobber)");
            h.succeed();
            return;
        }
        boolean fixOn = Fixes.active().stream().anyMatch(f -> f.id().equals(DragonFlightSends.ID));
        ServerPlayer player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        player.moveTo(h.absoluteVec(new Vec3(12, 2, 12)));
        EmbeddedChannel channel = (EmbeddedChannel) player.connection.connection.channel();
        for (int i = 0; i < PIECES.length; i++) {
            player.setItemSlot(SLOTS[i], new ItemStack(ForgeRegistries.ITEMS.getValue(PIECES[i])));
        }
        abilitiesPackets(channel);

        final int latency = 2;      // ticks each way, a 100 ms round trip
        final int tapAt = 6;        // the double tap, once the grant has arrived
        final int ticks = 60;
        Abilities client = new Abilities();
        Deque<Object[]> toClient = new ArrayDeque<>();   // {arrival tick, ClientboundPlayerAbilitiesPacket}
        Deque<Object[]> toServer = new ArrayDeque<>();   // {arrival tick, ServerboundPlayerAbilitiesPacket}
        List<Boolean> clientFlying = new ArrayList<>();
        for (int t = 0; t < ticks; t++) {
            // packets due this tick, as each side's handler takes them
            while (!toClient.isEmpty() && (int) toClient.peekFirst()[0] <= t) {
                ClientboundPlayerAbilitiesPacket p = (ClientboundPlayerAbilitiesPacket) toClient.pollFirst()[1];
                client.flying = p.isFlying();
                client.mayfly = p.canFly();
            }
            while (!toServer.isEmpty() && (int) toServer.peekFirst()[0] <= t) {
                player.connection.handlePlayerAbilities((ServerboundPlayerAbilitiesPacket) toServer.pollFirst()[1]);
            }
            // the server's tick: Gobber's armour tick through the real item
            player.getInventory().tick();
            channel.runPendingTasks();
            for (Object message : channel.outboundMessages()) {
                if (message instanceof ClientboundPlayerAbilitiesPacket p) {
                    toClient.addLast(new Object[]{t + latency, p});
                }
            }
            channel.outboundMessages().clear();
            // the client's tick: vanilla's double tap, then Gobber's client armour tick (four sends a tick as shipped, none with F-27)
            if (t == tapAt) {
                h.assertTrue(client.mayfly, "the grant reached the client before the double tap");
                client.flying = true;
                toServer.addLast(new Object[]{t + latency, new ServerboundPlayerAbilitiesPacket(client)});
            }
            if (!fixOn) {
                client.mayfly = true;
                for (int piece = 0; piece < 4; piece++) {
                    toServer.addLast(new Object[]{t + latency, new ServerboundPlayerAbilitiesPacket(client)});
                }
            }
            if (t > tapAt + 2 * latency) {
                clientFlying.add(client.flying);
            }
        }
        long flyingTicks = clientFlying.stream().filter(b -> b).count();
        Fixes.LOGGER.info("BSP-Mod Fixes test: latency loop {} F-27: the client was flying on {} of {} ticks after the double tap settled; server flying={} mayfly={}",
                fixOn ? "with" : "without", flyingTicks, clientFlying.size(), player.getAbilities().flying, player.getAbilities().mayfly);
        if (fixOn) {
            h.assertTrue(flyingTicks == clientFlying.size(), "with F-27 the flight holds on every tick, found " + flyingTicks + " of " + clientFlying.size());
            h.assertTrue(player.getAbilities().flying && player.getAbilities().mayfly, "and the server agrees");
        } else {
            h.assertTrue(flyingTicks > 0 && flyingTicks * 2 < clientFlying.size(), "as Gobber ships it the flight flickers: on for " + flyingTicks + " of " + clientFlying.size() + " ticks (the player's vibration)");
        }
        h.succeed();
    }

    /** The abilities packets written to the mock player's channel so far; the channel's outbound queue is cleared. */
    private static int abilitiesPackets(EmbeddedChannel channel) {
        channel.runPendingTasks();
        int n = 0;
        for (Object message : channel.outboundMessages()) {
            if (message instanceof ClientboundPlayerAbilitiesPacket) {
                n++;
            }
        }
        channel.outboundMessages().clear();
        return n;
    }
}
