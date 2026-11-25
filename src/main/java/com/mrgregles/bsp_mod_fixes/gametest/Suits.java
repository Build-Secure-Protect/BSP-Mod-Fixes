package com.mrgregles.bsp_mod_fixes.gametest;

import lehjr.numina.common.capabilities.NuminaCapabilities;
import lehjr.numina.common.capabilities.inventory.modularitem.IModularItem;
import lehjr.numina.common.capabilities.module.powermodule.IPowerModule;
import lehjr.numina.common.energy.ElectricItemUtils;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;

/** Mock players dressed in power armor with modules, for the module tests. Registry names are Powersuits' and Numina's. */
final class Suits {
    static final ResourceLocation HELMET = ResourceLocation.fromNamespaceAndPath("powersuits", "powerarmor_head");
    static final ResourceLocation CHESTPLATE = ResourceLocation.fromNamespaceAndPath("powersuits", "powerarmor_torso");
    static final ResourceLocation LEGGINGS = ResourceLocation.fromNamespaceAndPath("powersuits", "powerarmor_legs");
    static final ResourceLocation BOOTS = ResourceLocation.fromNamespaceAndPath("powersuits", "powerarmor_feet");
    static final ResourceLocation BATTERY = ResourceLocation.fromNamespaceAndPath("numina", "battery_basic");
    static final ResourceLocation NIGHT_VISION = ResourceLocation.fromNamespaceAndPath("powersuits", "night_vision");
    static final ResourceLocation KINETIC_GENERATOR = ResourceLocation.fromNamespaceAndPath("powersuits", "generator_kinetic");
    static final ResourceLocation SHOCK_ABSORBER = ResourceLocation.fromNamespaceAndPath("powersuits", "shock_absorber");
    static final ResourceLocation ENERGY_SHIELD = ResourceLocation.fromNamespaceAndPath("powersuits", "energy_shield");
    static final ResourceLocation GLIDER = ResourceLocation.fromNamespaceAndPath("powersuits", "glider");
    static final ResourceLocation SPRINT_ASSIST = ResourceLocation.fromNamespaceAndPath("powersuits", "sprint_assist");
    static final ResourceLocation JUMP_ASSIST = ResourceLocation.fromNamespaceAndPath("powersuits", "jump_assist");
    static final ResourceLocation POWER_FIST = ResourceLocation.fromNamespaceAndPath("powersuits", "powerfist");
    static final ResourceLocation RAILGUN = ResourceLocation.fromNamespaceAndPath("powersuits", "railgun");
    static final ResourceLocation SILK_TOUCH = ResourceLocation.fromNamespaceAndPath("powersuits", "silk_touch");
    static final ResourceLocation FORTUNE = ResourceLocation.fromNamespaceAndPath("powersuits", "fortune");
    static final ResourceLocation AQUA_AFFINITY = ResourceLocation.fromNamespaceAndPath("powersuits", "aqua_affinity");

    private Suits() {}

    /** A survival mock player standing in the test area, wearing {@code armor} in {@code slot} with {@code modules} installed. */
    static ServerPlayer wearing(GameTestHelper h, EquipmentSlot slot, ResourceLocation armor, ResourceLocation... modules) {
        ServerPlayer player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        player.moveTo(h.absoluteVec(new Vec3(12, 2, 12)));
        player.setItemSlot(slot, new ItemStack(ForgeRegistries.ITEMS.getValue(armor)));
        IModularItem item = modular(player, slot);
        for (ResourceLocation module : modules) {
            install(item, module);
        }
        return player;
    }

    /**
     * A survival mock player standing in the test area, holding a Power Fist in the main hand with {@code modules} installed.
     * Right-click modules go in first: Numina's {@code ModeChangingModularItem.serializeNBT} asks the active mode, and while
     * none is chosen (its static index is -1, until some fist with a saved mode is loaded) that reloads the fist's modules from
     * the fist's saved NBT, which for a second or later insert is the list of before that insert, so the module just inserted
     * is dropped and the re-save that follows writes the list without it (a first insert into a fresh fist has no saved NBT
     * yet and stays). With a right-click module in place the mode is chosen and the later inserts stay. Every module is
     * checked to be in the fist's NBT afterwards.
     */
    static ServerPlayer holding(GameTestHelper h, ResourceLocation... modules) {
        ServerPlayer player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        player.moveTo(h.absoluteVec(new Vec3(12, 2, 12)));
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ForgeRegistries.ITEMS.getValue(POWER_FIST)));
        IModularItem item = modular(player, EquipmentSlot.MAINHAND);
        java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
        for (ResourceLocation module : modules) {
            ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(module));
            boolean rightClick = stack.getCapability(NuminaCapabilities.POWER_MODULE)
                    .filter(lehjr.numina.common.capabilities.module.rightclick.IRightClickModule.class::isInstance).isPresent();
            stacks.add(rightClick ? 0 : stacks.size(), stack);
        }
        for (ItemStack stack : stacks) {
            install(item, stack);
        }
        ((lehjr.numina.common.capabilities.CapabilityUpdate) item).loadCapValues(); // the handler re-reads the fist's NBT, as any later capability access of the fist does
        for (ResourceLocation module : modules) {
            if (item.findInstalledModule(module) < 0) {
                throw new AssertionError(module + " is not in the fist's NBT after the install: " + player.getMainHandItem().getTag());
            }
        }
        return player;
    }

    static IModularItem modular(ServerPlayer player, EquipmentSlot slot) {
        return player.getItemBySlot(slot).getCapability(ForgeCapabilities.ITEM_HANDLER)
                .filter(IModularItem.class::isInstance).map(IModularItem.class::cast)
                .orElseThrow(() -> new AssertionError("no modular item in the " + slot.getName() + " slot"));
    }

    /** Installs the module into the first slot that takes it, as the creative-install handler does. */
    static void install(IModularItem item, ResourceLocation module) {
        install(item, new ItemStack(ForgeRegistries.ITEMS.getValue(module)));
    }

    /** Installs an already constructed module stack into the first slot that takes it. */
    static void install(IModularItem item, ItemStack stack) {
        ResourceLocation module = ForgeRegistries.ITEMS.getKey(stack.getItem());
        for (int i = 0; i < item.getSlots(); i++) {
            if (item.insertItem(i, stack, false).isEmpty()) {
                return;
            }
        }
        throw new AssertionError(module + " fits no slot of the item");
    }

    /** The installed module's power-module capability (the object whose tick and properties the fixes patch). */
    static IPowerModule module(ServerPlayer player, EquipmentSlot slot, ResourceLocation module) {
        IModularItem item = modular(player, slot);
        int index = item.findInstalledModule(module);
        if (index < 0) {
            throw new AssertionError(module + " is not installed");
        }
        return item.getStackInSlot(index).getCapability(NuminaCapabilities.POWER_MODULE)
                .orElseThrow(() -> new AssertionError(module + " has no power module capability"));
    }

    static double energy(ServerPlayer player) {
        return ElectricItemUtils.getPlayerEnergy(player);
    }

    static void charge(ServerPlayer player, int amount) {
        ElectricItemUtils.givePlayerEnergy(player, amount, false);
    }
}
