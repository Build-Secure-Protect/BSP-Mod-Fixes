package com.mrgregles.bsp_mod_fixes.client;

import appeng.api.implementations.items.IAEItemPowerStorage;
import com.mrgregles.bsp_mod_fixes.FixesClientConfig;
import com.mrgregles.bsp_mod_fixes.fix.aae.AaeGate;
import com.mrgregles.bsp_mod_fixes.fix.aae.ReadoutState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * F-25, the suit readout on the client: a plate at the screen's left edge above the hotbar while AdvancedAE's quantum armor
 * is worn, with the suit's charge (read from the worn pieces every few ticks) and, for three seconds after a hit, the
 * damage row the server reported ({@code QuantumAbsorbedMessage}). A tab on the plate's right edge collapses it off screen
 * to the tab alone, by the key binding or a click on the tab while a screen is open; the state is kept in the client config
 * (D45, D49-D51). Geometry and timing in {@link ReadoutState}; the look is the BSP dark board.
 */
public final class QuantumReadout {
    private static final int PANEL = 0xFF10151C, DIM = 0xFF2A2F3A, MUTED = 0xFF9AA3B5, TEXT = 0xFFE6EAF2, TRACK = 0xFF0C0E12;
    private static final int TEAL = 0xFF19D3B0, GOLD = 0xFFFFD23A, GOLD_DIM = 0x8CFFD23A, HIT = 0x8CC4323A, NOTCH = 0xFFFFF4B8, VIOLET = 0xFFB58CFF;
    private static final int READ_EVERY = 5;

    private static float offset = ReadoutState.HIDDEN;
    private static boolean worn;
    private static double charge, capacity;
    private static long hitAt = -1;
    private static float lastHit, lastAbsorbed;
    private static double lastPaid;
    private static long tick;
    /** What the left HUD rows take, as Forge's HUD reports it at the last render. */
    private static int leftHeight = ReadoutState.DEFAULT_LEFT_HEIGHT;

    private QuantumReadout() {}

