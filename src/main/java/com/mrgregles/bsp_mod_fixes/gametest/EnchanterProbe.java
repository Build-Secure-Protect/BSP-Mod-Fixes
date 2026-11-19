package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.runtime.EmiReloadManager;
import dev.emi.emi.screen.WidgetGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.List;

/**
 * Dev-only probe: {@code ./gradlew runClient -PwithEmi -PwithEnderIo -PenchanterProbe -PquickPlay=emitest}. Once a world is open
 * and EMI has reloaded, it takes every recipe of EMI's category {@code enderio:enchanter} (Ender IO's Enchanting, reached through
 * EMI's JEI layer), builds its widgets as the recipe screen does and draws each widget once with a real {@code GuiGraphics}, in its
 * own try, logging the exception behind EMI's "Error Rendering" with its stack trace; then it opens the category in EMI's own
 * screen for a few seconds so EMI's own "Error constructing recipe widgets" or "Error rendering widget" lines land too, and closes
 * the client. Lines start "BSP-Mod Fixes enchanter probe". Stays out of the jar with the rest of the gametest package.
 */
public final class EnchanterProbe {
    private static final ResourceLocation CATEGORY = new ResourceLocation("enderio", "enchanter");
    private static boolean done;
    private static boolean displayed;
    private static int settledTicks;
    private static int displayTicks;

    private EnchanterProbe() {}

    public static void install() {
        MinecraftForge.EVENT_BUS.addListener(EnchanterProbe::tick);
        Fixes.LOGGER.info("BSP-Mod Fixes enchanter probe: installed, waiting for a world and EMI's reload");
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
        if (displayed) {
            if (++displayTicks > 100) {
                done = true;
                Fixes.LOGGER.info("BSP-Mod Fixes enchanter probe: done");
                mc.stop();
            }
            return;
        }
        if (++settledTicks < 60) {
            return;
        }
        try {
            EmiRecipeCategory category = report(mc);
            if (category != null) {
                // over the player's inventory, as a player reaches it: EMI's JEI transfer handlers render only above a container screen
                mc.setScreen(new InventoryScreen(mc.player));
                EmiApi.displayRecipeCategory(category);
                Fixes.LOGGER.info("BSP-Mod Fixes enchanter probe: EMI's recipe screen opened on the category over the inventory for 100 ticks; EMI's own error lines, if any, follow");
            }
        } catch (Throwable t) {
            Fixes.LOGGER.error("BSP-Mod Fixes enchanter probe FAILED", t);
        }
        displayed = true;
    }

    private static EmiRecipeCategory report(Minecraft mc) {
        EmiRecipeCategory category = EmiApi.getRecipeManager().getCategories().stream().filter(c -> c.getId().equals(CATEGORY)).findFirst().orElse(null);
        if (category == null) {
            Fixes.LOGGER.info("BSP-Mod Fixes enchanter probe: no EMI category {} (categories: {})", CATEGORY,
                    EmiApi.getRecipeManager().getCategories().stream().map(c -> c.getId().toString()).sorted().toList());
            return null;
        }
        List<EmiRecipe> recipes = EmiApi.getRecipeManager().getRecipes(category);
        Fixes.LOGGER.info("BSP-Mod Fixes enchanter probe: category {} holds {} recipes", CATEGORY, recipes.size());
        GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        int built = 0, drawn = 0, buildFailed = 0, drawFailed = 0;
        for (EmiRecipe recipe : recipes) {
            String id = String.valueOf(recipe.getId());
            WidgetGroup group = new WidgetGroup(recipe, 0, 0, recipe.getDisplayWidth(), recipe.getDisplayHeight());
            try {
                recipe.addWidgets(group);
                built++;
            } catch (Throwable t) {
                buildFailed++;
                Fixes.LOGGER.error("BSP-Mod Fixes enchanter probe: {} ({}) FAILED while its widgets were built", id, recipe.getClass().getName(), t);
                continue;
            }
            try {
                for (Widget widget : group.widgets) {
                    widget.render(graphics, -1, -1, 0f);
                }
                drawn++;
            } catch (Throwable t) {
                drawFailed++;
                Fixes.LOGGER.error("BSP-Mod Fixes enchanter probe: {} ({}, {} widgets) FAILED while drawn", id, recipe.getClass().getName(), group.widgets.size(), t);
            }
        }
        graphics.flush();
        Fixes.LOGGER.info("BSP-Mod Fixes enchanter probe: {} recipes built, {} drawn, {} failed to build, {} failed to draw", built, drawn, buildFailed, drawFailed);
        return category;
    }
}
