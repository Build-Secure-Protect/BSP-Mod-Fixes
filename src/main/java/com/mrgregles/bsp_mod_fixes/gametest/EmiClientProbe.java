package com.mrgregles.bsp_mod_fixes.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.datafixers.util.Either;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import dev.emi.emi.api.widget.TextureWidget;
import dev.emi.emi.api.widget.TooltipWidget;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.emi.emi.jemi.impl.JemiTooltipBuilder;
import dev.emi.emi.jemi.impl.extras.JemiPlaceable;
import dev.emi.emi.jemi.impl.extras.JemiRecipeExtrasBuilder;
import dev.emi.emi.jemi.impl.extras.JemiWidgetBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.IPlaceable;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.gui.widgets.ICycleButtonWidget;
import mezz.jei.api.gui.widgets.IDrawableWidget;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.gui.widgets.IRecipeWidgetTooltipCallback;
import mezz.jei.api.gui.widgets.ITextWidget;
import mezz.jei.api.runtime.IJeiKeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * Dev-only probe for the EMI fixes on a client (F-12, F-14, F-15 target classes EMI loads only for its recipe screen, which
 * no headless run reaches): {@code ./gradlew runClient -PwithEmi -PemiProbe} runs it once loading is complete. It
 * constructs EMI's real tooltip builder and extras builder, calls the JEI methods the mixins add (the calls that threw
 * {@code AbstractMethodError} before), builds the resulting EMI widgets into a collecting holder and reads their tooltips.
 * Nothing is drawn. Every check logs "probe ok" or "probe FAILED"; the last line sums them up. This class stays out of
 * the jar with the rest of the gametest package and is loaded by name only when the property is set.
 */
public final class EmiClientProbe {
    private static int checks, failures;

    private EmiClientProbe() {}

    public static void run() {
        checks = 0;
        failures = 0;
        Fixes.LOGGER.info("BSP-Mod Fixes probe: start (EMI {})", com.mrgregles.bsp_mod_fixes.BSPModFixes.version("emi"));
        tooltipBuilder();
        extras();
        Fixes.LOGGER.info("BSP-Mod Fixes probe: {} checks, {} failed", checks, failures);
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (ok) {
            Fixes.LOGGER.info("BSP-Mod Fixes probe ok: {}", what);
        } else {
            failures++;
            Fixes.LOGGER.error("BSP-Mod Fixes probe FAILED: {}", what);
        }
    }

    private static void tooltipBuilder() {
        JemiTooltipBuilder jemi = new JemiTooltipBuilder();
        ITooltipBuilder b = jemi;
        b.add(Component.literal("Molten Iron"));
        b.add(Component.translatable("jei.tooltip.liquid.amount", 1000));
        List<Either<FormattedText, TooltipComponent>> lines = b.getLines();
        check(lines.size() == 2 && jemi.tooltip.size() == 2, "F-12 getLines sees both added lines (" + lines.size() + ", rendered " + jemi.tooltip.size() + ")");
        ListIterator<Either<FormattedText, TooltipComponent>> it = lines.listIterator();
        while (it.hasNext()) {
            Either<FormattedText, TooltipComponent> line = it.next();
            if (line.left().orElse(null) instanceof Component c && c.getContents() instanceof TranslatableContents tc && tc.getKey().equals("jei.tooltip.liquid.amount")) {
                it.remove();
            }
        }
        check(jemi.tooltip.size() == 1 && b.toLegacyToComponents().size() == 1, "F-12 Tinkers' ListIterator removal reached EMI's lists (rendered " + jemi.tooltip.size() + ", legacy " + b.toLegacyToComponents().size() + ")");
        b.addAll(List.of(Component.literal("1 Ingot"), Component.literal("9 Nuggets")));
        check(jemi.tooltip.size() == 3 && lines.size() == 3, "F-12 addAll after the removal (rendered " + jemi.tooltip.size() + ")");
        b.addKeyUsageComponent("probe.key.%s", new IJeiKeyMapping() {
            @Override public boolean isActiveAndMatches(InputConstants.Key key) { return false; }
            @Override public boolean isUnbound() { return false; }
            @Override public Component getTranslatedKeyMessage() { return Component.literal("R"); }
        });
        check(jemi.tooltip.size() == 4, "F-12 addKeyUsageComponent adds a line (rendered " + jemi.tooltip.size() + ")");
        b.clearIngredient();
        b.clear();
        check(jemi.tooltip.isEmpty() && lines.isEmpty() && b.toLegacyToComponents().isEmpty(), "F-12 clear empties EMI's lists and the view");
    }

