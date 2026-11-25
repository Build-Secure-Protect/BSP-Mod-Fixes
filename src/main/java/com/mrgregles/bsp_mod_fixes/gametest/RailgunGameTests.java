package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.MixinFixPlugin;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.powersuits.RailgunShot;
import com.mrgregles.bsp_mod_fixes.fix.powersuits.SilkTouchConfigKey;
import lehjr.numina.common.capabilities.NuminaCapabilities;
import lehjr.numina.common.capabilities.inventory.modechanging.IModeChangingItem;
import lehjr.numina.common.capabilities.module.powermodule.IPowerModule;
import lehjr.numina.common.capabilities.module.powermodule.PowerModule;
import lehjr.numina.common.tags.TagUtils;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * MF-42: the Power Fist's Mining Enchantment config keys (F-45), the Rail Gun's item use (F-46), and the measurement of the
 * Rail Gun's single shot (the record is in docs/TESTING.md). The fist and its modules are the real Powersuits items on a mock
 * player; the Rail Gun is fired through the item's own {@code use}, as a right click would. With
 * {@code -PskipFixes=NuminaConfigTitleMixin,PowersuitsRailgunUseMixin} the tests assert Powersuits as shipped instead.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class RailgunGameTests {
    private static final String COOLDOWN = "cooldown";

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void mining_enchantment_modules_read_their_config_under_the_section_powersuits_writes(GameTestHelper h) {
        h.assertTrue(SilkTouchConfigKey.configTitle("Mining_Enchantment").equals("Mining_Enhancement"), "the rule: the enchantment title becomes the enhancement title");
        h.assertTrue(SilkTouchConfigKey.configTitle("Mining_Enhancement").equals("Mining_Enhancement") && SilkTouchConfigKey.configTitle("Weapon").equals("Weapon")
                && SilkTouchConfigKey.configTitle("Mining Enchantment").equals("Mining Enchantment"), "every other title is itself");

        boolean fixOn = !Fixes.skipped().containsKey(SilkTouchConfigKey.ID);
        h.assertTrue(fixOn || Fixes.skipped().get(SilkTouchConfigKey.ID).equals(MixinFixPlugin.SKIP_REASON), "F-45 is applied or switched off for this run: " + Fixes.skipped());
        long before = SilkTouchConfigKey.redirected();
        // bare module stacks: a module's keys are built when its capability is, whether or not it sits in a fist
        PowerModule silk = module(Suits.SILK_TOUCH);
        PowerModule fortune = module(Suits.FORTUNE);
        PowerModule aqua = module(Suits.AQUA_AFFINITY);
        List<String> key = silk.getConfigKey("base_silkTouchEnCon");
        double cost = silk.applyPropertyModifiers("silkTouchEnCon");
        String section = fixOn ? SilkTouchConfigKey.SPEC_TITLE : SilkTouchConfigKey.MODULE_TITLE;
        h.assertTrue(key.equals(List.of("Modules", section, "silk_touch", "base_silkTouchEnCon")), "the Silk Touch key is built under " + section + ", got " + key);
        h.assertTrue(fortune.getConfigKey("isAllowed").get(1).equals(section) && aqua.getConfigKey("isAllowed").get(1).equals(section),
                "Fortune and Aqua Affinity are built under the same section: " + fortune.getConfigKey("isAllowed") + " " + aqua.getConfigKey("isAllowed"));
        PowerModule railgun = module(Suits.RAILGUN);
        h.assertTrue(railgun.getConfigKey("isAllowed").get(1).equals("Weapon"), "a module of another category keeps its title: " + railgun.getConfigKey("isAllowed"));
        if (fixOn) {
            // 2,500 is Powersuits' spec default, written into the dev world's serverconfig/powersuits-server.toml under
            // [Modules.Mining_Enhancement.silk_touch]; the point is that it is the file's value, not the code's 50,000
            h.assertTrue(cost == 2500.0, "with the fix the Silk Touch energy cost is the file's 2,500 FE a tick, got " + cost);
            h.assertTrue(SilkTouchConfigKey.redirected() >= before + 3, "three modules had their title moved, " + (SilkTouchConfigKey.redirected() - before));
        } else {
            h.assertTrue(cost == 50000.0, "as shipped the Silk Touch energy cost is the code's 50,000 FE a tick (the config's key is never read), got " + cost);
            h.assertTrue(SilkTouchConfigKey.redirected() == before, "nothing moved with the mixin off");
        }
        h.assertTrue(silk.isAllowed() && fortune.isAllowed() && aqua.isAllowed(), "the three modules are allowed (isAllowed = true in the file, and the default without a key)");
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void the_rail_gun_fires_without_starting_an_item_use(GameTestHelper h) {
        h.assertFalse(RailgunShot.startsItemUse(), "the rule: the Rail Gun's use never starts an item use");
        boolean fixOn = !Fixes.skipped().containsKey(RailgunShot.ID);
        h.assertTrue(fixOn || Fixes.skipped().get(RailgunShot.ID).equals(MixinFixPlugin.SKIP_REASON), "F-46 is applied or switched off for this run: " + Fixes.skipped());

        ServerPlayer player = Suits.holding(h, Suits.BATTERY, Suits.RAILGUN);
        Suits.charge(player, 1_000_000);
        ItemStack fist = player.getMainHandItem();
        IModeChangingItem handler = fistHandler(fist);
        int railgunSlot = handler.findInstalledModule(Suits.RAILGUN);
        handler.setActiveMode(railgunSlot);
        h.assertTrue(ForgeRegistries.ITEMS.getKey(handler.getActiveModule().getItem()).equals(Suits.RAILGUN), "the Rail Gun is the fist's active mode: slot " + railgunSlot
                + ", mode " + handler.getActiveMode() + ", active module " + handler.getActiveModule() + ", valid modes " + handler.getValidModes() + ", in slot " + handler.getStackInSlot(Math.max(railgunSlot, 0))
                + ", allowed " + handler.getStackInSlot(Math.max(railgunSlot, 0)).getCapability(NuminaCapabilities.POWER_MODULE).map(IPowerModule::isAllowed).orElse(null)
                + ", slot 1 " + handler.getStackInSlot(1) + " " + handler.getStackInSlot(1).getTag() + ", fist tag " + fist.getTag());
        h.assertTrue(!player.isUsingItem() && TagUtils.getModularItemDoubleOrZero(fist, COOLDOWN) == 0.0, "before the click: no item use, no cooldown");

        double energy = Suits.energy(player);
        long skipped = RailgunShot.skipped();
        InteractionResultHolder<ItemStack> result = fist.getItem().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(result.getResult() == InteractionResult.SUCCESS, "the click is a success, got " + result.getResult());
        h.assertTrue(TagUtils.getModularItemDoubleOrZero(fist, COOLDOWN) == 15.0, "the shot went off: the cooldown is 15, got " + TagUtils.getModularItemDoubleOrZero(fist, COOLDOWN));
        h.assertTrue(Suits.energy(player) < energy, "the shot drained energy: " + energy + " -> " + Suits.energy(player));
        if (fixOn) {
            h.assertTrue(!player.isUsingItem() && player.getUseItem().isEmpty(), "with the fix the player is not using the fist after the shot");
            h.assertTrue(RailgunShot.skipped() == skipped + 1, "one item use skipped, got " + (RailgunShot.skipped() - skipped));
        } else {
            h.assertTrue(player.isUsingItem() && player.getUseItem() == fist && player.getUseItemRemainingTicks() == 25,
                    "as shipped the player uses the fist for 25 ticks (the bow pose): using " + player.isUsingItem() + ", " + player.getUseItemRemainingTicks());
            player.stopUsingItem();
        }

        // a second click within the cooldown: nothing fires, and with the fix still no item use (as shipped the pose restarts)
        double afterShot = Suits.energy(player);
        result = fist.getItem().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(result.getResult() == InteractionResult.SUCCESS && Suits.energy(player) == afterShot && TagUtils.getModularItemDoubleOrZero(fist, COOLDOWN) == 15.0,
                "the second click is a success without a shot (cooldown running)");
        h.assertTrue(player.isUsingItem() != fixOn, "item use after the second click: " + player.isUsingItem() + " (fix " + (fixOn ? "on" : "off") + ")");
        if (fixOn) {
            h.assertTrue(RailgunShot.skipped() == skipped + 2, "two item uses skipped, got " + (RailgunShot.skipped() - skipped));
        }
        h.succeed();
    }

    /**
     * MF-42, the measurement behind F-47 (asserts only what the two fixes need; everything else goes to the log as "MF-42:").
     * What the Rail Gun's cooldown, the module's "Active" tag and {@code isModuleOnline()} do across clicks, ticks, a mode
     * change, another toggleable module's construction and a re-creation of the fist's stack.
     */
    @GameTest(template = ModFixesGameTests.EMPTY)
    public void mf42_the_rail_guns_single_shot_measured(GameTestHelper h) {
        // A: a fist with a battery and a Rail Gun, nothing else constructed between the module and its first use
        ServerPlayer player = Suits.holding(h, Suits.BATTERY, Suits.RAILGUN);
        Suits.charge(player, 1_000_000);
        ItemStack fist = player.getMainHandItem();
        IModeChangingItem handler = fistHandler(fist);
        int slot = handler.findInstalledModule(Suits.RAILGUN);
        handler.setActiveMode(slot);
        IPowerModule gun = Suits.module(player, EquipmentSlot.MAINHAND, Suits.RAILGUN);
        log(h, "A fresh fist: online=%s Active=%s", gun.isModuleOnline(), activeTag(handler.getStackInSlot(slot)));
        click(h, player, fist, "A first click");
        tick(player, 20);
        log(h, "A after 20 ticks: cooldown=%s online=%s", TagUtils.getModularItemDoubleOrZero(fist, COOLDOWN), gun.isModuleOnline());
        click(h, player, fist, "A second click");

        // B: a Rail Gun stack constructed, then another toggleable module (Silk Touch, default off), then the Rail Gun installed: its
        // Active tag is written at install (the insert resolves the capability) from Numina's static default of that moment
        ServerPlayer player2 = Suits.holding(h, Suits.BATTERY);
        Suits.charge(player2, 1_000_000);
        ItemStack fist2 = player2.getMainHandItem();
        IModeChangingItem handler2 = fistHandler(fist2);
        ItemStack railgun2 = new ItemStack(ForgeRegistries.ITEMS.getValue(Suits.RAILGUN)); // its Ticker is built at its first lookup, not here
        ItemStack silk = new ItemStack(ForgeRegistries.ITEMS.getValue(Suits.SILK_TOUCH));
        boolean silkOnlineB = silk.getCapability(NuminaCapabilities.POWER_MODULE).map(IPowerModule::isModuleOnline).orElseThrow(AssertionError::new); // its TickingEnchantment is built now: defBool = false
        Suits.install(Suits.modular(player2, EquipmentSlot.MAINHAND), railgun2); // the insert copies the stack and looks the copy up
        int slot2 = handler2.findInstalledModule(Suits.RAILGUN);
        handler2.setActiveMode(slot2);
        IPowerModule gun2 = Suits.module(player2, EquipmentSlot.MAINHAND, Suits.RAILGUN);
        log(h, "B silk touch looked up (online=%s) before the gun's install: gun online=%s Active=%s", silkOnlineB, gun2.isModuleOnline(), activeTag(handler2.getStackInSlot(slot2)));
        click(h, player2, fist2, "B first click");
        tick(player2, 20);
        log(h, "B after 20 ticks: cooldown=%s online=%s", TagUtils.getModularItemDoubleOrZero(fist2, COOLDOWN), gun2.isModuleOnline());
        click(h, player2, fist2, "B second click");
        log(h, "B the fist's tag before a mode change: %s", fist2.getOrCreateTag());
        handler2.setActiveMode(slot2);
        log(h, "B right after the mode change: cooldown=%s", TagUtils.getModularItemDoubleOrZero(fist2, COOLDOWN));
        tick(player2, 20);
        log(h, "B after a mode change and 20 more ticks: cooldown=%s online=%s Active=%s", TagUtils.getModularItemDoubleOrZero(fist2, COOLDOWN), gun2.isModuleOnline(), activeTag(handler2.getStackInSlot(slot2)));
        click(h, player2, fist2, "B third click");
        log(h, "B the fist's saved module tag after the mode change: %s", savedModuleTag(fist2, slot2));

        // C: the fist's stack re-created from its NBT (as a save and load, or a copy, does): the modules are built again in slot order
        ServerPlayer player3 = Suits.holding(h, Suits.BATTERY, Suits.RAILGUN, Suits.SILK_TOUCH);
        ItemStack saved = ItemStack.of(player3.getMainHandItem().save(new CompoundTag()));
        IModeChangingItem handler3 = fistHandler(saved);
        int slot3 = handler3.findInstalledModule(Suits.RAILGUN);
        IPowerModule gun3 = handler3.getStackInSlot(slot3).getCapability(NuminaCapabilities.POWER_MODULE).orElseThrow(AssertionError::new);
        log(h, "C fist re-created with a Rail Gun in slot %d and Silk Touch in slot %d: gun online=%s Active=%s", slot3, handler3.findInstalledModule(Suits.SILK_TOUCH), gun3.isModuleOnline(), activeTag(handler3.getStackInSlot(slot3)));
        ServerPlayer player4 = Suits.holding(h, Suits.BATTERY, Suits.SILK_TOUCH, Suits.RAILGUN);
        ItemStack saved4 = ItemStack.of(player4.getMainHandItem().save(new CompoundTag()));
        IModeChangingItem handler4 = fistHandler(saved4);
        int slot4 = handler4.findInstalledModule(Suits.RAILGUN);
        IPowerModule gun4 = handler4.getStackInSlot(slot4).getCapability(NuminaCapabilities.POWER_MODULE).orElseThrow(AssertionError::new);
        log(h, "C fist re-created with Silk Touch in slot %d and the Rail Gun in slot %d: gun online=%s Active=%s", handler4.findInstalledModule(Suits.SILK_TOUCH), slot4, gun4.isModuleOnline(), activeTag(handler4.getStackInSlot(slot4)));

        // D: a fist whose Rail Gun was once online and has its Active tag saved, re-created after a Silk Touch construction
        handler.setActiveMode(slot); // onContentsChanged: the fist's NBT now carries the module tags of A
        log(h, "D fist A's saved module tag: %s", savedModuleTag(fist, slot));
        new ItemStack(ForgeRegistries.ITEMS.getValue(Suits.SILK_TOUCH));
        ItemStack savedA = ItemStack.of(fist.save(new CompoundTag()));
        IModeChangingItem handlerA = fistHandler(savedA);
        IPowerModule gunA = handlerA.getStackInSlot(slot).getCapability(NuminaCapabilities.POWER_MODULE).orElseThrow(AssertionError::new);
        log(h, "D fist A re-created after a Silk Touch construction: gun online=%s Active=%s", gunA.isModuleOnline(), activeTag(handlerA.getStackInSlot(slot)));

        // E: a Rail Gun stack in a player's inventory, looked at (a tooltip, a menu's validity check) after another toggleable
        // module was constructed, then installed: the "Active" tag it carries into the fist is the static default of the lookup
        ServerPlayer player5 = Suits.holding(h, Suits.BATTERY);
        Suits.charge(player5, 1_000_000);
        ItemStack fist5 = player5.getMainHandItem();
        // Forge builds a stack's capabilities at the first lookup, not at construction, so the module objects (and Numina's static
        // default) come into being in lookup order: the Silk Touch is looked up first (defBool = false), then the Rail Gun
        ItemStack railgun5 = new ItemStack(ForgeRegistries.ITEMS.getValue(Suits.RAILGUN));
        ItemStack silk5 = new ItemStack(ForgeRegistries.ITEMS.getValue(Suits.SILK_TOUCH));
        boolean silkOnline = silk5.getCapability(NuminaCapabilities.POWER_MODULE).map(IPowerModule::isModuleOnline).orElseThrow(AssertionError::new);
        boolean onlineAtLookup = railgun5.getCapability(NuminaCapabilities.POWER_MODULE).map(IPowerModule::isModuleOnline).orElseThrow(AssertionError::new);
        log(h, "E a Rail Gun stack looked up after a Silk Touch lookup (silk online=%s): online=%s Active=%s", silkOnline, onlineAtLookup, activeTag(railgun5));
        Suits.install(Suits.modular(player5, EquipmentSlot.MAINHAND), railgun5);
        IModeChangingItem handler5 = fistHandler(fist5);
        int slot5 = handler5.findInstalledModule(Suits.RAILGUN);
        handler5.setActiveMode(slot5);
        IPowerModule gun5 = Suits.module(player5, EquipmentSlot.MAINHAND, Suits.RAILGUN);
        log(h, "E installed: online=%s Active=%s saved=%s", gun5.isModuleOnline(), activeTag(handler5.getStackInSlot(slot5)), savedModuleTag(fist5, slot5));
        click(h, player5, fist5, "E first click");
        tick(player5, 20);
        log(h, "E after 20 ticks: cooldown=%s online=%s", TagUtils.getModularItemDoubleOrZero(fist5, COOLDOWN), gun5.isModuleOnline());
        click(h, player5, fist5, "E second click");
        handler5.setActiveMode(slot5);
        log(h, "E right after a mode change: cooldown=%s", TagUtils.getModularItemDoubleOrZero(fist5, COOLDOWN));
        click(h, player5, fist5, "E third click");
        h.succeed();
    }

    /** A freshly constructed module stack's power module. */
    private static PowerModule module(ResourceLocation module) {
        return (PowerModule) new ItemStack(ForgeRegistries.ITEMS.getValue(module)).getCapability(NuminaCapabilities.POWER_MODULE)
                .orElseThrow(() -> new AssertionError(module + " has no power module capability"));
    }

    private static IModeChangingItem fistHandler(ItemStack fist) {
        return fist.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER)
                .filter(IModeChangingItem.class::isInstance).map(IModeChangingItem.class::cast)
                .orElseThrow(() -> new AssertionError("the fist has no mode-changing item handler"));
    }

    private static void click(GameTestHelper h, ServerPlayer player, ItemStack fist, String what) {
        double energy = Suits.energy(player);
        InteractionResultHolder<ItemStack> result = fist.getItem().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        log(h, "%s: result=%s fired=%s cooldown=%s usingItem=%s", what, result.getResult(), Suits.energy(player) < energy, TagUtils.getModularItemDoubleOrZero(fist, COOLDOWN), player.isUsingItem());
        player.stopUsingItem();
    }

    /** Numina's player tick handler, {@code count} START ticks (the fist's modules tick through it; F-3 lets START through). */
    private static void tick(ServerPlayer player, int count) {
        for (int i = 0; i < count; i++) {
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, player));
        }
    }

    private static String activeTag(ItemStack module) {
        CompoundTag tag = TagUtils.getModuleTag(module);
        return tag.contains("Active") ? String.valueOf(tag.getBoolean("Active")) : "absent";
    }

    /** The module's tag as saved in the fist's NBT (what a re-created fist would give the module). */
    private static String savedModuleTag(ItemStack fist, int slot) {
        CompoundTag items = fist.getOrCreateTag().getCompound("MMModItem");
        for (net.minecraft.nbt.Tag t : items.getList("Items", 10)) {
            CompoundTag item = (CompoundTag) t;
            if (item.getInt("Slot") == slot) {
                return item.getCompound("tag").toString();
            }
        }
        return "no saved stack in slot " + slot;
    }

    private static void log(GameTestHelper h, String format, Object... args) {
        Fixes.LOGGER.info("MF-42: " + String.format(format, args));
    }
}
