package com.mrgregles.bsp_mod_fixes.gametest;

import dev.su5ed.mffs.api.Projector;
import dev.su5ed.mffs.api.module.Module;
import dev.su5ed.mffs.blockentity.ForceFieldBlockEntity;
import dev.su5ed.mffs.blockentity.FortronBlockEntity;
import dev.su5ed.mffs.blockentity.ProjectorBlockEntity;
import dev.su5ed.mffs.setup.ModBlocks;
import dev.su5ed.mffs.setup.ModFluids;
import dev.su5ed.mffs.setup.ModItems;
import dev.su5ed.mffs.setup.ModModules;
import dev.su5ed.mffs.setup.ModObjects;
import dev.su5ed.mffs.util.ModUtil;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * MFFS's own classes for the MFFS tests, named here only, so the test classes load without the mod; a test touches this
 * class only after it has seen that MFFS is loaded ({@code -PwithMffs}).
 */
final class MffsTestBridge {
    private MffsTestBridge() {}

    static BlockState projector() {
        return ModBlocks.PROJECTOR.get().defaultBlockState();
    }

    /** Gives the projector the cube mode and {@code radius} Scale modules in its first upgrade slot (every axis, both ways). */
    static void setCubeOfRadius(BlockEntity be, int radius) {
        ProjectorBlockEntity projector = (ProjectorBlockEntity) be;
        projector.upgradeSlots.get(0).setItem(new ItemStack(ModItems.SCALE_MODULE.get(), radius));
        projector.projectorModeSlot.setItem(new ItemStack(ModItems.CUBE_MODE.get()));
    }

    static int positiveScaleX(BlockEntity be) {
        return ((Projector) be).getPositiveScale().getX();
    }

    /** Switches the projector on and fills its fortron store, as a powered projector beside a capacitor would be. */
    static void power(BlockEntity be) {
        FortronBlockEntity fortron = (FortronBlockEntity) be;
        fortron.fortronStorage.setCapacity(1_000_000);
        fortron.fortronStorage.setStoredFortron(1_000_000);
        fortron.setActive(true);
    }

    /** Swaps the projector's mode item: "cube", "sphere" or "cylinder"; the Scale modules stay where they are. */
    static void setMode(BlockEntity be, String kind) {
        Item item = switch (kind) {
            case "sphere" -> ModItems.SPHERE_MODE.get();
            case "cylinder" -> ModItems.CYLINDER_MODE.get();
            default -> ModItems.CUBE_MODE.get();
        };
        ((ProjectorBlockEntity) be).projectorModeSlot.setItem(new ItemStack(item));
    }

    /** Switches the projector off and empties its fortron store. */
    static void unpower(BlockEntity be) {
        FortronBlockEntity fortron = (FortronBlockEntity) be;
        fortron.setActive(false);
        fortron.fortronStorage.setStoredFortron(0);
    }

    /** MFFS's own teardown and recalculation (what a module change or a power loss does). */
    static void destroyField(BlockEntity be) {
        ((Projector) be).destroyField();
    }

    static BlockState forceField() {
        return ModBlocks.FORCE_FIELD.get().defaultBlockState();
    }

    /** Gives the projector {@code count} Glow modules in its second upgrade slot: 64 of them light the field at 15. */
    static void setGlowModules(BlockEntity be, int count) {
        ((ProjectorBlockEntity) be).upgradeSlots.get(1).setItem(new ItemStack(ModItems.GLOW_MODULE.get(), count));
    }

    static BlockEntityType<?> forceFieldType() {
        return ModObjects.FORCE_FIELD_BLOCK_ENTITY.get();
    }

    static boolean isForceFieldEntity(BlockEntity be) {
        return be instanceof ForceFieldBlockEntity;
    }

    static void setCamouflage(BlockEntity forceField, BlockState camouflage) {
        ((ForceFieldBlockEntity) forceField).setCamouflage(camouflage);
    }

    static BlockState camouflage(BlockEntity forceField) {
        return ((ForceFieldBlockEntity) forceField).getCamouflage();
    }

    /** The light the client gives a field block, read from the projector's Glow modules through the block's tag. */
    static int clientBlockLight(BlockEntity forceField) {
        return ((ForceFieldBlockEntity) forceField).getClientBlockLight();
    }

    /** MFFS's own reply tag for a field block: the projector, the camouflage and the light. */
    static CompoundTag customUpdateTag(BlockEntity forceField) {
        return ((ForceFieldBlockEntity) forceField).getCustomUpdateTag();
    }

    static boolean isForceField(BlockState state) {
        return state.is(ModBlocks.FORCE_FIELD.get());
    }

    /** MFFS's own fluid, Fortron, as a source block: a modded fluid for the sponge to find. */
    static BlockState fortronSource() {
        return ModFluids.FORTRON.getSourceFluid().defaultFluidState().createLegacyBlock();
    }

    /** A sponge module instance as the projector would make one from the item in a slot. */
    static Object spongeModule() {
        return ModModules.SPONGE.createModule(new ItemStack(ModItems.SPONGE_MODULE.get()));
    }

    static void beforeSelect(Object module, BlockEntity projector) {
        ((Module) module).beforeSelect((Projector) projector, List.of());
    }

    static void beforeProject(Object module, BlockEntity projector) {
        ((Module) module).beforeProject((Projector) projector);
    }

    static long ticks(BlockEntity projector) {
        return ((Projector) projector).getTicks();
    }

    /** Whether the projector's cache holds the interior point set MFFS's sponge builds (the key MFFS uses for it). */
    static boolean cacheHasInteriorPoints(BlockEntity projector) {
        try {
            Class<?> c = projector.getClass();
            while (c != null && !c.getSimpleName().equals("ModularBlockEntity")) {
                c = c.getSuperclass();
            }
            if (c == null) {
                throw new IllegalStateException("no ModularBlockEntity in " + projector.getClass());
            }
            Field f = c.getDeclaredField("cache");
            f.setAccessible(true);
            Map<?, ?> cache = (Map<?, ?>) f.get(projector);
            synchronized (cache) {
                return cache.containsKey("getInteriorPoints");
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Gives the projector a Fusion module in a slot that takes it (an upgrade slot for a matrix module, a field slot otherwise). */
    static void setFusionModule(BlockEntity be) {
        ProjectorBlockEntity projector = (ProjectorBlockEntity) be;
        ItemStack fusion = new ItemStack(ModItems.FUSION_MODULE.get());
        if (ModUtil.isModule(fusion, Module.Category.FIELD)) {
            projector.fieldModuleSlots.get(Direction.UP).get(0).setItem(fusion);
        } else {
            projector.upgradeSlots.get(1).setItem(fusion);
        }
    }

    static boolean hasFusionModule(BlockEntity be) {
        return ((Projector) be).hasModule(ModModules.FUSION);
    }

    static void setFrequency(BlockEntity be, int frequency) {
        ((FortronBlockEntity) be).fortronStorage.setFrequency(frequency);
    }

    static int calculatedPositions(BlockEntity be) {
        return ((Projector) be).getCalculatedFieldPositions().size();
    }

    /** How many of a chunk's block entities are force field blocks. */
    static int forceFieldEntities(LevelChunk chunk) {
        int n = 0;
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof ForceFieldBlockEntity) {
                n++;
            }
        }
        return n;
    }
}
