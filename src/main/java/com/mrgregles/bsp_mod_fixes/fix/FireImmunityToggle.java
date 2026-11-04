package com.mrgregles.bsp_mod_fixes.fix;

import com.mrgregles.bsp_mod_fixes.FixesConfig;

/**
 * F-10 (Numina 2.3.13), an option rather than a bug fix: Numina's player tick handler clears the fire of anyone wearing or
 * holding a modular item while the suit's heat is under 95 % of its maximum, so suit wearers are all but immune to burning. The
 * server config's {@code suitClearsFire} (default true, Powersuits' behaviour) can turn that off; the mixin routes Numina's
 * {@code clearFire()} call through {@link #clearsFire()}.
 */
public final class FireImmunityToggle {
    public static final String ID = "F-10";
    public static final String WHAT = "server config suitClearsFire (default true) decides whether a suit puts its wearer's fire out";
    public static final String TARGET = "Numina 2.3.13";

    private FireImmunityToggle() {}

    /** Whether Numina may clear the fire: the config value, or Powersuits' behaviour while the server config is not loaded. */
    public static boolean clearsFire() {
        return !FixesConfig.SPEC.isLoaded() || FixesConfig.SUIT_CLEARS_FIRE.get();
    }
}
