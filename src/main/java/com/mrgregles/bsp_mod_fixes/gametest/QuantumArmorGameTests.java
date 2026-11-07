package com.mrgregles.bsp_mod_fixes.gametest;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.aae.AaeGate;
import com.mrgregles.bsp_mod_fixes.fix.aae.QuantumAbsorption;
import com.mrgregles.bsp_mod_fixes.fix.aae.ReadoutState;
import com.mrgregles.bsp_mod_fixes.mixin.gametest.ServerPlayerAccessor;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * MF-20 and MF-22: AdvancedAE's quantum armor (F-25). The pure plan and rules run everywhere; the hit on a dressed mock
 * player runs only with {@code -PwithAae} (AdvancedAE, AE2 and GuideME loaded), and then proves that the suit pays AE for
 * 30 % of the damage left after the armor and that the wearer is told. No AdvancedAE class is referenced here: the pieces
 * come from the registry by name and their charge is the NBT AdvancedAE keeps it in.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class QuantumArmorGameTests {
    /** AdvancedAE's four pieces, in the order of the slots below. */
    private static final ResourceLocation[] PIECES = {
            ResourceLocation.fromNamespaceAndPath(AaeGate.MOD_ID, "quantum_helmet"),
            ResourceLocation.fromNamespaceAndPath(AaeGate.MOD_ID, "quantum_chestplate"),
            ResourceLocation.fromNamespaceAndPath(AaeGate.MOD_ID, "quantum_leggings"),
            ResourceLocation.fromNamespaceAndPath(AaeGate.MOD_ID, "quantum_boots")};
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** AdvancedAE's NBT key for a piece's AE charge. */
    private static final String CHARGE = "internalCurrentPower";
    private static final double CHARGE_EACH = 1_000_000;

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void quantum_absorption_plan_pays_a_quarter_per_piece_at_1000_ae_a_point_and_the_rules_and_gate_hold(GameTestHelper h) {
        double[] full = {CHARGE_EACH, CHARGE_EACH, CHARGE_EACH, CHARGE_EACH};
        QuantumAbsorption.Plan two = QuantumAbsorption.plan(2.0f, 30, full);
        h.assertTrue(near(two.absorbed(), 0.6) && allNear(two.paid(), 150) && near(two.energy(), 600), "2.0 after the armor: 0.6 absorbed, 150 AE from each of four pieces, found " + two);
        QuantumAbsorption.Plan ten = QuantumAbsorption.plan(10f, 30, full);
        h.assertTrue(near(ten.absorbed(), 3.0) && allNear(ten.paid(), 750), "10 points: 3.0 absorbed for 750 AE each, found " + ten);
        QuantumAbsorption.Plan poor = QuantumAbsorption.plan(2.0f, 30, new double[]{100, CHARGE_EACH, 0, CHARGE_EACH});
        h.assertTrue(near(poor.absorbed(), 0.4) && near(poor.paid()[0], 100) && near(poor.paid()[2], 0) && near(poor.paid()[1], 150), "a piece pays what it has and nothing is redistributed, found " + poor);
        QuantumAbsorption.Plan three = QuantumAbsorption.plan(2.0f, 30, new double[]{CHARGE_EACH, CHARGE_EACH, CHARGE_EACH});
        h.assertTrue(near(three.absorbed(), 0.45) && allNear(three.paid(), 150), "three pieces worn: three quarters, found " + three);
        h.assertTrue(QuantumAbsorption.plan(2.0f, 30, new double[0]).equals(QuantumAbsorption.Plan.NOTHING), "no quantum piece, nothing");
        h.assertTrue(QuantumAbsorption.plan(0f, 30, full).equals(QuantumAbsorption.Plan.NOTHING) && QuantumAbsorption.plan(2f, 0, full).equals(QuantumAbsorption.Plan.NOTHING), "no damage or no percentage, nothing");
        h.assertTrue(near(QuantumAbsorption.plan(2.0f, 30, new double[]{Double.NaN, -5, CHARGE_EACH, CHARGE_EACH}).absorbed(), 0.3), "NaN and negative charges pay nothing");
        h.assertTrue(QuantumAbsorption.cancelsUnder(0.99f) && !QuantumAbsorption.cancelsUnder(1.0f), "AdvancedAE's rule: under one point the hit is cancelled");
        h.assertTrue(!QuantumAbsorption.worthReporting(QuantumAbsorption.Plan.NOTHING) && QuantumAbsorption.worthReporting(two), "the wearer is told only when something was absorbed");
        Map<String, String> ours = read("/assets/bsp_mod_fixes/lang/en_us.json");
        h.assertTrue("DMG".equals(ours.get("hud.bsp_mod_fixes.quantum_dmg")) && "AE".equals(ours.get("hud.bsp_mod_fixes.quantum_ae"))
                && ours.containsKey("key.bsp_mod_fixes.quantum_readout") && ours.containsKey("key.categories.bsp_mod_fixes"), "this jar's lang file carries the readout's labels and key names");

        h.assertTrue(AaeGate.verdict(Optional.of(AaeGate.VERSION)).applies(), "exactly " + AaeGate.VERSION + " applies");
        h.assertTrue(AaeGate.verdict(Optional.of("1.3.5-1.20.1")) == AaeGate.Verdict.OTHER_VERSION, "an older build is skipped");
        h.assertTrue(AaeGate.verdict(Optional.empty()) == AaeGate.Verdict.ABSENT, "absent is skipped");
        h.assertTrue(AaeGate.gates("AaeDamageAbsorptionMixin") && !AaeGate.gates("JeiStartObserverMixin"), "the gate decides for its own mixin only");
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void quantum_armor_pays_ae_for_30_percent_of_the_damage_left_after_the_armor_and_tells_the_wearer(GameTestHelper h) {
        if (!ModList.get().isLoaded(AaeGate.MOD_ID)) {
            Map<String, String> skipped = Fixes.skipped();
            h.assertTrue(skipped.containsKey(QuantumAbsorption.ID) && skipped.get(QuantumAbsorption.ID).equals(AaeGate.Verdict.ABSENT.reason()),
                    "without AdvancedAE the plugin skipped F-25 with the absent reason, got " + skipped);
            Fixes.LOGGER.info("BSP-Mod Fixes test: AdvancedAE is not loaded, the quantum armor hit is not run (add -PwithAae)");
            h.succeed();
            return;
        }
        h.assertTrue(BSPModFixes.version(AaeGate.MOD_ID).equals(AaeGate.VERSION), "AdvancedAE " + AaeGate.VERSION + ", found " + BSPModFixes.version(AaeGate.MOD_ID));
        // With -PskipFixes=AaeDamageAbsorptionMixin the hit goes through AdvancedAE's own handler: the same numbers, no line.
        boolean fixOn = Fixes.active().stream().anyMatch(f -> f.id().equals(QuantumAbsorption.ID));
        h.assertTrue(fixOn || Fixes.skipped().containsKey(QuantumAbsorption.ID), "F-25 is applied (AdvancedAE's handler class loads at its setup) or switched off for this run: " + Fixes.active() + " " + Fixes.skipped());
        Fixes.LOGGER.info("BSP-Mod Fixes test: the quantum armor hit runs against {}", fixOn ? "F-25" : "AdvancedAE's own handler (F-25 switched off)");
        int reportEach = fixOn ? 1 : 0;

        ServerPlayer player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        player.moveTo(h.absoluteVec(new Vec3(12, 2, 12)));
        for (int i = 0; i < PIECES.length; i++) {
            ItemStack piece = new ItemStack(ForgeRegistries.ITEMS.getValue(PIECES[i]));
            h.assertTrue(!piece.isEmpty(), PIECES[i] + " exists");
            piece.getOrCreateTag().putDouble(CHARGE, CHARGE_EACH);
            player.setItemSlot(SLOTS[i], piece);
            // vanilla applies a worn piece's attributes when the player ticks; a mock player never does, so it is done here as its equipment handling would
            player.getAttributes().addTransientAttributeModifiers(piece.getAttributeModifiers(SLOTS[i]));
        }
        h.assertTrue(player.getArmorValue() == 23, "the quantum set is worth 23 armor points, found " + player.getArmorValue());
        h.assertTrue(near(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS), 20), "and 10 toughness a piece, capped by vanilla's attribute at 20, found " + player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS));
        player.setHealth(20);
        ((ServerPlayerAccessor) player).bsp_mod_fixes$setSpawnInvulnerableTime(0); // vanilla's 60 ticks of spawn protection refuse any hit
        int reports = QuantumAbsorption.reportedCount();

        // 10 raw from a source the armor counts against (vanilla's generic damage bypasses armor; a cactus does not): the armor (23 points,
        // toughness 20, vanilla's cap of 80 % for any hit up to 21) leaves 2.0; the suit takes 30 % of that for 150 AE a piece
        h.assertTrue(player.hurt(h.getLevel().damageSources().cactus(), 10f), "the hit landed");
        h.assertTrue(near(player.getHealth(), 20 - 1.4), "1.4 dealt of 10 (2.0 after the armor, 0.6 absorbed), health " + player.getHealth());
        for (int i = 0; i < PIECES.length; i++) {
            double drop = CHARGE_EACH - player.getItemBySlot(SLOTS[i]).getOrCreateTag().getDouble(CHARGE);
            h.assertTrue(drop >= 150 && drop < 170, PIECES[i].getPath() + " paid 150 AE for its quarter (plus AdvancedAE's few AE of durability), found " + drop);
        }
        h.assertTrue(QuantumAbsorption.reportedCount() == reports + reportEach, "one report to the wearer for the hit (none with the fix off)");

        // 3 raw (a zombie): 0.6 after the armor, 0.18 absorbed for 45 AE a piece, then under one point and cancelled altogether (AdvancedAE's rule, D38)
        player.invulnerableTime = 0;
        player.setHealth(20);
        double before = player.getItemBySlot(EquipmentSlot.CHEST).getOrCreateTag().getDouble(CHARGE);
        player.hurt(h.getLevel().damageSources().cactus(), 3f);
        h.assertTrue(player.getHealth() == 20, "a zombie's 3 ends under one point and is cancelled, health " + player.getHealth());
        double paid = before - player.getItemBySlot(EquipmentSlot.CHEST).getOrCreateTag().getDouble(CHARGE);
        h.assertTrue(paid >= 45 && paid < 60, "the chestplate still paid its 45 AE, found " + paid);
        h.assertTrue(QuantumAbsorption.reportedCount() == reports + 2 * reportEach, "a report for that one too");

        // an empty suit absorbs nothing and says nothing
        for (EquipmentSlot slot : SLOTS) {
            player.getItemBySlot(slot).getOrCreateTag().putDouble(CHARGE, 0);
        }
        player.invulnerableTime = 0;
        player.setHealth(20);
        player.hurt(h.getLevel().damageSources().cactus(), 10f);
        h.assertTrue(near(player.getHealth(), 18), "with no charge the full 2.0 is dealt, health " + player.getHealth());
        h.assertTrue(QuantumAbsorption.reportedCount() == reports + 2 * reportEach, "no report without an absorption");
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void suit_readout_slides_with_the_armor_and_the_tab_and_clears_the_damage_row_three_seconds_after_a_hit(GameTestHelper h) {
        h.assertTrue(ReadoutState.targetOffset(true, false) == ReadoutState.OPEN, "worn and open: the plate is on screen");
        h.assertTrue(ReadoutState.targetOffset(true, true) == ReadoutState.COLLAPSED && ReadoutState.visible(ReadoutState.COLLAPSED), "worn and collapsed: off to the tab, which stays visible");
        h.assertTrue(ReadoutState.targetOffset(false, false) == ReadoutState.HIDDEN && ReadoutState.targetOffset(false, true) == ReadoutState.HIDDEN && !ReadoutState.visible(ReadoutState.HIDDEN), "not worn: nothing, collapsed or not");
        float offset = ReadoutState.HIDDEN;
        int ticks = 0;
        while (offset < ReadoutState.OPEN && ticks < 100) {
            offset = ReadoutState.step(offset, ReadoutState.OPEN);
            ticks++;
        }
        h.assertTrue(offset == ReadoutState.OPEN && ticks == ReadoutState.SLIDE_TICKS, "the slide in takes " + ReadoutState.SLIDE_TICKS + " ticks and stops exactly at open, took " + ticks);
        h.assertTrue(ReadoutState.step(ReadoutState.OPEN, ReadoutState.OPEN) == ReadoutState.OPEN, "at the target nothing moves");
        h.assertTrue(ReadoutState.step(ReadoutState.OPEN, ReadoutState.COLLAPSED) < ReadoutState.OPEN, "collapsing moves left");

        h.assertTrue(ReadoutState.hitRowAlpha(100, -1) == 0, "no hit, no row");
        h.assertTrue(ReadoutState.hitRowAlpha(100, 100) == 1 && ReadoutState.hitRowAlpha(140, 100) == 1, "full for the first " + (ReadoutState.HIT_ROW_TICKS - ReadoutState.HIT_ROW_FADE_TICKS) + " ticks");
        float mid = ReadoutState.hitRowAlpha(100 + ReadoutState.HIT_ROW_TICKS - ReadoutState.HIT_ROW_FADE_TICKS / 2, 100);
        h.assertTrue(mid > 0 && mid < 1, "fading in the last " + ReadoutState.HIT_ROW_FADE_TICKS + " ticks, found " + mid);
        h.assertTrue(ReadoutState.hitRowAlpha(100 + ReadoutState.HIT_ROW_TICKS, 100) == 0, "gone three seconds after the hit (D50)");

        int[] tab = ReadoutState.tabRect(ReadoutState.OPEN, 270, ReadoutState.DEFAULT_LEFT_HEIGHT);
        h.assertTrue(tab[0] == ReadoutState.PLATE_W && tab[2] == ReadoutState.TAB_W && tab[3] == ReadoutState.TAB_H, "the tab sits on the plate's right edge: " + java.util.Arrays.toString(tab));
        h.assertTrue(tab[1] == ReadoutState.plateY(270, ReadoutState.DEFAULT_LEFT_HEIGHT) + (ReadoutState.PLATE_H - ReadoutState.TAB_H) / 2, "vertically centred on the plate");
        h.assertTrue(ReadoutState.onTab(ReadoutState.OPEN, 270, 49, tab[0] + 4, tab[1] + 10) && !ReadoutState.onTab(ReadoutState.OPEN, 270, 49, tab[0] - 1, tab[1] + 10), "a click on the tab counts, one beside it does not");
        int[] collapsedTab = ReadoutState.tabRect(ReadoutState.COLLAPSED, 270, 49);
        h.assertTrue(collapsedTab[0] == 0, "collapsed, the tab is at the screen's left edge");
        h.assertTrue(ReadoutState.plateY(240, 49) + ReadoutState.PLATE_H == 240 - 49 - ReadoutState.GAP, "the plate's bottom clears the rows the HUD reports (49 here) on the smallest window");
        h.assertTrue(ReadoutState.plateY(240, 69) == ReadoutState.plateY(240, 49) - 20 && ReadoutState.plateY(240, 10) == ReadoutState.plateY(240, 49), "it rises with stacked rows and never below the vanilla two");

        h.assertTrue(ReadoutState.fillWidth(0.3) == 12 && ReadoutState.fillWidth(1) == ReadoutState.BAR_W && ReadoutState.fillWidth(2) == ReadoutState.BAR_W && ReadoutState.fillWidth(-1) == 0 && ReadoutState.fillWidth(Double.NaN) == 0 && ReadoutState.fillWidth(0.004) == 1, "bar fills, a charge above nothing showing one pixel");
        h.assertTrue(ReadoutState.notchWidth(600, 950_000_000, ReadoutState.BAR_W) == ReadoutState.MIN_NOTCH, "600 AE of 950 million is drawn as the minimum notch");
        h.assertTrue(ReadoutState.notchWidth(475_000_000, 950_000_000, ReadoutState.BAR_W) == ReadoutState.BAR_W / 2, "half the charge is half the bar");
        h.assertTrue(ReadoutState.notchWidth(600, 950_000_000, 0) == 0 && ReadoutState.notchWidth(0, 950_000_000, 20) == 0, "no fill or nothing paid: no notch");
        h.assertTrue(ReadoutState.chargeFraction(950_000_000, 950_000_000) == 1 && ReadoutState.chargeFraction(0, 950_000_000) == 0 && ReadoutState.chargeFraction(5, 0) == 0, "charge fractions");
        h.assertTrue("0.6/2.0".equals(ReadoutState.hitText(2.0f, 0.6f)) && "-600 AE".equals(ReadoutState.paidText(600)) && "-1,200 AE".equals(ReadoutState.paidText(1200.4))
                && "100%".equals(ReadoutState.percentText(1)) && "950,000,000 AE".equals(ReadoutState.chargeText(950_000_000)), "the plate's texts");
        h.succeed();
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-3;
    }

    private static boolean allNear(double[] values, double expected) {
        for (double v : values) {
            if (!near(v, expected)) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, String> read(String resource) {
        try (Reader in = new InputStreamReader(BSPModFixes.class.getResourceAsStream(resource), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(in, new TypeToken<Map<String, String>>() {}.getType());
        } catch (Exception e) {
            throw new AssertionError("cannot read " + resource + ": " + e, e);
        }
    }
}
