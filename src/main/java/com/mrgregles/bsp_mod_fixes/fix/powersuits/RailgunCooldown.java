package com.mrgregles.bsp_mod_fixes.fix.powersuits;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import lehjr.numina.common.tags.TagUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * F-47 (Modular Powersuits 2.3.13): the Rail Gun's cooldown is counted by game time in its own {@code use}, so it ends 15
 * ticks after the shot whether or not the module is online. Powersuits writes {@code cooldown = 15} into the fist's
 * {@code MMModItem} compound at each shot and counts it down only in {@code Ticker.onPlayerTickActive}, which Numina's
 * {@code ModularItem.tick} calls only while the module is online, while {@code use} itself fires without looking at the
 * online flag: a Rail Gun whose module is offline (its {@code Active} tag false, however it got there: a module toggle key,
 * the Tinker Table's toggle, possibly Numina's static default at an unlucky lookup, which MF-42 did not reproduce) fires once and never again, because nothing
 * counts its cooldown down, until a mode change rewrites {@code MMModItem} from the module list and wipes the cooldown with
 * it. That is the owner's "just shot once, and it's needed to change weapon to shoot again" (MF-42). Mod at fault: Modular
 * Powersuits, whose cooldown's clock stops with the module's tick while its trigger does not.
 *
 * <p>The fix ({@code PowersuitsRailgunCooldownMixin}) redirects the one cooldown read and the one cooldown write in
 * {@code Ticker.use}: the write keeps Powersuits' own {@code cooldown} tag and stamps, in the same compound, the game time
 * at which the cooldown ends ({@link #READY_AT}); the read answers the ticks left by that stamp and the level's game time,
 * through the pure {@link #remaining}: no stamp (a fist from before this fix, or a compound just rewritten by a mode change)
 * means ready, as does a stamp more than a full cooldown plus a few ticks of slack ahead (a clock from another server,
 * carried over by the inventory sync; the slack is for a client whose clock runs a tick or two behind the server's between
 * time syncs, so its own cosmetic {@code use} agrees with the server's; a foreign clock that lands inside the slack costs
 * those few extra ticks once). The per-tick count-down still runs while the module is online; it no longer matters, and
 * once it removes Powersuits' tag the stamp stays in the compound until the next rewrite, by design. Both tags live in
 * {@code MMModItem}, so a mode change still resets the cooldown as before. No switch (D151's reasoning: the mod is pinned).
 */
public final class RailgunCooldown {
    public static final String ID = "F-47";
    public static final String WHAT = "the Rail Gun's cooldown is counted by game time in its use, so it ends 15 ticks after the shot whether or not the module is online (Powersuits counted it down only in the module's online tick, so an offline Rail Gun fired once until a weapon change wiped the cooldown)";
    public static final String TARGET = "Modular Powersuits 2.3.13";

    /** The tag, beside Powersuits' {@code cooldown} in the fist's {@code MMModItem} compound: the game time at which the cooldown ends. */
    public static final String READY_AT = "bsp_mod_fixes_railgunReadyAt";
    /** The Rail Gun's cooldown in ticks, the value Powersuits writes at each shot. */
    public static final double COOLDOWN_TICKS = 15.0;
    /** Ticks a stamp may lie beyond a full cooldown and still be ours: a client's clock between time syncs runs this much off the server's at most. */
    public static final double CLOCK_SLACK = 5.0;

    private static final LongAdder RELEASED = new LongAdder();
    private static final AtomicBoolean LOGGED = new AtomicBoolean();

    private RailgunCooldown() {}

    /**
     * Pure: the ticks of cooldown left. Without a stamp the gun is ready (nothing to wait for that can be trusted); with one,
     * what is left until {@code readyAt}, none once it has passed, and none when the stamp lies more than a full cooldown plus
     * {@link #CLOCK_SLACK} ahead of {@code now} (another server's clock).
     */
    public static double remaining(boolean hasStamp, long readyAt, long now) {
        if (!hasStamp) {
            return 0.0;
        }
        long left = readyAt - now;
        if (left <= 0 || left > COOLDOWN_TICKS + CLOCK_SLACK) {
            return 0.0;
        }
        return left;
    }

    /** The read in {@code use}, in place of Powersuits' {@code cooldown} tag: {@link #remaining} from the stamp and the level's game time. */
    public static double cooldown(ItemStack fist, String key, Level level) {
        CompoundTag tag = TagUtils.getMuseModularItemTag(fist);
        double stored = tag.contains(key, Tag.TAG_DOUBLE) ? tag.getDouble(key) : 0.0;
        double left = remaining(tag.contains(READY_AT, Tag.TAG_LONG), tag.getLong(READY_AT), level.getGameTime());
        if (left == 0.0 && stored > 0.0) {
            RELEASED.increment();
            if (LOGGED.compareAndSet(false, true)) {
                Fixes.LOGGER.info("BSP-Mod Fixes F-47: a Rail Gun's cooldown of {} ticks had not been counted down (its module is offline, or the fist predates the fix); the gun is ready by game time", stored);
            }
        }
        return left;
    }

    /** The write in {@code use}: Powersuits' own {@code cooldown} tag as before, and the stamp of the game time it ends at. */
    public static void shot(ItemStack fist, String key, double value, Level level) {
        TagUtils.setModularItemDoubleOrRemove(fist, key, value);
        TagUtils.getMuseModularItemTag(fist).putLong(READY_AT, level.getGameTime() + (long) value);
    }

    /** How many clicks found Powersuits' cooldown still standing while the stamp said the gun was ready. */
    public static long released() {
        return RELEASED.sum();
    }
}
