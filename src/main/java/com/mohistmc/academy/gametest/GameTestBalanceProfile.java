package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.config.BalanceProfile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;

/**
 * The GameTest server runs on the LEGACY profile: most tests pin 1.0.7 numbers.  Tests of the modern
 * profile switch to MODERN themselves and restore LEGACY afterwards.
 */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class GameTestBalanceProfile {
    private GameTestBalanceProfile() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!GameTestHooks.isGametestServer()) return;
        ACConfig.Server.BALANCE_PROFILE.set(BalanceProfile.LEGACY);
        // Saved, so a config file correction (new keys) that reloads the spec keeps LEGACY.
        ACConfig.Server.save();
    }

    /** A reload from disk (for example after a spec change rewrote the file) must not bring MODERN back. */
    @EventBusSubscriber(modid = AcademyCraft.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        private Reload() {}

        @SubscribeEvent
        public static void reloading(net.neoforged.fml.event.config.ModConfigEvent.Reloading event) {
            if (event.getConfig().getSpec() == ACConfig.Server.SPEC && GameTestHooks.isGametestServer()
                    && ACConfig.Server.BALANCE_PROFILE.get() != BalanceProfile.LEGACY) {
                ACConfig.Server.BALANCE_PROFILE.set(BalanceProfile.LEGACY);
            }
        }
    }
}
