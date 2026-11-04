package com.mrgregles.bsp_mod_fixes.mixin;

import com.mojang.datafixers.util.Either;
import com.mrgregles.bsp_mod_fixes.fix.emi.EmiTooltips;
import com.mrgregles.bsp_mod_fixes.fix.emi.LiveLines;
import com.mrgregles.bsp_mod_fixes.fix.emi.TooltipBuilderFix;
import com.mrgregles.bsp_mod_fixes.fix.emi.TooltipLinesAccess;
import dev.emi.emi.jemi.impl.JemiTooltipBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.runtime.IJeiKeyMapping;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * F-12: the four JEI 15.39+ methods EMI's tooltip builder lacks. Every line EMI adds through its own two add methods is
 * recorded in a live line list over EMI's {@code tooltip} and {@code legacyText}; {@code getLines()} returns that list.
 * Applied only when EMI is at the pinned version (EmiGate).
 */
@Mixin(value = JemiTooltipBuilder.class, remap = false)
public abstract class EmiTooltipBuilderMixin implements ITooltipBuilder, TooltipLinesAccess {
    @Shadow @Final public List<ClientTooltipComponent> tooltip;
    @Shadow @Final private List<Component> legacyText;

    @Unique
    private LiveLines<Either<FormattedText, TooltipComponent>, ClientTooltipComponent, Component> bsp_mod_fixes$lines;

    @Override
    public LiveLines<Either<FormattedText, TooltipComponent>, ClientTooltipComponent, Component> bsp_mod_fixes$lines() {
        if (bsp_mod_fixes$lines == null) {
            bsp_mod_fixes$lines = EmiTooltips.lines(tooltip, legacyText);
        }
        return bsp_mod_fixes$lines;
    }

    @Inject(method = "add(Lnet/minecraft/network/chat/FormattedText;)V", at = @At("TAIL"))
    private void bsp_mod_fixes$recordText(FormattedText text, CallbackInfo ci) {
        bsp_mod_fixes$lines().recordAppended(Either.left(text));
    }

    @Inject(method = "add(Lnet/minecraft/world/inventory/tooltip/TooltipComponent;)V", at = @At("TAIL"))
    private void bsp_mod_fixes$recordComponent(TooltipComponent component, CallbackInfo ci) {
        bsp_mod_fixes$lines().recordAppended(Either.right(component));
    }

    @Override
    public List<Either<FormattedText, TooltipComponent>> getLines() {
        return bsp_mod_fixes$lines();
    }

    @Override
    public void clear() {
        bsp_mod_fixes$lines().clear();
    }

    @Override
    public void clearIngredient() {
        // EMI's setIngredient keeps nothing, so there is nothing to clear.
    }

    @Override
    public void addKeyUsageComponent(String translationKey, IJeiKeyMapping keyMapping) {
        add(TooltipBuilderFix.keyUsage(translationKey, keyMapping.getTranslatedKeyMessage()));
    }
}
