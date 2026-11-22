package com.mohistmc.academy.skill.passive;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.world.AcademyParticles;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Server-authoritative port of the 1.0.7 {@code MDDamageHelper} radiation mark.
 *
 * <p>The passive does not amplify the Meltdowner hit which creates it. The old
 * helper first called {@code ctx.attack}, then marked the target; all later
 * living-damage events were multiplied for at least sixty ticks. The original
 * sync call accidentally selected the caster for the particle cue, so that
 * observable quirk is retained independently from the authoritative target.
 */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class RadiationIntensifyRuntime {
    private static final String MARK_TICKS = AcademyCraft.MODID + ":md_marktick";
    private static final String MARK_RATE = AcademyCraft.MODID + ":md_markrate";
    private static final String VISUAL_TICKS = AcademyCraft.MODID + ":md_mark_visual";
    /** Tracks only entities with an active mark or visual timer so untouched entities never touch persistentData. */
    private static final Set<UUID> TRACKED = ConcurrentHashMap.newKeySet();

    private RadiationIntensifyRuntime() {}

    public static void mark(ServerPlayer caster, PlayerAbilityData data, Entity target) {
        if (!data.hasLearnedSkill("rad_intensify") || target == null) return;

        CompoundTag casterData = caster.getPersistentData();
        int ticks = Math.max(60, casterData.getInt(MARK_TICKS));
        CompoundTag targetData = target.getPersistentData();
        targetData.putInt(MARK_TICKS, ticks);

        float proficiency = PassiveSkillMath.radiationProficiency(
                data.getMaxCp(), PlayerAbilityData.LEVEL_5_INITIAL_CP);
        targetData.putFloat(MARK_RATE, PassiveSkillMath.radiationMultiplier(proficiency));
        TRACKED.add(target.getUUID());

        // MDDamageHelper.sync sent the caster rather than target in 1.0.7.
        casterData.putInt(VISUAL_TICKS, ticks);
        TRACKED.add(caster.getUUID());
    }

    @SubscribeEvent
    public static void entityTick(EntityTickEvent.Post event) {
        if (TRACKED.isEmpty()) return;
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || !TRACKED.contains(entity.getUUID())) return;
        CompoundTag data = entity.getPersistentData();

        int markTicks = data.getInt(MARK_TICKS);
        if (markTicks > 0) {
            markTicks--;
            data.putInt(MARK_TICKS, markTicks);
        }

        int visualTicks = data.getInt(VISUAL_TICKS);
        boolean emitParticles = visualTicks > 0;
        if (emitParticles) {
            visualTicks--;
            data.putInt(VISUAL_TICKS, visualTicks);
        }
        if (markTicks <= 0 && visualTicks <= 0) {
            TRACKED.remove(entity.getUUID());
        }
        if (!emitParticles) return;

        int count = level.random.nextInt(3);
        for (int i = 0; i < count; i++) {
            double radius = (.6 + level.random.nextDouble() * .1) * entity.getBbWidth();
            double theta = level.random.nextDouble() * Math.PI * 2;
            double x = entity.getX() + radius * Math.sin(theta);
            double y = entity.getY() + level.random.nextDouble() * entity.getBbHeight();
            double z = entity.getZ() + radius * Math.cos(theta);
            double vx = level.random.nextGaussian();
            double vy = level.random.nextGaussian();
            double vz = level.random.nextGaussian();
            double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (length < 1.0e-8) {
                vx = 0;
                vy = 1;
                vz = 0;
                length = 1;
            }
            level.sendParticles(AcademyParticles.MELTDOWN.get(), x, y, z, 0,
                    vx / length, vy / length, vz / length, .02);
        }
    }

    @SubscribeEvent
    public static void incomingDamage(LivingIncomingDamageEvent event) {
        if (TRACKED.isEmpty() || !TRACKED.contains(event.getEntity().getUUID())) return;
        CompoundTag data = event.getEntity().getPersistentData();
        if (data.getInt(MARK_TICKS) <= 0) return;
        float rate = data.getFloat(MARK_RATE);
        if (Float.isFinite(rate) && rate > 0) event.setAmount(event.getAmount() * rate);
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) TRACKED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TRACKED.clear();
    }

    public static int markedTicks(Entity entity) {
        return Math.max(0, entity.getPersistentData().getInt(MARK_TICKS));
    }

    public static float markedRate(Entity entity) {
        return entity.getPersistentData().getFloat(MARK_RATE);
    }
}
