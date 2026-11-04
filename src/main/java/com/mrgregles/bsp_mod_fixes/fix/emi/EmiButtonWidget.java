package com.mrgregles.bsp_mod_fixes.fix.emi;

import dev.emi.emi.jemi.impl.extras.JemiPlaceable;
import dev.emi.emi.jemi.impl.extras.JemiWidgetBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.gui.widgets.IButtonWidget;
import mezz.jei.api.gui.widgets.IRecipeWidgetTooltipCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.Collection;

/** F-14: JEI's {@code IButtonWidget} for EMI, drawn by {@link EmiButtonRender}. Client only. */
public final class EmiButtonWidget extends JemiPlaceable<EmiButtonWidget> implements IButtonWidget<EmiButtonWidget> {
    private final Runnable action;
    private final WidgetTooltip tooltip = new WidgetTooltip();
    private Component message;
    private IDrawable icon;
    private boolean active = true;
    private boolean visible = true;

    public EmiButtonWidget(int width, int height, Runnable action) {
        super(width, height);
        this.action = action;
    }

    public JemiWidgetBuilder builder() {
        return new JemiWidgetBuilder(width, height, (self, holder) -> holder.add(new EmiButtonRender(x, y, width, height,
                () -> message, () -> icon, () -> active, () -> visible, action, tooltip)));
    }

    @Override
    public EmiButtonWidget setPosition(int areaX, int areaY, int areaWidth, int areaHeight, HorizontalAlignment horizontal, VerticalAlignment vertical) {
        return setPosition(areaX + horizontal.getXPos(areaWidth, width), areaY + vertical.getYPos(areaHeight, height));
    }

    @Override
    public EmiButtonWidget setMessage(Component text) {
        message = text;
        return this;
    }

    @Override
    public EmiButtonWidget setIcon(IDrawable drawable) {
        icon = drawable;
        return this;
    }

    @Override
    public EmiButtonWidget setActive(boolean isActive) {
        active = isActive;
        return this;
    }

    @Override
    public EmiButtonWidget setVisible(boolean isVisible) {
        visible = isVisible;
        return this;
    }

    @Override
    public EmiButtonWidget setTooltip(FormattedText text) {
        tooltip.set(text);
        return this;
    }

    @Override
    public EmiButtonWidget setTooltip(Collection<? extends FormattedText> lines) {
        tooltip.set(lines);
        return this;
    }

    @Override
    public EmiButtonWidget setTooltip(TooltipComponent component) {
        tooltip.set(component);
        return this;
    }

    @Override
    public EmiButtonWidget setTooltip(IRecipeWidgetTooltipCallback callback) {
        tooltip.set(callback);
        return this;
    }
}
