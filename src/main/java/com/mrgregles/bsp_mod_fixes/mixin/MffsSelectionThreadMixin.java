package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.mffs.SelectionThread;
import dev.su5ed.mffs.blockentity.ProjectorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * F-43: the one {@code completeAsync(supplier)} in MFFS's {@code runSelectionTask}, which put the field's selection on the
 * common ForkJoinPool, goes through {@link SelectionThread}, which runs the same supplier on the server thread between ticks.
 * {@code allow = 1} makes a second such call in a changed MFFS fail the mixin instead of being changed unseen. Targets an MFFS
 * class, whose names are never remapped ({@code remap = false}); applied only beside exactly the MFFS build it was written for
 * ({@code fix/mffs/MffsGate}); switched off by {@code [mffs] selectOnServerThread = false}.
 */
@Mixin(value = ProjectorBlockEntity.class, remap = false)
public abstract class MffsSelectionThreadMixin {
    @Redirect(method = "runSelectionTask",
              at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;completeAsync(Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;"),
              allow = 1)
    private CompletableFuture<Object> bsp_mod_fixes$selectOnTheServer(CompletableFuture<Object> future, Supplier<Object> supplier) {
        return SelectionThread.start(future, supplier, ((BlockEntity) (Object) this).getLevel());
    }
}
