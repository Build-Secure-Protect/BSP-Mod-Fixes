package com.mrgregles.bsp_mod_fixes.fix.emi;

import com.mojang.datafixers.util.Either;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/** Added to EMI's {@code JemiTooltipBuilder} by the F-12 mixin: its live line list, for the F-13 probe. Client only. */
public interface TooltipLinesAccess {
    LiveLines<Either<FormattedText, TooltipComponent>, ClientTooltipComponent, Component> bsp_mod_fixes$lines();
}
