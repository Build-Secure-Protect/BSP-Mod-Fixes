package com.mrgregles.bsp_mod_fixes.fix.emi;

import dev.emi.emi.api.widget.WidgetHolder;
import dev.emi.emi.jemi.impl.extras.JemiPlaceable;
import dev.emi.emi.jemi.impl.extras.JemiWidgetBuilder;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.gui.widgets.IDrawableWidget;
import mezz.jei.api.gui.widgets.IRecipeWidgetTooltipCallback;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.Collection;

/**
 * F-14: JEI's {@code IDrawableWidget} for EMI. Holds the position, size and tooltip a JEI plugin sets; the visual (a
 * drawable, a texture, an animated texture or nothing for a tooltip area) is added to EMI's widget holder at the final
 * position when the recipe's widgets are built, through a {@link JemiWidgetBuilder} like EMI's own extras. Client only.
 */
public final class EmiDrawableWidget extends JemiPlaceable<IDrawableWidget> implements IDrawableWidget {
    /** Adds the widget's EMI widgets to the holder at its final position. */
    public interface Visual {
        void addTo(WidgetHolder holder, EmiDrawableWidget widget);
    }

    private final Visual visual;
    public final WidgetTooltip tooltip = new WidgetTooltip();

    public EmiDrawableWidget(int width, int height, Visual visual) {
        super(width, height);
        this.visual = visual;
    }

    /** The entry for EMI's extras list; its own position is unused, this widget's is read when the recipe is built. */
    public JemiWidgetBuilder builder() {
        return new JemiWidgetBuilder(width, height, (self, holder) -> visual.addTo(holder, this));
    }

    @Override
    public IDrawableWidget setPosition(int areaX, int areaY, int areaWidth, int areaHeight, HorizontalAlignment horizontal, VerticalAlignment vertical) {
        return setPosition(areaX + horizontal.getXPos(areaWidth, width), areaY + vertical.getYPos(areaHeight, height));
    }

    @Override
    public IDrawableWidget setTooltip(FormattedText text) {
        tooltip.set(text);
        return this;
    }

    @Override
    public IDrawableWidget setTooltip(Collection<? extends FormattedText> lines) {
        tooltip.set(lines);
        return this;
    }

    @Override
    public IDrawableWidget setTooltip(TooltipComponent component) {
        tooltip.set(component);
        return this;
    }

    @Override
    public IDrawableWidget setTooltip(IRecipeWidgetTooltipCallback callback) {
        tooltip.set(callback);
        return this;
    }
}
