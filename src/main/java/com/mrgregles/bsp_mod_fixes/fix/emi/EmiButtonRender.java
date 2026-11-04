package com.mrgregles.bsp_mod_fixes.fix.emi;

import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.List;
import java.util.function.Supplier;

/**
 * F-14: a minimal working button for EMI's recipe screen (D17): a bordered box, lighter while active and under the mouse,
 * its icon or message centred, running its action on a left click. JEI's own buttons use JEI's textures; this one needs
 * nothing of JEI's at draw time. Client only.
 */
public final class EmiButtonRender extends Widget {
    private final Bounds bounds;
    private final Supplier<Component> message;
    private final Supplier<IDrawable> icon;
    private final Supplier<Boolean> active;
    private final Supplier<Boolean> visible;
    private final Runnable action;
    private final WidgetTooltip tooltip;

    public EmiButtonRender(int x, int y, int width, int height, Supplier<Component> message, Supplier<IDrawable> icon,
                           Supplier<Boolean> active, Supplier<Boolean> visible, Runnable action, WidgetTooltip tooltip) {
        this.bounds = new Bounds(x, y, width, height);
        this.message = message;
        this.icon = icon;
        this.active = active;
        this.visible = visible;
        this.action = action;
        this.tooltip = tooltip;
    }

    @Override
    public Bounds getBounds() {
        return visible.get() ? bounds : Bounds.EMPTY;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (!visible.get()) {
            return;
        }
        boolean enabled = active.get();
        boolean hover = enabled && bounds.contains(mouseX, mouseY);
        int x = bounds.x(), y = bounds.y(), w = bounds.width(), h = bounds.height();
        graphics.fill(x, y, x + w, y + h, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, !enabled ? 0xFF3A3A3A : hover ? 0xFF8A8A8A : 0xFF6E6E6E);
        IDrawable drawable = icon.get();
        if (drawable != null) {
            drawable.draw(graphics, x + (w - drawable.getWidth()) / 2, y + (h - drawable.getHeight()) / 2);
        }
        Component text = message.get();
        if (text != null) {
            graphics.drawCenteredString(Minecraft.getInstance().font, text, x + w / 2, y + (h - 8) / 2, enabled ? 0xFFFFFF : 0xA0A0A0);
        }
    }

    @Override
    public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
        return visible.get() ? tooltip.lines(mouseX, mouseY) : List.of();
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || !visible.get() || !active.get()) {
            return false;
        }
        action.run();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        return true;
    }
}
