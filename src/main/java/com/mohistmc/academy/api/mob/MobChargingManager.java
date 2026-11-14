package com.mohistmc.academy.api.mob;

import com.mohistmc.academy.AcademyCraft;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Server-side charge timers for mob-cast skills.  Unlike player charging there is no client handshake
 * or HUD; a charge simply counts ticks, re-aims at the target, and releases or aborts.
 */
@EventBusSubscriber(modid = AcademyCraft.MODID)
final class MobChargingManager {
    private static final class Session {
        final MobCastContext start;
        final MobCastable castable;
        final int charge;
        int ticks;
        Session(MobCastContext start, MobCastable castable, int charge) {
            this.start = start; this.castable = castable; this.charge = charge;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private MobChargingManager() {}

    static void start(MobCastContext context, MobCastable castable, int charge) {
        SESSIONS.put(context.caster().getUUID(), new Session(context, castable, charge));
    }

    static boolean isCharging(LivingEntity caster) {
        return SESSIONS.containsKey(caster.getUUID());
    }

    static boolean abort(LivingEntity caster) {
        Session session = SESSIONS.remove(caster.getUUID());
        if (session == null) return false;
        session.castable.mobChargeAborted(session.start.at(session.ticks));
        return true;
    }

    @SubscribeEvent
    static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || SESSIONS.isEmpty()) return;
        for (Map.Entry<UUID, Session> entry : new ArrayList<>(SESSIONS.entrySet())) {
            Session session = entry.getValue();
            if (session.start.level() != level) continue;
            LivingEntity caster = session.start.caster();
            if (!caster.isAlive() || caster.isRemoved() || caster.level() != level) {
                if (SESSIONS.remove(entry.getKey(), session)) session.castable.mobChargeAborted(session.start.at(session.ticks));
                continue;
            }
            session.ticks++;
            MobCastContext now = session.start.at(session.ticks);
            if (!session.castable.mobChargeTick(now, session.ticks)) {
                if (SESSIONS.remove(entry.getKey(), session)) session.castable.mobChargeAborted(now);
                continue;
            }
            if (session.ticks < session.charge) continue;
            if (!SESSIONS.remove(entry.getKey(), session)) continue;
            if (session.castable.mobCast(now))
                AcademyMobAbilities.applyCooldown(caster, now.skillId(), session.castable, now.proficiency(),
                        level.getGameTime());
        }
    }

    @SubscribeEvent
    static void leave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity living
                && SESSIONS.containsKey(living.getUUID())) abort(living);
    }

    @SubscribeEvent
    static void stopped(ServerStoppedEvent event) {
        SESSIONS.clear();
        com.mohistmc.academy.skill.ability.vecmanip.PlasmaCannonEffect.clearMobSessions();
        com.mohistmc.academy.skill.ability.teleporter.FleshRippingEffect.clearMobSessions();
    }
}
