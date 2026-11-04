package com.mrgregles.bsp_mod_fixes.fix.emi;

import com.mojang.datafixers.util.Either;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.FluidEmiStack;
import dev.emi.emi.api.stack.ListEmiIngredient;
import dev.emi.emi.jemi.impl.JemiTooltipBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.List;
import java.util.Optional;

/**
 * The client-side part of F-12 and F-13, called from the mixins only (it touches EMI, JEI and client classes). Keeps the
 * {@link LiveLines} of a tooltip builder in step with EMI's lists, and runs a slot's rich tooltip callback with the fluid
 * amount probe.
 */
public final class EmiTooltips {
    /** The lines EMI puts before the picked fluid's tooltip in a list ingredient: "Accepts:" and the ingredient grid. */
    static final int LIST_PREFIX_LINES = 2;

    private EmiTooltips() {}

    /** The live line list over EMI's {@code tooltip} and {@code legacyText}. */
    public static LiveLines<Either<FormattedText, TooltipComponent>, ClientTooltipComponent, Component> lines(List<ClientTooltipComponent> tooltip, List<Component> legacyText) {
        return new LiveLines<>(tooltip, legacyText, EmiTooltips::render, EmiTooltips::textOf, EmiTooltips::opaque);
    }

    /**
     * A line's rendered form: text through the language's visual order; a rich component through the client factory, or an
     * empty line when the client has no renderer for it, so the live list keeps its indexes (EMI's own add drops such a line).
     */
    static ClientTooltipComponent render(Either<FormattedText, TooltipComponent> line) {
        return line.map(
                text -> ClientTooltipComponent.create(Language.getInstance().getVisualOrder(text)),
                component -> {
                    try {
                        return ClientTooltipComponent.create(component);
                    } catch (RuntimeException e) {
                        Fixes.LOGGER.debug("BSP-Mod Fixes: F-12 has no renderer for a tooltip component, shown as an empty line: {}", component, e);
                        return ClientTooltipComponent.create(FormattedCharSequence.EMPTY);
                    }
                });
    }

    /** The plain text EMI keeps in {@code legacyText}: only lines that are Components, as EMI's own add() does. */
    static Optional<Component> textOf(Either<FormattedText, TooltipComponent> line) {
        return line.left().filter(t -> t instanceof Component).map(t -> (Component) t);
    }

    /** A stand-in for a rendered line this mod never saw added: a rich component with nothing to say. */
    static Either<FormattedText, TooltipComponent> opaque(ClientTooltipComponent rendered) {
        return Either.right(new TooltipComponent() {});
    }

    /**
     * F-13: runs a slot's rich tooltip callback. For a single fluid with an amount, EMI's own amount line is taken out of the
     * slot tooltip and offered to the callback as the first line of its builder, under JEI's key. Afterwards whatever the
     * builder holds (the amount line if it was left alone, plus the callback's lines) goes back into the slot tooltip at the
     * amount line's index, and the builder is emptied so EMI's own append adds nothing twice. Anything else runs as before.
     */
    public static void richTooltip(IRecipeSlotRichTooltipCallback callback, IRecipeSlotView slot, ITooltipBuilder builder,
                                   List<ClientTooltipComponent> tooltip, EmiIngredient stack) {
        if (builder instanceof TooltipLinesAccess access && builder instanceof JemiTooltipBuilder jemi) {
            int index = amountLineIndex(stack, tooltip);
            if (index >= 0) {
                try {
                    ClientTooltipComponent emiAmount = tooltip.remove(index);
                    Component probe = Component.translatable(AmountLine.JEI_KEY, Component.literal(Long.toString(stack.getAmount())));
                    access.bsp_mod_fixes$lines().seed(Either.left(probe), emiAmount);
                    callback.onRichTooltip(slot, builder);
                } finally {
                    AmountLine.splice(tooltip, index, jemi.tooltip);
                    builder.clear();
                }
                return;
            }
        }
        callback.onRichTooltip(slot, builder);
    }

    /**
     * Where EMI's own amount line sits in the slot tooltip, or -1 when the slot is not a fluid with an amount. A single fluid:
     * after its text lines. A list of fluids (a tag input, as in alloying): EMI shows "Accepts:", the ingredient grid, then the
     * tooltip of the fluid it picked for this second; the pick is repeated here, and the class guard bounds the harm should
     * the second tick over in between.
     */
    static int amountLineIndex(EmiIngredient stack, List<ClientTooltipComponent> tooltip) {
        List<EmiStack> stacks = stack.getEmiStacks();
        if (stacks.isEmpty() || stack.getAmount() <= 1) {
            return -1;
        }
        int index;
        if (stack instanceof FluidEmiStack fluid) {
            index = AmountLine.index(fluid.getTooltipText().size(), fluid.getAmount(), tooltip.size());
        } else if (stack instanceof ListEmiIngredient && stacks.stream().allMatch(s -> s instanceof FluidEmiStack)) {
            EmiStack current = stacks.get((int) (System.currentTimeMillis() / 1000 % stacks.size()));
            index = AmountLine.index(LIST_PREFIX_LINES + current.getTooltipText().size(), stack.getAmount(), tooltip.size());
        } else {
            return -1;
        }
        if (index >= 0 && tooltip.get(index).getClass() != ClientTextTooltip.class) {
            return -1; // not the plain text line EMI makes for the amount; leave the tooltip alone
        }
        return index;
    }
}
