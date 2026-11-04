package com.mrgregles.bsp_mod_fixes.fix.emi;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import dev.emi.emi.jemi.impl.JemiTooltipBuilder;
import mezz.jei.api.gui.widgets.IRecipeWidgetTooltipCallback;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

/**
 * A JEI recipe widget's tooltip, in the four forms JEI's {@code IRecipeWidgetBuilder.setTooltip} takes, rendered for EMI
 * through a fresh {@code JemiTooltipBuilder} each time (so F-12's live lines work inside a callback too). Client only.
 */
public final class WidgetTooltip {
    private IRecipeWidgetTooltipCallback callback;

    public void set(FormattedText text) {
        callback = b -> b.add(text);
    }

    public void set(Collection<? extends FormattedText> lines) {
        List<FormattedText> copy = List.copyOf(lines);
        callback = b -> b.addAll(copy);
    }

    public void set(TooltipComponent component) {
        callback = b -> b.add(component);
    }

    public void set(IRecipeWidgetTooltipCallback tooltipCallback) {
        callback = tooltipCallback;
    }

    /** The lines to show at a mouse position; none without a tooltip, and none if the callback fails (logged once per failure at debug). */
    public List<ClientTooltipComponent> lines(int mouseX, int mouseY) {
        if (callback == null) {
            return List.of();
        }
        JemiTooltipBuilder builder = new JemiTooltipBuilder();
        try {
            callback.onTooltip(builder);
        } catch (RuntimeException e) {
            Fixes.LOGGER.debug("BSP-Mod Fixes: a JEI widget tooltip callback failed under EMI", e);
        }
        return builder.tooltip;
    }

    public BiFunction<Integer, Integer, List<ClientTooltipComponent>> supplier() {
        return this::lines;
    }
}
