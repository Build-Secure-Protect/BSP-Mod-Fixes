package com.mrgregles.bsp_mod_fixes.mixin.gametest;

import net.minecraft.network.Connection;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * GameTest server only: lets the tests build the {@link NetworkEvent.Context} a packet handler receives, so a mock player's
 * packet can be handed to the patched handlers exactly as the network layer would. The constructor is package-private in Forge.
 */
@Mixin(value = NetworkEvent.Context.class, remap = false)
public interface NetworkContextInvoker {
    @Invoker("<init>")
    static NetworkEvent.Context bsp_mod_fixes$newContext(Connection connection, NetworkDirection direction, int index) {
        throw new AssertionError("replaced by Mixin");
    }
}