    private static void extras() {
        JemiRecipeExtrasBuilder jemi = new JemiRecipeExtrasBuilder(null);
        IRecipeExtrasBuilder ex = jemi;
        IDrawableWidget arrow = ex.addAnimatedRecipeArrowWidget(100).setPosition(56, 18).setTooltip(Component.literal("Melting time"));
        IDrawableWidget area = ex.addTooltipArea(0, 2, 121, 11).setTooltip(List.of(Component.literal("line a"), Component.literal("line b")));
        IDrawableWidget plus = ex.addRecipePlusSignWidget().setPosition(0, 0, 100, 100, HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        ex.addRecipeArrowWidget().setPosition(10, 10).setTooltip((IRecipeWidgetTooltipCallback) t -> t.add(Component.literal("from a callback")));
        ex.addDrawableWidget(new IDrawable() {
            @Override public int getWidth() { return 16; }
            @Override public int getHeight() { return 16; }
            @Override public void draw(GuiGraphics graphics, int x, int y) {}
        }).setPosition(83, 26).setTooltip(Component.literal("ore rate"));
        ex.addAnimatedRecipeFlameWidget(200).setPosition(1, 1);
        ex.addButton(40, 20, () -> {}).setMessage(Component.literal("Go")).setActive(true);
        ICycleButtonWidget<String> cycle = ex.addCycleButton(List.of("a", "b", "c"), "b", 40, 20, v -> {});
        ITextWidget text = ex.addText(Component.literal("Melting"), 100, 10).setPosition(3, 2).setTooltip(Component.literal("title tip"));
        IPlaceable<?> oldArrow = ex.addRecipeArrow();
        oldArrow.setPosition(0, 0, 100, 100, HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        check(arrow.getWidth() == 24 && arrow.getHeight() == 17, "F-14 the arrow widget has EMI's arrow size (" + arrow.getWidth() + "x" + arrow.getHeight() + ")");
        check(area.getWidth() == 121 && area.getHeight() == 11, "F-14 the tooltip area keeps its size");
        int plusX = HorizontalAlignment.CENTER.getXPos(100, plus.getWidth());
        int plusY = VerticalAlignment.CENTER.getYPos(100, plus.getHeight());
        check(plus instanceof JemiPlaceable<?> p && p.x == plusX && p.y == plusY, "F-14 the aligned setPosition centres the plus sign as JEI's alignment computes it (" + plusX + ", " + plusY + ")");
        check(oldArrow instanceof JemiPlaceable<?> p2 && p2.x == HorizontalAlignment.CENTER.getXPos(100, 24), "F-14 EMI's own placeable gained the aligned setPosition");
        check(cycle.getValue().equals("b"), "F-14 the cycle button starts at its initial value");
        check(text instanceof JemiPlaceable<?> tp && tp.x == 3 && tp.y == 2, "F-15 the text widget keeps its position");
        check(jemi.widgets.size() == 10, "F-14/F-15 ten builders registered (animated arrow, area, plus, arrow, drawable, flame, button, cycle, text, old arrow): " + jemi.widgets.size());

        List<Widget> built = new ArrayList<>();
        WidgetHolder holder = new WidgetHolder() {
            @Override public int getWidth() { return 176; }
            @Override public int getHeight() { return 100; }
            @Override public <T extends Widget> T add(T widget) { built.add(widget); return widget; }
        };
        for (JemiWidgetBuilder wb : jemi.widgets) {
            wb.addWidgets(holder);
        }
        check(built.size() == 12, "F-14/F-15 twelve EMI widgets built (two for each animated one): " + built.size());
        Widget arrowTexture = built.stream().filter(w -> w instanceof TextureWidget && w.getBounds().x() == 56 && w.getBounds().y() == 18).findFirst().orElse(null);
        List<ClientTooltipComponent> arrowTip = arrowTexture == null ? List.of() : arrowTexture.getTooltip(60, 20);
        check(arrowTip.size() == 1, "F-14 the animated arrow's texture carries its tooltip line (" + arrowTip.size() + ")");
        Widget areaWidget = built.stream().filter(w -> w instanceof TooltipWidget).findFirst().orElse(null);
        check(areaWidget != null && areaWidget.getBounds().width() == 121 && areaWidget.getTooltip(5, 5).size() == 2, "F-14 the tooltip area is an EMI tooltip widget with two lines");
        Widget callbackArrow = built.stream().filter(w -> w instanceof TextureWidget && w.getBounds().x() == 10 && w.getBounds().y() == 10).findFirst().orElse(null);
        check(callbackArrow != null && callbackArrow.getTooltip(12, 12).size() == 1, "F-14 a callback tooltip runs through EMI's builder");
        Widget textWidget = built.stream().filter(w -> w.getBounds().x() == 3 && w.getBounds().y() == 2 && w.getBounds().width() == 100).findFirst().orElse(null);
        check(textWidget != null && textWidget.getTooltip(4, 3).size() == 1, "F-15 the text widget is built as an EMI drawable with its tooltip");
    }
}
