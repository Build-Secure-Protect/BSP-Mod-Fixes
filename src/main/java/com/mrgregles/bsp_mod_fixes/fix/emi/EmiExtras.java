package com.mrgregles.bsp_mod_fixes.fix.emi;

import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.jemi.impl.extras.JemiTextWidget;
import dev.emi.emi.jemi.impl.extras.JemiWidgetBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IButtonWidget;
import mezz.jei.api.gui.widgets.ICycleButtonWidget;
import mezz.jei.api.gui.widgets.IDrawableWidget;
import mezz.jei.api.gui.widgets.ITextWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * F-14 and F-15, the client-side part called from the extras builder mixin: makes JEI's widgets for EMI and registers each
 * one's {@link JemiWidgetBuilder} in EMI's extras list, the way EMI's own {@code addDrawable} and {@code addRecipeArrow}
 * do. Times are converted from ticks to milliseconds as EMI converts them.
 */
public final class EmiExtras {
    private EmiExtras() {}

    public static IDrawableWidget drawable(List<JemiWidgetBuilder> widgets, IDrawable drawable) {
        return register(widgets, new EmiDrawableWidget(drawable.getWidth(), drawable.getHeight(), (holder, w) ->
                holder.addDrawable(w.x, w.y, w.width, w.height, (graphics, mouseX, mouseY, delta) -> drawable.draw(graphics))
                        .tooltip(w.tooltip.supplier())));
    }

    /** An invisible widget with only a tooltip over its area. */
    public static IDrawableWidget tooltipArea(List<JemiWidgetBuilder> widgets, int x, int y, int width, int height) {
        EmiDrawableWidget w = new EmiDrawableWidget(width, height, (holder, self) ->
                holder.addTooltip(self.tooltip.supplier(), self.x, self.y, self.width, self.height));
        w.setPosition(x, y);
        return register(widgets, w);
    }

    public static IDrawableWidget arrow(List<JemiWidgetBuilder> widgets) {
        return texture(widgets, EmiTexture.EMPTY_ARROW);
    }

    public static IDrawableWidget plusSign(List<JemiWidgetBuilder> widgets) {
        return texture(widgets, EmiTexture.PLUS);
    }

    public static IDrawableWidget animatedArrow(List<JemiWidgetBuilder> widgets, int ticksPerCycle) {
        return animated(widgets, EmiTexture.EMPTY_ARROW, EmiTexture.FULL_ARROW, ticksPerCycle, true, false, false);
    }

    public static IDrawableWidget animatedFlame(List<JemiWidgetBuilder> widgets, int ticksPerCycle) {
        return animated(widgets, EmiTexture.EMPTY_FLAME, EmiTexture.FULL_FLAME, ticksPerCycle, false, true, true);
    }

    public static IButtonWidget<?> button(List<JemiWidgetBuilder> widgets, int width, int height, Runnable action) {
        EmiButtonWidget b = new EmiButtonWidget(width, height, action);
        widgets.add(b.builder());
        return b;
    }

    public static <T> ICycleButtonWidget<T> cycleButton(List<JemiWidgetBuilder> widgets, List<T> values, T initial, int width, int height, Consumer<T> onChange) {
        EmiCycleButtonWidget<T> b = new EmiCycleButtonWidget<>(values, initial, width, height, onChange);
        widgets.add(b.builder());
        return b;
    }

    /**
     * F-15: registers a text widget EMI made so that it is drawn, wrapped and aligned as JEI draws it, with its tooltip. The
     * lines are laid out once when the recipe's widgets are built (after the plugin's setters ran); when the area cannot hold
     * them all, the full text goes into the tooltip, as JEI does.
     */
    public static void text(List<JemiWidgetBuilder> widgets, ITextWidget widget) {
        if (widget instanceof JemiTextWidget t) {
            WidgetTooltip configured = widget instanceof TextTooltipAccess access ? access.bsp_mod_fixes$tooltip() : new WidgetTooltip();
            widgets.add(new JemiWidgetBuilder(t.width, t.height, (self, holder) -> {
                Layout layout = layout(t);
                BiFunction<Integer, Integer, List<ClientTooltipComponent>> tooltip = (mouseX, mouseY) -> {
                    List<ClientTooltipComponent> lines = new ArrayList<>();
                    if (layout.truncated) {
                        for (FormattedText text : t.text) {
                            lines.add(ClientTooltipComponent.create(Language.getInstance().getVisualOrder(text)));
                        }
                    }
                    lines.addAll(configured.lines(mouseX, mouseY));
                    return lines;
                };
                holder.addDrawable(t.x, t.y, t.width, t.height, (graphics, mouseX, mouseY, delta) -> draw(graphics, t, layout))
                        .tooltip(tooltip);
            }));
        }
    }

    /** The wrapped lines of a text widget, their starting y, and whether some were cut. */
    record Layout(List<FormattedCharSequence> lines, int startY, int lineHeight, boolean truncated) {}

    static Layout layout(JemiTextWidget t) {
        Font font = Minecraft.getInstance().font;
        int spacing = t.spacing;
        int lineHeight = TextWidgets.lineHeight(font.lineHeight, spacing);
        int maxLines = TextWidgets.maxLines(t.height, font.lineHeight, spacing);
        List<FormattedCharSequence> lines = new ArrayList<>();
        boolean truncated = false;
        for (FormattedText text : t.text) {
            for (FormattedCharSequence line : font.split(text, t.width > 0 ? t.width : Integer.MAX_VALUE)) {
                if (lines.size() >= maxLines) {
                    truncated = true;
                    break;
                }
                lines.add(line);
            }
        }
        int startY = t.vertical.getYPos(t.height, TextWidgets.blockHeight(lines.size(), font.lineHeight, spacing));
        return new Layout(lines, startY, lineHeight, truncated);
    }

    /** Draws the laid-out lines inside the widget's area (the holder's drawable is already translated to its position). */
    static void draw(GuiGraphics graphics, JemiTextWidget t, Layout layout) {
        Font font = Minecraft.getInstance().font;
        int y = layout.startY;
        for (FormattedCharSequence line : layout.lines) {
            int x = t.horizontal.getXPos(t.width, font.width(line));
            graphics.drawString(font, line, x, y, t.color, t.shadow);
            y += layout.lineHeight;
        }
    }

    private static IDrawableWidget texture(List<JemiWidgetBuilder> widgets, EmiTexture texture) {
        return register(widgets, new EmiDrawableWidget(texture.width, texture.height, (holder, w) ->
                holder.addTexture(texture, w.x, w.y).tooltip(w.tooltip.supplier())));
    }

    private static IDrawableWidget animated(List<JemiWidgetBuilder> widgets, EmiTexture texture, EmiTexture animated, int ticks,
                                            boolean horizontal, boolean endToStart, boolean fullToEmpty) {
        return register(widgets, new EmiDrawableWidget(texture.width, texture.height, (holder, w) -> {
            holder.addTexture(texture, w.x, w.y).tooltip(w.tooltip.supplier());
            holder.addAnimatedTexture(animated, w.x, w.y, ticks * 1000 / 20, horizontal, endToStart, fullToEmpty);
        }));
    }

    private static IDrawableWidget register(List<JemiWidgetBuilder> widgets, EmiDrawableWidget widget) {
        widgets.add(widget.builder());
        return widget;
    }
}
