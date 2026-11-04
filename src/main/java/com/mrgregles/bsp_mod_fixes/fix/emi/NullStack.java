package com.mrgregles.bsp_mod_fixes.fix.emi;

/**
 * F-11 (EMI 1.1.24+1.20.1+forge): JEI's {@code IIngredientAcceptor.addIngredients(type, List<@Nullable I>)} allows null
 * entries, meaning "no ingredient in this position", and JEI's own acceptor skips them. EMI's {@code JemiUtil.getStack(type,
 * ingredient)} hands an item entry to {@code EmiStack.of(ItemStack)} unchecked, which dereferences it, so a recipe with such
 * an entry is dropped at reload with "Exception thrown adding adding JEI recipe". Tinkers' Construct's entity melting and
 * severing categories pass null for every entity without a spawn egg. The mixin answers {@code EmiStack.EMPTY} for a null
 * ingredient, which EMI's acceptor already discards.
 */
public final class NullStack {
    public static final String ID = "F-11";
    public static final String WHAT = "a null item in a JEI ingredient list converts to an empty EMI stack instead of crashing the recipe";
    public static final String TARGET = EmiGate.TARGET;

    private NullStack() {}

    /** Whether EMI's conversion must answer the empty stack: the ingredient is absent. */
    public static boolean isMissing(Object ingredient) {
        return ingredient == null;
    }
}
