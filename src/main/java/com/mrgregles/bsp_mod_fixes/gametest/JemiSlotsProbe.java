package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.emi.SlotViews;
import com.mrgregles.bsp_mod_fixes.fix.emi.TransferSlots;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.jemi.JemiRecipeHandler;
import dev.emi.emi.jemi.impl.JemiRecipeSlot;
import dev.emi.emi.jemi.impl.JemiRecipeSlotsView;
import dev.emi.emi.runtime.EmiReloadManager;
import dev.emi.emi.screen.WidgetGroup;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.List;
import java.util.Optional;

/**
 * Dev-only probe of F-37: {@code ./gradlew runClient -PwithEmi -PwithMekanism -PjemiProbe -PquickPlay=emitest}. Once a world
 * is open and EMI has reloaded, it takes the EMI recipe and the raw crafting recipe of every id in {@link #RECIPES} that is
 * loaded, lays the EMI recipe's widgets out as the recipe screen does, and calls the real {@code
 * JemiRecipeHandler.createSlotsView} on them, which is what EMI does for a JEI transfer handler's "+" button and while a
 * recipe is rendered with such a screen open. It logs the recipe's class, shape, input count, the exception if one escapes,
 * and otherwise every slot of the view by role and position. With {@code -PskipFixes=EmiJemiSlotsViewMixin} the Laser
 * Tractor Beam throws {@code IndexOutOfBoundsException: toIndex = 8}; with the fix the view holds nine INPUT slots. Then it
 * closes the client. Lines start "BSP-Mod Fixes jemi probe". Stays out of the jar with the rest of the gametest package.
 */
public final class JemiSlotsProbe {
    private static final List<String> RECIPES = List.of(
            "mekanism:laser_tractor_beam",                  // six entries, EMI asks for 0..8
            "enderstorage:recolour_ender_tank",             // one entry, 0..8 (only when EnderStorage is in the run)
            "enderstorage:recolour_ender_chest",
            "enderstorage:recolour_ender_pouch",
            "quark:tweaks/crafting/dirty_glass",            // six entries, 0..9 (only when Quark is in the run)
            "mekanism:personal_chest",                      // a Mekanism shaped recipe with nine entries, left alone
            "minecraft:crafting_table",                     // vanilla shaped, four entries padded to nine by EMI itself
            "minecraft:stick");                             // vanilla shaped, 1x2
    private static boolean done;
    private static int settledTicks;

    private JemiSlotsProbe() {}

