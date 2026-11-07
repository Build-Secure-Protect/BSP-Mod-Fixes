package com.mrgregles.bsp_mod_fixes.fix.aae;

/**
 * F-25, the suit readout's arithmetic, pure so the GameTest server can check it: where the plate sits, how it slides, when
 * the damage row shows, where the tab is. Sizes are in GUI units (the scaled screen Minecraft draws the HUD in). The plate
 * sits flush to the screen's left edge above the hotbar's left end; it slides in when a quantum piece is worn, off when
 * none is, and off to its tab when collapsed (D45); the damage row clears three seconds after a hit (D50).
 *
 * <pre>
 *   x = offset (0 open; -PLATE_W collapsed, tab showing; -(PLATE_W + TAB_W) hidden)
 *   +---------------------------------------+--+
 *   | glyph  DMG [===bar===] 0.6/2.0        |  |  <- tab, TAB_H tall, centred
 *   |        AE  [===bar===]    100%        |  |
 *   |        950,000,000 AE                 +--+
 *   +---------------------------------------+
 * </pre>
 */
public final class ReadoutState {
    public static final int PLATE_W = 140;
    public static final int PLATE_H = 40;
    public static final int TAB_W = 9;
    public static final int TAB_H = 20;
    /** The least the plate assumes the left HUD rows take: Forge's HUD starts at 39 and adds 10 per row (hearts 49, armor 59, more with stacked health); the live figure is used when larger. */
    public static final int DEFAULT_LEFT_HEIGHT = 49;
    /** The gap between the plate's bottom edge and the top of the left HUD rows. */
    public static final int GAP = 2;
    /** A slide takes this many ticks from open to hidden. */
    public static final int SLIDE_TICKS = 10;
    /** The damage row shows for this long after a hit, in ticks (D50: three seconds), the last fifth fading. */
    public static final int HIT_ROW_TICKS = 60;
    public static final int HIT_ROW_FADE_TICKS = 12;
    /** The slice paid is drawn at least this wide on the charge bar, since the true width is under a pixel. */
    public static final int MIN_NOTCH = 2;
    /** The bars' geometry inside the plate. */
    public static final int GLYPH_X = 8, LABEL_X = 30, BAR_X = 52, BAR_W = 40, BAR_H = 5, VALUE_RIGHT = 136;
    public static final int DMG_ROW_Y = 5, AE_ROW_Y = 17, FIGURE_Y = 29;

    public static final int HIDDEN = -(PLATE_W + TAB_W);
    public static final int COLLAPSED = -PLATE_W;
    public static final int OPEN = 0;

    private ReadoutState() {}

    /** Where the plate wants to be: open while a piece is worn, off to its tab when collapsed, off screen when nothing is worn. */
    public static int targetOffset(boolean worn, boolean collapsed) {
        if (!worn) {
            return HIDDEN;
        }
        return collapsed ? COLLAPSED : OPEN;
    }

    /** One tick of the slide towards the target: a fixed step, never past it. */
    public static float step(float offset, int target) {
        float perTick = (float) (PLATE_W + TAB_W) / SLIDE_TICKS;
        if (offset < target) {
            return Math.min(target, offset + perTick);
        }
        if (offset > target) {
            return Math.max(target, offset - perTick);
        }
        return offset;
    }

    /** Whether anything is drawn at this offset: nothing once the tab has left the screen. */
    public static boolean visible(float offset) {
        return offset > HIDDEN;
    }

    /** The damage row's brightness, 0 to 1: full after a hit, fading over the last {@link #HIT_ROW_FADE_TICKS}, gone after {@link #HIT_ROW_TICKS}; 0 with no hit. */
    public static float hitRowAlpha(long now, long hitAt) {
        if (hitAt < 0 || now < hitAt) {
            return 0;
        }
        long age = now - hitAt;
        if (age >= HIT_ROW_TICKS) {
            return 0;
        }
        long fadeStart = HIT_ROW_TICKS - HIT_ROW_FADE_TICKS;
        if (age < fadeStart) {
            return 1;
        }
        return 1 - (float) (age - fadeStart) / HIT_ROW_FADE_TICKS;
    }

    /** The plate's top edge for a screen of this height, clear of the left HUD rows (hearts, armor, whatever stacks above them). */
    public static int plateY(int screenHeight, int leftHeight) {
        return screenHeight - Math.max(DEFAULT_LEFT_HEIGHT, leftHeight) - GAP - PLATE_H;
    }

    /** The tab's rectangle, {x, y, w, h}, for a plate at this offset: on the plate's right edge, vertically centred. */
    public static int[] tabRect(float offset, int screenHeight, int leftHeight) {
        return new int[]{Math.round(offset) + PLATE_W, plateY(screenHeight, leftHeight) + (PLATE_H - TAB_H) / 2, TAB_W, TAB_H};
    }

    /** Whether a point (GUI units) is on the tab. */
    public static boolean onTab(float offset, int screenHeight, int leftHeight, double x, double y) {
        int[] r = tabRect(offset, screenHeight, leftHeight);
        return x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }

    /** A bar's filled width for a fraction of its track, 0 to {@link #BAR_W}; anything above nothing shows at least one pixel. */
    public static int fillWidth(double fraction) {
        if (Double.isNaN(fraction) || fraction <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.round(Math.min(1, fraction) * BAR_W));
    }

    /** The paid slice's width on the charge bar: its true share of the track, never under {@link #MIN_NOTCH}, never more than the fill. */
    public static int notchWidth(double paid, double capacity, int fill) {
        if (fill <= 0 || !(paid > 0)) {
            return 0;
        }
        double share = capacity > 0 ? paid / capacity : 0;
        return Math.max(MIN_NOTCH, Math.min(fill, (int) Math.round(share * BAR_W)));
    }

    /** The charge as a fraction of the capacity, 0 to 1. */
    public static double chargeFraction(double charge, double capacity) {
        if (!(capacity > 0) || Double.isNaN(charge)) {
            return 0;
        }
        return Math.max(0, Math.min(1, charge / capacity));
    }

    /** The texts the plate shows for a hit: the absorbed share over the hit, one decimal each. */
    public static String hitText(float hit, float absorbed) {
        return String.format(java.util.Locale.ROOT, "%.1f/%.1f", absorbed, hit);
    }

    public static String paidText(double paid) {
        return String.format(java.util.Locale.ROOT, "-%,d AE", Math.round(paid));
    }

    public static String percentText(double fraction) {
        return String.format(java.util.Locale.ROOT, "%d%%", Math.round(fraction * 100));
    }

    public static String chargeText(double charge) {
        return String.format(java.util.Locale.ROOT, "%,d AE", Math.round(charge));
    }
}
