package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.jemi.JemiPlugin;
import dev.emi.emi.runtime.EmiReloadLog;
import dev.emi.emi.runtime.EmiReloadManager;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Dev-only probe of the recipes EMI holds for Mekanism once a world is open and EMI has reloaded:
 * {@code ./gradlew runClient -PwithEmi -PwithMekanism -PrecipeProbe -PquickPlay=emitest}. It waits for EMI's reload, then
 * logs every Mekanism category on the JEI side (through EMI's JEI runtime) and on the EMI side with its recipe count, the
 * EMI recipes that make a handful of Mekanism items, the ids of the Metallurgic Infuser recipes, and EMI's reload warnings
 * that mention Mekanism. Then it closes the client. Lines start "BSP-Mod Fixes recipe probe". Stays out of the jar with the
 * rest of the gametest package; loaded by name only when the property is set.
 */
public final class EmiRecipeProbe {
    private static final List<String> ITEMS = List.of(
            "mekanism:alloy_infused", "mekanism:alloy_reinforced", "mekanism:alloy_atomic", "mekanism:enriched_iron",
            "mekanism:ingot_steel", "mekanism:dust_steel", "mekanism:ingot_bronze", "mekanism:ingot_osmium",
            "mekanism:dust_osmium", "minecraft:mossy_cobblestone");
    private static boolean done;
    private static int settledTicks;

    private EmiRecipeProbe() {}

    public static void install() {
        MinecraftForge.EVENT_BUS.addListener(EmiRecipeProbe::tick);
        Fixes.LOGGER.info("BSP-Mod Fixes recipe probe: installed, waiting for a world and EMI's reload");
    }

    private static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !EmiReloadManager.isLoaded() || EmiApi.getRecipeManager().getRecipes().isEmpty()) {
            settledTicks = 0;
            return;
        }
        if (++settledTicks < 60) {
            return;
        }
        done = true;
        try {
            report();
        } catch (Throwable t) {
            Fixes.LOGGER.error("BSP-Mod Fixes recipe probe FAILED", t);
        }
        mc.stop();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void report() {
        EmiRecipeManager emi = EmiApi.getRecipeManager();
        Fixes.LOGGER.info("BSP-Mod Fixes recipe probe: EMI holds {} recipes in {} categories", emi.getRecipes().size(), emi.getCategories().size());

        IJeiRuntime runtime = JemiPlugin.runtime;
        if (runtime == null) {
            Fixes.LOGGER.info("BSP-Mod Fixes recipe probe: no JEI runtime (JEI not loaded)");
        } else {
            IRecipeManager jei = runtime.getRecipeManager();
            for (IRecipeCategory<?> category : jei.createRecipeCategoryLookup().includeHidden().get().toList()) {
                RecipeType type = category.getRecipeType();
                if (!type.getUid().getNamespace().equals("mekanism")) {
                    continue;
                }
                long all = jei.createRecipeLookup(type).includeHidden().get().count();
                long visible = jei.createRecipeLookup(type).get().count();
                Fixes.LOGGER.info("BSP-Mod Fixes recipe probe: JEI category {} ('{}'): {} recipes, {} visible",
                        type.getUid(), category.getTitle().getString(), all, visible);
            }
        }

        EmiRecipeCategory infusing = null;
        for (EmiRecipeCategory category : emi.getCategories()) {
            if (category.getId().getNamespace().equals("mekanism")) {
                Fixes.LOGGER.info("BSP-Mod Fixes recipe probe: EMI category {}: {} recipes", category.getId(), emi.getRecipes(category).size());
                if (category.getId().getPath().contains("metallurgic")) {
                    infusing = category;
                }
            }
        }
        if (infusing == null) {
            Fixes.LOGGER.info("BSP-Mod Fixes recipe probe: EMI has no Metallurgic Infuser category");
        } else {
            for (EmiRecipe recipe : emi.getRecipes(infusing)) {
                Fixes.LOGGER.info("BSP-Mod Fixes recipe probe: infusing recipe {} -> {} (inputs {}, catalysts {})",
                        recipe.getId(), recipe.getOutputs(), recipe.getInputs().size(), recipe.getCatalysts().size());
            }
        }

        for (String id : ITEMS) {
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
            if (item == null) {
                Fixes.LOGGER.info("BSP-Mod Fixes recipe probe: {} is not registered", id);
                continue;
            }
            List<EmiRecipe> recipes = emi.getRecipesByOutput(EmiStack.of(item));
            Fixes.LOGGER.info("BSP-Mod Fixes recipe probe: {} EMI recipes make {}: {}", recipes.size(), id,
                    recipes.stream().map(r -> r.getCategory().getId() + " " + r.getId()).toList());
        }

        int warnings = 0;
        for (String warning : EmiReloadLog.warnings) {
            if (warning.contains("mekanism") || warning.contains("Mekanism")) {
                warnings++;
                if (warnings <= 40) {
                    Fixes.LOGGER.info("BSP-Mod Fixes recipe probe: EMI reload warning: {}", warning);
                }
            }
        }
        Fixes.LOGGER.info("BSP-Mod Fixes recipe probe: done ({} Mekanism reload warnings of {})", warnings, EmiReloadLog.warnings.size());
    }
}
