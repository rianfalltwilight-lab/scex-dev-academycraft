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
        if (GameTestHooks.isGametestServer()) ACConfig.Server.BALANCE_PROFILE.set(BalanceProfile.LEGACY);
    }
}