    public static void install() {
        MinecraftForge.EVENT_BUS.addListener(JemiSlotsProbe::tick);
        Fixes.LOGGER.info("BSP-Mod Fixes jemi probe: installed, waiting for a world and EMI's reload");
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
            report(mc);
        } catch (Throwable t) {
            Fixes.LOGGER.error("BSP-Mod Fixes jemi probe FAILED", t);
        }
        mc.stop();
    }

    private static String describe(EmiIngredient stack) {
        if (stack == null) {
            return "null";
        }
        if (stack.isEmpty()) {
            return "empty";
        }
        List<EmiStack> stacks = stack.getEmiStacks();
        return stacks.isEmpty() ? "no stacks" : String.valueOf(stacks.get(0).getId());
    }

    private static void report(Minecraft mc) {
        Fixes.LOGGER.info("BSP-Mod Fixes jemi probe: the F-37 switch reads {}", TransferSlots.enabled() ? "on" : "off");
        int crashes = 0, viewed = 0, membersAnswered = 0, membersRefused = 0;
        for (String idText : RECIPES) {
            ResourceLocation id = ResourceLocation.tryParse(idText);
            EmiRecipe emiRecipe = EmiApi.getRecipeManager().getRecipe(id);
            Optional<? extends Recipe<?>> raw = mc.level.getRecipeManager().byKey(id);
            if (emiRecipe == null || raw.isEmpty()) {
                Fixes.LOGGER.info("BSP-Mod Fixes jemi probe: {} is not loaded (EMI {}, raw {})", idText, emiRecipe != null, raw.isPresent());
                continue;
            }
            if (!(raw.get() instanceof CraftingRecipe crafting)) {
                Fixes.LOGGER.info("BSP-Mod Fixes jemi probe: {} is not a crafting recipe ({})", idText, raw.get().getClass().getName());
                continue;
            }
            String shape = emiRecipe instanceof EmiCraftingRecipe ecr
                    ? (ecr.shapeless ? "shapeless" : "shaped, fits 1x3 " + ecr.canFit(1, 3) + ", 3x1 " + ecr.canFit(3, 1) + ", 3x2 " + ecr.canFit(3, 2) + ", 2x2 " + ecr.canFit(2, 2))
                    : "not an EmiCraftingRecipe";
            Fixes.LOGGER.info("BSP-Mod Fixes jemi probe: {} -> EMI {} ({}, {} inputs), raw {}", idText, emiRecipe.getClass().getSimpleName(),
                    shape, emiRecipe.getInputs().size(), crafting.getClass().getName());
            WidgetGroup group = new WidgetGroup(emiRecipe, 0, 0, emiRecipe.getDisplayWidth(), emiRecipe.getDisplayHeight());
            emiRecipe.addWidgets(group);
            long slotWidgets = group.widgets.stream().filter(w -> w instanceof SlotWidget).count();
            JemiRecipeSlotsView view;
            try {
                view = JemiRecipeHandler.createSlotsView(emiRecipe, crafting, RecipeTypes.CRAFTING, group.widgets);
            } catch (Throwable t) {
                crashes++;
                Fixes.LOGGER.error("BSP-Mod Fixes jemi probe: {} CRASHED createSlotsView", idText, t);
                continue;
            }
            viewed++;
            // F-41: JEI 15.34 to 15.53's members on EMI's slot views, the first of them read by JEI's own transfer handlers
            int answered = 0, refused = 0;
            for (IRecipeSlotView slot : view.getSlotViews()) {
                try {
                    int all = slot.getAllIngredientsList().size();
                    long displayed = slot.getDisplayedIngredients().count();
                    boolean tagged = slot.getTagKey().isPresent();
                    Fixes.LOGGER.info("BSP-Mod Fixes jemi probe:   slot {} answers JEI 15.74: {} in the list, {} displayed, tag {}", answered + refused, all, displayed, tagged);
                    answered++;
                } catch (AbstractMethodError e) {
                    refused++;
                    if (refused == 1) {
                        Fixes.LOGGER.error("BSP-Mod Fixes jemi probe:   slot views REFUSE JEI 15.74's members (as shipped): {}", e.toString());
                    }
                }
            }
            membersAnswered += answered;
            membersRefused += refused;
            try {
                List<IRecipeSlotView> inputs = view.getSlotViews(RecipeIngredientRole.INPUT);
                Fixes.LOGGER.info("BSP-Mod Fixes jemi probe: {} laid out: {} slot widgets, {} slots in the view, {} INPUT, {} OUTPUT, {} CATALYST",
                        idText, slotWidgets, view.getSlotViews().size(), inputs.size(),
                        view.getSlotViews(RecipeIngredientRole.OUTPUT).size(), view.getSlotViews(RecipeIngredientRole.CATALYST).size());
                int n = 0;
                for (IRecipeSlotView slot : view.getSlotViews()) {
                    JemiRecipeSlot s = (JemiRecipeSlot) slot;
                    Fixes.LOGGER.info("BSP-Mod Fixes jemi probe:   slot {} {} at {},{} widget {} stack {}", n++, s.role, s.x, s.y,
                            s.widget == null ? "none" : "yes", describe(s.stack));
                }
            } catch (Throwable t) {
                Fixes.LOGGER.error("BSP-Mod Fixes jemi probe: {} laid out, but its slots could not be read", idText, t);
            }
        }
        // the mixin's target loads at the first call above, so the fix's state is known only now
        Fixes.LOGGER.info("BSP-Mod Fixes jemi probe: done, {} recipes laid out, {} crashed; {} slot views answered JEI 15.74's members, {} refused; F-37 {}, F-41 {} in this run", viewed, crashes,
                membersAnswered, membersRefused,
                Fixes.active().stream().anyMatch(f -> f.id().equals(TransferSlots.ID)) ? "applied" : "NOT applied",
                Fixes.active().stream().anyMatch(f -> f.id().equals(SlotViews.ID)) ? "applied" : "NOT applied");
    }
}
