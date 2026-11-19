package com.mrgregles.bsp_mod_fixes.fix.emi;

import java.util.List;
import java.util.stream.Stream;

/**
 * F-41 (EMI 1.1.24+1.20.1+forge): EMI's JEI recipe slot views gain the members JEI added to {@code IRecipeSlotView} after EMI
 * was written: {@code getAllIngredientsList()} (JEI 15.34), {@code getDisplayedIngredients()} (15.52) and {@code getTagKey()}
 * (15.53), and the slot drawable {@code IRecipeSlotDrawable.drawTooltip}. The pack's JEI 15.74 calls the first from its own
 * {@code BasicRecipeTransferHandler}, the handler a mod gets from {@code addRecipeTransferHandler(menu, menuType, recipeType,
 * slot indices)}: when EMI's recipe screen opens over a container screen with such a handler for the recipe's type, EMI's fill
 * button asks {@code canCraft}, JEI runs the transfer in simulation and reads every slot's ingredient list, and EMI's
 * {@code JemiRecipeSlot} throws {@code AbstractMethodError}, which lands as an "Error Rendering" card on every recipe of that
 * machine (Ender IO's Enchanter, Alloy Smelter, Primitive Alloy Smelter, SAG Mill, Slice'N'Splice and Soul Binder; Apotheosis,
 * Botania, Farmer's Delight, Integrated Dynamics, Mekanism, Railcraft and RFTools Control screens too). {@code
 * EmiJemiSlotViewMixin} adds the members, in the F-14 pattern; the list is the slot's ingredients as JEI's own slot gives them,
 * the displayed ingredients are the slot's one ingredient unfiltered, the tag key is EMI's when the ingredient is a tag.
 * {@code EmiJemiSlotDrawableMixin} does the same for EMI's slot drawable, which EMI 1.1.24 never constructs: defensive, its
 * {@code drawTooltip} untested. The JEI versions are the API source's {@code @since} tags. Gated like every EMI fix: exactly the pinned EMI,
 * on a client; the classes exist only beside JEI. No switch: a method, once added, cannot be taken back by a config read.
 */
public final class SlotViews {
    public static final String ID = "F-41";
    public static final String WHAT = "EMI's JEI recipe slot views gain the JEI 15.34 to 15.53 members they lacked (the ingredient list, the displayed group, the tag key, the drawable's tooltip), so JEI's own transfer handlers no longer throw from EMI's recipe screen over a machine's screen";
    public static final String TARGET = EmiGate.TARGET;

    private SlotViews() {}

    /** The stream's elements as an unmodifiable list in order, which is what JEI's slot answers for its ingredient list. */
    public static <T> List<T> list(Stream<T> ingredients) {
        return ingredients.toList();
    }
}
