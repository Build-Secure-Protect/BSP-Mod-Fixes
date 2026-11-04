package com.mrgregles.bsp_mod_fixes.fix.emi;

import dev.emi.emi.jemi.impl.extras.JemiPlaceable;
import dev.emi.emi.jemi.impl.extras.JemiWidgetBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.gui.widgets.ICycleButtonWidget;
import mezz.jei.api.gui.widgets.IRecipeWidgetTooltipCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** F-14: JEI's {@code ICycleButtonWidget} for EMI: a button that steps through its values and tells its consumer. Client only. */
public final class EmiCycleButtonWidget<T> extends JemiPlaceable<ICycleButtonWidget<T>> implements ICycleButtonWidget<T> {
    private final CycleValues<T> values;
    private final Consumer<T> onChange;
    private final WidgetTooltip tooltip = new WidgetTooltip();
    private Function<T, Component> message = v -> null;
    private Function<T, IDrawable> icon = v -> null;
    private boolean active = true;
    private boolean visible = true;

    public EmiCycleButtonWidget(List<T> values, T initial, int width, int height, Consumer<T> onChange) {
        super(width, height);
        this.values = new CycleValues<>(values, initial);
        this.onChange = onChange;
    }

    public JemiWidgetBuilder builder() {
        return new JemiWidgetBuilder(width, height, (self, holder) -> holder.add(new EmiButtonRender(x, y, width, height,
                () -> message.apply(values.value()), () -> icon.apply(values.value()), () -> active, () -> visible, this::step, tooltip)));
    }

    private void step() {
        onChange.accept(values.next());
    }

    @Override
    public ICycleButtonWidget<T> setPosition(int areaX, int areaY, int areaWidth, int areaHeight, HorizontalAlignment horizontal, VerticalAlignment vertical) {
        return setPosition(areaX + horizontal.getXPos(areaWidth, width), areaY + vertical.getYPos(areaHeight, height));
    }

    @Override
    public ICycleButtonWidget<T> setMessage(Component text) {
        message = v -> text;
        return this;
    }

    @Override
    public ICycleButtonWidget<T> setMessage(Function<T, Component> byValue) {
        message = byValue;
        return this;
    }

    @Override
    public ICycleButtonWidget<T> setIcon(IDrawable drawable) {
        icon = v -> drawable;
        return this;
    }

    @Override
    public ICycleButtonWidget<T> setIcon(Function<T, IDrawable> byValue) {
        icon = byValue;
        return this;
    }

    @Override
    public ICycleButtonWidget<T> setActive(boolean isActive) {
        active = isActive;
        return this;
    }

    @Override
    public ICycleButtonWidget<T> setVisible(boolean isVisible) {
        visible = isVisible;
        return this;
    }

    @Override
    public T getValue() {
        return values.value();
    }

    @Override
    public ICycleButtonWidget<T> setValue(T value) {
        values.set(value);
        return this;
    }

    @Override
    public ICycleButtonWidget<T> setTooltip(FormattedText text) {
        tooltip.set(text);
        return this;
    }

    @Override
    public ICycleButtonWidget<T> setTooltip(Collection<? extends FormattedText> lines) {
        tooltip.set(lines);
        return this;
    }

    @Override
    public ICycleButtonWidget<T> setTooltip(TooltipComponent component) {
        tooltip.set(component);
        return this;
    }

    @Override
    public ICycleButtonWidget<T> setTooltip(IRecipeWidgetTooltipCallback callback) {
        tooltip.set(callback);
        return this;
    }
}