    /** The server reported an absorption: the damage row shows it from now. */
    public static void onAbsorbed(float hit, float absorbed, double paid) {
        lastHit = hit;
        lastAbsorbed = absorbed;
        lastPaid = paid;
        hitAt = tick;
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tick++;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            worn = false;
            offset = ReadoutState.HIDDEN;
            hitAt = -1;
            return;
        }
        if (tick % READ_EVERY == 0) {
            readSuit(player);
        }
        while (ClientSetup.TOGGLE_READOUT.consumeClick()) {
            toggle();
        }
        offset = ReadoutState.step(offset, ReadoutState.targetOffset(worn, FixesClientConfig.collapsed()));
    }

    private static void readSuit(LocalPlayer player) {
        boolean any = false;
        double current = 0, max = 0;
        for (ItemStack stack : player.getArmorSlots()) {
            if (!stack.isEmpty() && stack.getItem() instanceof IAEItemPowerStorage storage && isQuantum(stack)) {
                any = true;
                current += storage.getAECurrentPower(stack);
                max += storage.getAEMaxPower(stack);
            }
        }
        worn = any;
        charge = current;
        capacity = max;
    }

    private static boolean isQuantum(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && key.getNamespace().equals(AaeGate.MOD_ID);
    }

    public static void toggle() {
        FixesClientConfig.setCollapsed(!FixesClientConfig.collapsed());
    }

    /** The HUD overlay (registered above everything else). */
    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        if (!ReadoutState.visible(offset) || Minecraft.getInstance().options.hideGui) {
            return;
        }
        leftHeight = gui.leftHeight;
        int x = Math.round(offset);
        int y = ReadoutState.plateY(screenHeight, leftHeight);
        drawPlate(g, x, y);
        drawTab(g, offset, screenHeight);
    }

    /**
     * The tab again on top of a container screen (the inventory, a chest), so it can be clicked there; the HUD underneath is
     * dimmed by the screen. Only container screens: on a small window the tab's spot can coincide with another screen's
     * controls (the death screen's buttons, for one), and a click there must stay that screen's.
     */
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (worn && ReadoutState.visible(offset) && tabOnScreen(event.getScreen()) && !Minecraft.getInstance().options.hideGui) {
            drawTab(event.getGuiGraphics(), offset, event.getScreen().height);
        }
    }

    /** A left click on the tab over a container screen toggles the readout, unless a slot or a widget of the screen is under the cursor there. */
    public static void onScreenClick(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen screen = event.getScreen();
        if (worn && event.getButton() == 0 && ReadoutState.visible(offset) && tabOnScreen(screen)
                && ReadoutState.onTab(offset, screen.height, leftHeight, event.getMouseX(), event.getMouseY())
                && ((AbstractContainerScreen<?>) screen).getSlotUnderMouse() == null
                && screen.getChildAt(event.getMouseX(), event.getMouseY()).isEmpty()) {
            toggle();
            event.setCanceled(true);
        }
    }

    private static boolean tabOnScreen(Screen screen) {
        return screen instanceof AbstractContainerScreen<?>;
    }

    private static void drawPlate(GuiGraphics g, int x, int y) {
        Font font = Minecraft.getInstance().font;
        g.fill(x, y, x + ReadoutState.PLATE_W, y + ReadoutState.PLATE_H, PANEL);
        g.fill(x, y, x + ReadoutState.PLATE_W, y + 1, TEAL);
        g.fill(x + ReadoutState.PLATE_W - 1, y, x + ReadoutState.PLATE_W, y + ReadoutState.PLATE_H, DIM);
        g.fill(x, y + ReadoutState.PLATE_H - 1, x + ReadoutState.PLATE_W, y + ReadoutState.PLATE_H, DIM);
        drawGlyph(g, x + ReadoutState.GLYPH_X, y + 9);

        // the damage row: the hit after the armor as the track, the absorbed share as the fill; shown after a hit, then cleared
        float alpha = ReadoutState.hitRowAlpha(tick, hitAt);
        int rowY = y + ReadoutState.DMG_ROW_Y;
        if (alpha > 0) {
            int a = Math.round(alpha * 255);
            g.drawString(font, Component.translatable("hud.bsp_mod_fixes.quantum_dmg"), x + ReadoutState.LABEL_X, rowY, withAlpha(MUTED, a), false);
            drawTrack(g, x + ReadoutState.BAR_X, rowY + 1);
            g.fill(x + ReadoutState.BAR_X, rowY + 1, x + ReadoutState.BAR_X + ReadoutState.BAR_W, rowY + 1 + ReadoutState.BAR_H, withAlpha(HIT, Math.round(alpha * 140)));
            int fill = ReadoutState.fillWidth(lastHit > 0 ? lastAbsorbed / lastHit : 0);
            if (fill > 0) {
                g.fill(x + ReadoutState.BAR_X, rowY + 1, x + ReadoutState.BAR_X + fill, rowY + 1 + ReadoutState.BAR_H, withAlpha(TEAL, a));
            }
            drawRight(g, font, ReadoutState.hitText(lastHit, lastAbsorbed), x + ReadoutState.VALUE_RIGHT, rowY, withAlpha(TEAL, a));
            // the AE paid floats off the plate's top edge
            int floatY = y - 2 - Math.round((1 - alpha) * 6) - (int) Math.min(8, (tick - hitAt) / 4);
            drawRight(g, font, ReadoutState.paidText(lastPaid), x + ReadoutState.VALUE_RIGHT, floatY, withAlpha(GOLD, a));
        }

        // the charge row: the four pieces together, the paid slice as a notch while the row shows
        int aeY = y + ReadoutState.AE_ROW_Y;
        g.drawString(font, Component.translatable("hud.bsp_mod_fixes.quantum_ae"), x + ReadoutState.LABEL_X, aeY, MUTED, false);
        drawTrack(g, x + ReadoutState.BAR_X, aeY + 1);
        double fraction = ReadoutState.chargeFraction(charge, capacity);
        int fill = ReadoutState.fillWidth(fraction);
        if (fill > 0) {
            g.fill(x + ReadoutState.BAR_X, aeY + 1, x + ReadoutState.BAR_X + fill, aeY + 1 + ReadoutState.BAR_H, GOLD_DIM);
            int notch = alpha > 0 ? ReadoutState.notchWidth(lastPaid, capacity, fill) : 0;
            if (notch > 0) {
                g.fill(x + ReadoutState.BAR_X + fill - notch, aeY + 1, x + ReadoutState.BAR_X + fill, aeY + 1 + ReadoutState.BAR_H, withAlpha(NOTCH, Math.round(alpha * 255)));
            }
        }
        drawRight(g, font, ReadoutState.percentText(fraction), x + ReadoutState.VALUE_RIGHT, aeY, GOLD);
        g.drawString(font, ReadoutState.chargeText(charge), x + ReadoutState.LABEL_X, y + ReadoutState.FIGURE_Y, MUTED, false);
    }

    private static void drawTab(GuiGraphics g, float offset, int screenHeight) {
        int[] r = ReadoutState.tabRect(offset, screenHeight, leftHeight);
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], PANEL);
        g.fill(r[0] + r[2] - 1, r[1], r[0] + r[2], r[1] + r[3], DIM);
        g.fill(r[0], r[1], r[0] + r[2], r[1] + 1, DIM);
        g.fill(r[0], r[1] + r[3] - 1, r[0] + r[2], r[1] + r[3], DIM);
        boolean collapsed = FixesClientConfig.collapsed();
        // the arrow: its tip (i = 0) points left to collapse, right to open; three steps of one pixel
        int cx = r[0] + r[2] / 2, cy = r[1] + r[3] / 2 - 2;
        for (int i = 0; i < 3; i++) {
            int dx = collapsed ? 1 - i : i - 1;
            g.fill(cx + dx, cy - i, cx + dx + 1, cy - i + 1, TEAL);
            g.fill(cx + dx, cy + i, cx + dx + 1, cy + i + 1, TEAL);
        }
        if (collapsed) {
            // a sliver of the charge so the collapsed tab still says how full the suit is
            int w = (int) Math.round(ReadoutState.chargeFraction(charge, capacity) * (r[2] - 2));
            g.fill(r[0] + 1, r[1] + r[3] - 3, r[0] + 1 + w, r[1] + r[3] - 2, GOLD);
        }
    }

    /** A small chestplate: shoulders, body, a violet core. */
    private static void drawGlyph(GuiGraphics g, int x, int y) {
        g.fill(x + 3, y - 3, x + 11, y, DIM);
        g.fill(x, y, x + 14, y + 12, DIM);
        g.fill(x + 3, y + 12, x + 11, y + 14, DIM);
        g.fill(x, y, x + 14, y + 1, MUTED);
        g.fill(x + 5, y + 3, x + 9, y + 7, VIOLET);
    }

    private static void drawTrack(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + ReadoutState.BAR_W + 1, y + ReadoutState.BAR_H + 1, DIM);
        g.fill(x, y, x + ReadoutState.BAR_W, y + ReadoutState.BAR_H, TRACK);
    }

    private static void drawRight(GuiGraphics g, Font font, String text, int right, int y, int color) {
        g.drawString(font, text, right - font.width(text), y, color, false);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }
}
