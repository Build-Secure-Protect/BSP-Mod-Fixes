package com.mrgregles.bsp_mod_fixes.fix.aae;

import appeng.api.config.Actionable;
import com.mrgregles.bsp_mod_fixes.net.FixesNetwork;
import com.mrgregles.bsp_mod_fixes.net.QuantumAbsorbedMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.pedroksl.advanced_ae.common.definitions.AAEConfig;
import net.pedroksl.advanced_ae.common.items.armors.QuantumArmorBase;

import java.util.ArrayList;
import java.util.List;

/**
 * F-25, the part that touches AdvancedAE's classes: the replacement of {@code AAELivingEntityEvents.incomingDamage}, called
 * by {@code mixin/AaeDamageAbsorptionMixin} in its place. Same walk over the armor slots, same percentage from AdvancedAE's
 * config, same price, same cancel rule ({@link QuantumAbsorption}); then one packet to the wearer when something was
 * absorbed, for the client's suit readout. Loaded only when the mixin is applied, so this class is never touched without
 * AdvancedAE.
 */
public final class QuantumAbsorptionHandler {
    private QuantumAbsorptionHandler() {}

    public static void handle(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!target.isAlive() || !(event.getAmount() > 0) || !(target instanceof Player player)) {
            return;
        }
        List<ItemStack> pieces = new ArrayList<>(QuantumAbsorption.PIECES);
        for (ItemStack stack : player.getArmorSlots()) {
            if (stack != null && !stack.isEmpty() && stack.getItem() instanceof QuantumArmorBase) {
                pieces.add(stack);
            }
        }
        double[] charges = new double[pieces.size()];
        for (int i = 0; i < pieces.size(); i++) {
            charges[i] = ((QuantumArmorBase) pieces.get(i).getItem()).getAECurrentPower(pieces.get(i));
        }
        float hit = event.getAmount();
        QuantumAbsorption.Plan plan = QuantumAbsorption.plan(hit, AAEConfig.instance().getPercentageDamageAbsorption(), charges);
        for (int i = 0; i < pieces.size(); i++) {
            if (plan.paid()[i] > 0) {
                ((QuantumArmorBase) pieces.get(i).getItem()).extractAEPower(pieces.get(i), plan.paid()[i], Actionable.MODULATE);
            }
        }
        if (QuantumAbsorption.worthReporting(plan)) {
            event.setAmount(Math.max(0, hit - plan.absorbed()));
            if (player instanceof ServerPlayer serverPlayer) {
                FixesNetwork.sendTo(serverPlayer, new QuantumAbsorbedMessage(hit, plan.absorbed(), plan.energy()));
                QuantumAbsorption.reported();
            }
        }
        if (QuantumAbsorption.cancelsUnder(event.getAmount())) {
            event.setCanceled(true);
        }
    }
}
