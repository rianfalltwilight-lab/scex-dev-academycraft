package com.mohistmc.academy.skill.ability.aerohand;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.api.mob.MobCastContext;
import com.mohistmc.academy.api.mob.MobCastable;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.DynamicOneShotSkillEffect;
import com.mohistmc.academy.world.effect.EffectHelper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** Six-block placed storm core: 80-tick attraction field followed by the legacy burst. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class StormCoreEffect implements DynamicOneShotSkillEffect, MobCastable {
    private static final List<Core> CORES = new ArrayList<>();
    private static final class Core {
        final ServerLevel level;
        final UUID owner;
        /** Players resolve through the player list as before; mobs through their level. */
        final boolean playerOwner;
        final Vec3 position;
        final float proficiency;
        int age;
        Core(ServerLevel level, LivingEntity owner, Vec3 position, float proficiency) {
            this.level = level; this.owner = owner.getUUID(); playerOwner = owner instanceof ServerPlayer;
            this.position = position; this.proficiency = proficiency;
        }
        LivingEntity resolveOwner() {
            if (playerOwner) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
                return player == null || player.serverLevel() != level ? null : player;
            }
            return level.getEntity(owner) instanceof LivingEntity living ? living : null;
        }
    }

    @Override public String getId() { return "storm_core"; }
    @Override public float rawCp(float p) { return lerpf(3000, 2000, p); }
    @Override public float rawOverload(float p) { return lerpf(300, 200, p); }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        float p = data.getProficiency(getId());
        place(player, player.serverLevel(), p, player.getEyePosition(), player.getLookAngle());
        if (!data.isDevMode()) DynamicSkillRules.addExp(player, data, getId(), lerpf(0.01F, 0.005F, p));
    }

    /** Mob path: same core, no payment or experience; damage stays config-scaled in the tick. */
    @Override
    public boolean mobCast(MobCastContext context) {
        place(context.caster(), context.level(), context.proficiency(), context.eye(), context.look());
        return true;
    }

    @Override public int mobCooldownTicks(float p) { return getCooldownTicks(p); }

    /** Places the core six blocks along {@code look} (or on the first block) after payment. */
    private static void place(LivingEntity caster, ServerLevel level, float p, Vec3 from, Vec3 look) {
        Vec3 intended = from.add(look.normalize().scale(6));
        var hit = com.mohistmc.academy.skill.ability.LoadedRaycast.clip(level, new ClipContext(from, intended, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster));
        Vec3 position = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : intended;
        CORES.add(new Core(level, caster, position, p));
        level.playSound(null, position.x, position.y, position.z,
                SoundEvents.LIGHTNING_BOLT_THUNDER, caster.getSoundSource(), 1.0F, 1.0F);
    }

    /** Whether a storm core owned by this entity is still running (tests, addons). */
    public static boolean hasActiveCore(UUID owner) {
        for (Core core : CORES) if (core.owner.equals(owner)) return true;
        return false;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        for (Core core : List.copyOf(CORES)) {
            if (!CORES.contains(core)) continue;
            LivingEntity owner = core.resolveOwner();
            if (owner == null || !owner.isAlive() || ++core.age >= 100) {
                CORES.remove(core);
                continue;
            }
            EffectHelper.windBurst(core.level, core.position.x, core.position.y, core.position.z,
                    core.age < 80 ? 5 : 18, core.age < 80 ? 0.45 : 1.25);
            if (core.age > 80) continue;
            AABB area = new AABB(core.position, core.position).inflate(8);
            for (Entity entity : core.level.getEntities(owner, area, Entity::isAlive)) {
                if (!owner.isAlive() || owner.level()!=core.level || !CORES.contains(core)) break;
                if (entity instanceof Projectile projectile) {
                    if (AeroProjectileRules.clearsLikeLegacy(projectile)) projectile.discard();
                    continue;
                }
                if (!(entity instanceof LivingEntity living) || living == owner || owner.isAlliedTo(living)) continue;
                float distance = (float) Math.sqrt(living.distanceToSqr(core.position));
                float power = Math.max(1 - distance / 10F, 0.2F);
                if (core.age < 80) {
                    AcademyDamageHelper.hurt(owner, living, com.mohistmc.academy.skill.SkillDamageSource.attack(owner),
                            DynamicSkillRules.damage("storm_core", power));
                    Vec3 pull = core.position.subtract(living.position());
                    if (pull.lengthSqr() > 1.0e-6) {
                        pull = pull.normalize().scale(0.5F * power);
                        living.push(pull.x, pull.y, pull.z);
                        living.hurtMarked = true;
                    }
                } else {
                    AcademyDamageHelper.hurt(owner, living, com.mohistmc.academy.skill.SkillDamageSource.attack(owner),
                            DynamicSkillRules.damage("storm_core", lerpf(80, 120, core.proficiency) * power));
                }
            }
        }
    }

    /** A non-player owner that dies, despawns or unloads takes its cores with it at once. */
    @SubscribeEvent
    public static void ownerLeft(EntityLeaveLevelEvent event) {
        if (CORES.isEmpty() || event.getLevel().isClientSide() || event.getEntity() instanceof ServerPlayer) return;
        UUID left = event.getEntity().getUUID();
        CORES.removeIf(core -> !core.playerOwner && core.owner.equals(left));
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { CORES.clear(); }
    @Override public int getCooldownTicks(float p) { return (int) lerpf(450, 300, Math.clamp(p, 0, 1)); }
}
