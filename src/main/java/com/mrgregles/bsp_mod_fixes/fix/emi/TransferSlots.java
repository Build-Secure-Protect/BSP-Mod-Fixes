package com.mrgregles.bsp_mod_fixes.fix.emi;

import com.mrgregles.bsp_mod_fixes.FixesClientConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * F-37 (EMI 1.1.24+1.20.1+forge): EMI wraps every JEI recipe transfer handler (Refined Storage's grid, Mekanism's QIO
 * crafting window and Formulaic Assemblicator) in its {@code JemiRecipeHandler}, whose {@code createSlotsView} lays a
 * shaped {@code EmiCraftingRecipe}'s inputs out for JEI as {@code inputs.subList(0, max(9, inputs.size()) - blankedSlots)}:
 * it assumes a shaped crafting recipe always carries nine inputs. EMI's own shaped recipes do (they pad to nine), but the
 * fallback EMI builds for a crafting recipe of another class (Mekanism's wrapped shaped recipe, EnderStorage's recolour
 * recipes, Quark's exclusion recipe) pads each row to three and the list no further, so a six-entry list is cut at eight and a
 * one-entry list at eight: {@code IndexOutOfBoundsException}, thrown while the recipe screen renders, outside EMI's own
 * catch, so the game crashes to the desktop as soon as such a recipe is viewed with one of those screens open. The mixin
 * redirects that one {@code subList} call here: both bounds are clamped to the list's size, and the layout then gets the
 * entries there are; EMI's crafting-category loop pads the rest to nine as it does for every other recipe. Pure arithmetic
 * here, so the GameTest server can check it without EMI; the switch is the client config's {@code [emi]
 * clampJeiTransferSlots}, read at each call.
 */
public final class TransferSlots {
    public static final String ID = "F-37";
    public static final String WHAT = "a shaped crafting recipe with fewer than nine inputs no longer crashes EMI's JEI transfer layout (Refined Storage grid, Mekanism QIO and Formulaic)";
    public static final String TARGET = EmiGate.TARGET;
    /** The recipes whose bounds have been clamped this session, each logged once. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private TransferSlots() {}

    /** The start index EMI asked for, held within the list: never below 0, never above {@code size}. */
    public static int clampedFrom(int from, int size) {
        return Math.min(Math.max(from, 0), size);
    }

    /** The end index EMI asked for, held within the list and never before the clamped start. */
    public static int clampedTo(int from, int to, int size) {
        return Math.min(Math.max(to, clampedFrom(from, size)), size);
    }

    /** Whether EMI's bounds already fit the list, so the fix changes nothing. */
    public static boolean fits(int from, int to, int size) {
        return from >= 0 && from <= to && to <= size;
    }

    /** Whether the switch is on: the client config's value, and on before that config has loaded. */
    public static boolean enabled() {
        return FixesClientConfig.clampJeiTransferSlots();
    }

    /** Whether this is the first clamp for this recipe id in the session (pure apart from the set; the tests reset it). */
    public static boolean firstClampOf(String recipeId) {
        return WARNED.add(recipeId);
    }

    /** For the tests: forgets which recipes were logged. */
    public static void forgetWarned() {
        WARNED.clear();
    }

    /**
     * What the mixin returns in place of EMI's {@code inputs.subList(from, to)}: the same call with both bounds held within
     * the list when the switch is on, EMI's own call (and its exception) when it is off. The first clamp of each recipe is
     * logged at WARN, so a new trigger shows in a player's log.
     */
    public static <T> List<T> subList(List<T> inputs, int from, int to, Object recipeId) {
        int size = inputs.size();
        if (!enabled() || fits(from, to, size)) {
            return inputs.subList(from, to);
        }
        int start = clampedFrom(from, size);
        int end = clampedTo(from, to, size);
        String id = recipeId == null ? "(a recipe without an id)" : recipeId.toString();
        if (firstClampOf(id)) {
            Fixes.LOGGER.warn("BSP-Mod Fixes {}: EMI asked for the input range [{}, {}) of the {}-entry shaped recipe {}; clamped to [{}, {}) so the JEI transfer layout does not crash",
                    ID, from, to, size, id, start, end);
        }
        return inputs.subList(start, end);
    }
}
