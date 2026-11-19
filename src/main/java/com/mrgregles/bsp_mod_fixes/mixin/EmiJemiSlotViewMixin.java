package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.emi.SlotViews;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.jemi.impl.JemiRecipeSlot;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * F-41: the JEI 15.34 to 15.53 members of {@code IRecipeSlotView} that EMI's recipe slot lacks ({@link SlotViews}). The
 * ingredient list is the slot's own stream as a list; the displayed ingredients are that same stream, unfiltered, since an EMI
 * slot holds one ingredient whose stacks rotate and EMI has no visibility filter (JEI's own transfer helper answers the same
 * way); the tag key is the tag when EMI's ingredient is one. Applied only when EMI is at the pinned
 * version, on a client ({@code fix/emi/EmiGate}).
 */
@Mixin(value = JemiRecipeSlot.class, remap = false)
public abstract class EmiJemiSlotViewMixin implements IRecipeSlotView {
    @Shadow @Final public EmiIngredient stack;

    @Shadow public abstract Stream<ITypedIngredient<?>> getAllIngredients();

    @Override
    public List<ITypedIngredient<?>> getAllIngredientsList() {
        return SlotViews.list(getAllIngredients());
    }

    @Override
    public Stream<ITypedIngredient<?>> getDisplayedIngredients() {
        return getAllIngredients();
    }

    @Override
    public Optional<TagKey<?>> getTagKey() {
        return stack instanceof TagEmiIngredient tag ? Optional.of(tag.key) : Optional.empty();
    }
}
