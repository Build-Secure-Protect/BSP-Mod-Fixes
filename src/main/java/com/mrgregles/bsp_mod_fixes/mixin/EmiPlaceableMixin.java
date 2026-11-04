package com.mrgregles.bsp_mod_fixes.mixin;

import dev.emi.emi.jemi.impl.extras.JemiPlaceable;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.IPlaceable;
import mezz.jei.api.gui.placement.VerticalAlignment;
import org.spongepowered.asm.mixin.Mixin;

/**
 * F-14: the aligned {@code setPosition} overload JEI 15.56 made abstract on every {@code IPlaceable}, for EMI's own
 * placeables (its older {@code addDrawable}, {@code addRecipeArrow}, {@code addText} results). Applied only when EMI is at
 * the pinned version.
 */
@Mixin(value = JemiPlaceable.class, remap = false)
public abstract class EmiPlaceableMixin<T extends IPlaceable<T>> implements IPlaceable<T> {
    @Override
    public T setPosition(int areaX, int areaY, int areaWidth, int areaHeight, HorizontalAlignment horizontal, VerticalAlignment vertical) {
        return setPosition(areaX + horizontal.getXPos(areaWidth, getWidth()), areaY + vertical.getYPos(areaHeight, getHeight()));
    }
}
