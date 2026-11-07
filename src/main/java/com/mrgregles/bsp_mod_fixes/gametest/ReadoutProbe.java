package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.client.QuantumReadout;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.aae.AaeGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Dev-only check of the quantum suit readout (F-25) on a real client: {@code ./gradlew runClient -PwithAae -PreadoutProbe
 * -PquickPlay=emitest}. Once the world is open it dresses the integrated server's player in the four quantum pieces at a
 * million AE each (survival), waits for the plate to slide in and screenshots it, hurts the player for 10 with a cactus on
 * the server thread (the packet comes back through the real channel) and screenshots the damage row, collapses the plate
 * to its tab and screenshots that, opens it again (so the client config is left as found) and closes the client. The
 * screenshots go to {@code run/screenshots/readout_*.png}; lines start "BSP-Mod Fixes readout probe". Stays out of the jar
 * with the rest of the gametest package; loaded by name only when the property is set.
 */
public final class ReadoutProbe {
    private static final ResourceLocation[] PIECES = {
            ResourceLocation.fromNamespaceAndPath(AaeGate.MOD_ID, "quantum_helmet"),
            ResourceLocation.fromNamespaceAndPath(AaeGate.MOD_ID, "quantum_chestplate"),
            ResourceLocation.fromNamespaceAndPath(AaeGate.MOD_ID, "quantum_leggings"),
            ResourceLocation.fromNamespaceAndPath(AaeGate.MOD_ID, "quantum_boots")};
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static int stage;
    private static int wait;

    private ReadoutProbe() {}

    public static void install() {
        MinecraftForge.EVENT_BUS.addListener(ReadoutProbe::tick);
        Fixes.LOGGER.info("BSP-Mod Fixes readout probe: installed, waiting for a world");
    }

    private static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null || mc.screen != null) {
            wait = 0;
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        switch (stage) {
            case 0 -> { // the world has been open a moment: dress the server's player
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                    player.setGameMode(GameType.SURVIVAL);
                    for (int i = 0; i < PIECES.length; i++) {
                        ItemStack piece = new ItemStack(ForgeRegistries.ITEMS.getValue(PIECES[i]));
                        piece.getOrCreateTag().putDouble("internalCurrentPower", 1_000_000);
                        player.setItemSlot(SLOTS[i], piece);
                    }
                    player.setHealth(20);
                    Fixes.LOGGER.info("BSP-Mod Fixes readout probe: the player wears the four quantum pieces at 1,000,000 AE each, survival");
                });
                wait = 100; // the client's copy of the armor, the plate's slide, and the spawn protection
                stage = 1;
            }
            case 1 -> {
                shot(mc, "readout_open.png", "the plate open at rest");
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                    boolean landed = player.hurt(player.damageSources().cactus(), 10f);
                    Fixes.LOGGER.info("BSP-Mod Fixes readout probe: cactus hit of 10 {}, health {}", landed ? "landed" : "did NOT land", player.getHealth());
                });
                wait = 12;
                stage = 2;
            }
            case 2 -> {
                shot(mc, "readout_hit.png", "the damage row after the hit");
                QuantumReadout.toggle();
                Fixes.LOGGER.info("BSP-Mod Fixes readout probe: collapsed by the toggle");
                wait = 25;
                stage = 3;
            }
            case 3 -> {
                shot(mc, "readout_collapsed.png", "the plate collapsed to its tab");
                QuantumReadout.toggle();
                Fixes.LOGGER.info("BSP-Mod Fixes readout probe: opened again, client config left as found");
                wait = 25;
                stage = 4;
            }
            default -> {
                shot(mc, "readout_reopened.png", "the plate open again, the damage row cleared");
                Fixes.LOGGER.info("BSP-Mod Fixes readout probe: done, closing the client");
                stage = -1;
                mc.stop();
            }
        }
    }

    private static void shot(Minecraft mc, String name, String what) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), c -> {});
        Fixes.LOGGER.info("BSP-Mod Fixes readout probe: screenshot {} ({})", name, what);
    }
}
