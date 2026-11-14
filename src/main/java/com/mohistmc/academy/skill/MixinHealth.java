package com.mohistmc.academy.skill;

import com.mohistmc.academy.AcademyCraft;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The damage-boundary mixins no longer abort startup when another mod rewrites {@code LivingEntity.hurt}
 * (all injectors are {@code require = 0}).  Instead this probe notices at the first player hit when the
 * {@code @WrapMethod} scope is missing and logs once that defensive abilities (Vector Reflection /
 * Deviation, Insulation, Offense Armour absorption, Light Shield) are degraded.
 */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class MixinHealth {
    private static final Logger LOGGER = LoggerFactory.getLogger("AcademyCraft/MixinHealth");
    private static volatile boolean reported;

    private MixinHealth() {}

    /** True while the hurt wrapper is known to be missing. */
    public static boolean damageBoundaryMissing() {
        return reported;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void probe(LivingIncomingDamageEvent event) {
        if (reported || !(event.getEntity() instanceof ServerPlayer player)) return;
        AbilityDamageFrames.Frame frame = AbilityDamageFrames.peek();
        if (frame != null && frame.player == player) return;
        reported = true;
        LOGGER.error("LivingEntity.hurt is not wrapped by AcademyCraft's damage-boundary mixin (another mod "
                + "probably rewrites it). The game keeps running, but defensive ability settlement "
                + "(Vector Reflection/Deviation, Insulation, Offense Armour, Light Shield) is degraded.");
    }
}
