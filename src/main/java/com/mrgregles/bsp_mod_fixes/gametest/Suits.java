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

    static IModularItem modular(ServerPlayer player, EquipmentSlot slot) {
        return player.getItemBySlot(slot).getCapability(ForgeCapabilities.ITEM_HANDLER)
                .filter(IModularItem.class::isInstance).map(IModularItem.class::cast)
                .orElseThrow(() -> new AssertionError("no modular item in the " + slot.getName() + " slot"));
    }

    /** Installs the module into the first slot that takes it, as the creative-install handler does. */
    static void install(IModularItem item, ResourceLocation module) {
        ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(module));
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
