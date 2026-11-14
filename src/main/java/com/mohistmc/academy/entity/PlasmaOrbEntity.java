package com.mohistmc.academy.entity;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.ability.LoadedRaycast;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server-authoritative 1.0.7 plasma body: charging overhead, then moving toward an immutable destination. */
public final class PlasmaOrbEntity extends Entity implements ItemSupplier {
    private static final EntityDataAccessor<Float> DEST_X = SynchedEntityData.defineId(PlasmaOrbEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DEST_Y = SynchedEntityData.defineId(PlasmaOrbEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DEST_Z = SynchedEntityData.defineId(PlasmaOrbEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(PlasmaOrbEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(PlasmaOrbEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> ARMED = SynchedEntityData.defineId(PlasmaOrbEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID owner;
    private int flightTicks;
    /** Cast-time griefing decision of a mob owner; player owners keep their per-block permission checks. */
    private boolean mobBreaksBlocks;

    public PlasmaOrbEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** {@code player} may also be a mob caster; it is then resolved through the level, not the player list. */
    public void configureCharging(LivingEntity player, Vec3 chargePosition) {
        owner = player.getUUID();
        setPos(chargePosition);
        entityData.set(ARMED, false);
        flightTicks = 0;
    }

    public boolean arm(Vec3 destination, float damage, float radius) {
        if (level().isClientSide || !valid(destination, damage, radius) || !isAlive()) return false;
        BlockPos target = BlockPos.containing(destination);
        if (!level().getWorldBorder().isWithinBounds(target) || !LoadedRaycast.isLoaded(level(), target)) return false;
        entityData.set(DEST_X, (float) destination.x);
        entityData.set(DEST_Y, (float) destination.y);
        entityData.set(DEST_Z, (float) destination.z);
        entityData.set(DAMAGE, damage);
        entityData.set(RADIUS, radius);
        entityData.set(ARMED, true);
        flightTicks = 0;
        hasImpulse = true;
        return true;
    }

    /** Only read for a mob owner: whether its explosion may break blocks (its cast-time griefing rules). */
    public void setMobBreaksBlocks(boolean value) { mobBreaksBlocks = value; }

    public UUID ownerId() { return owner; }

    public boolean isArmed() {
        return entityData.get(ARMED);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DEST_X, 0f);
        builder.define(DEST_Y, 0f);
        builder.define(DEST_Z, 0f);
        builder.define(DAMAGE, 80f);
        builder.define(RADIUS, 12f);
        builder.define(ARMED, false);
    }

    @Override public ItemStack getItem() { return new ItemStack(Items.GLOWSTONE_DUST); }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) owner = tag.getUUID("Owner");
        Vec3 destination = new Vec3(tag.getFloat("DestX"), tag.getFloat("DestY"), tag.getFloat("DestZ"));
        float damage = tag.getFloat("Damage");
        float radius = tag.getFloat("Radius");
        if (!valid(destination, damage, radius)) {
            discard();
            return;
        }
        entityData.set(DEST_X, (float) destination.x);
        entityData.set(DEST_Y, (float) destination.y);
        entityData.set(DEST_Z, (float) destination.z);
        entityData.set(DAMAGE, damage);
        entityData.set(RADIUS, radius);
        entityData.set(ARMED, tag.getBoolean("Armed"));
        flightTicks = Math.clamp(tag.getInt("FlightTicks"), 0, 240);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putFloat("DestX", entityData.get(DEST_X));
        tag.putFloat("DestY", entityData.get(DEST_Y));
        tag.putFloat("DestZ", entityData.get(DEST_Z));
        tag.putFloat("Damage", entityData.get(DAMAGE));
        tag.putFloat("Radius", entityData.get(RADIUS));
        tag.putBoolean("Armed", entityData.get(ARMED));
        tag.putInt("FlightTicks", flightTicks);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        ServerLevel serverLevel = (ServerLevel) level();
        LivingEntity player = owner(serverLevel);
        if (player == null || !player.isAlive() || player.level() != serverLevel) {
            discard();
            return;
        }
        if (!isArmed()) return;

        Vec3 destination = destination();
        Vec3 old = position();
        Vec3 rawDelta = destination.subtract(old);
        Vec3 next = rawDelta.length() < 1 ? destination : old.add(rawDelta.normalize());
        if (!LoadedRaycast.isLoaded(serverLevel, BlockPos.containing(next))) {
            discard();
            return;
        }

        HitResult blockHit = LoadedRaycast.clip(serverLevel, new ClipContext(old, next,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean entityHit = serverLevel.getEntities(this, new AABB(old, next).inflate(.75),
                        entity -> entity != player && entity.isAlive() && entity.isPickable())
                .stream().anyMatch(entity -> entity.getBoundingBox().inflate(.3).clip(old, next).isPresent());
        setPos(next);
        flightTicks++;
        if (blockHit.getType() != HitResult.Type.MISS || entityHit
                || rawDelta.length() < 1.5 || flightTicks >= 240) {
            explodeAtDestination(serverLevel, player);
        }
    }

    /** The online player with the owner UUID, else a non-player living owner (mob caster) in this level. */
    private LivingEntity owner(ServerLevel level) {
        if (owner == null) return null;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player != null) return player;
        return level.getEntity(owner) instanceof LivingEntity living && !(living instanceof Player) ? living : null;
    }

    private void explodeAtDestination(ServerLevel level, LivingEntity player) {
        Vec3 destination = destination();
        BlockPos center = BlockPos.containing(destination);
        if (!LoadedRaycast.isLoaded(level, center)) {
            discard();
            return;
        }
        // PlasmaCannonContext.explode() always used destination, even when the flight ray hit early.
        setPos(destination);

        float damage = entityData.get(DAMAGE);
        for (Entity entity : level.getEntities(this, getBoundingBox().inflate(10),
                entity -> entity.isAlive() && entity.distanceToSqr(this) <= 100)) {
            AcademyDamageHelper.hurt(player, entity, com.mohistmc.academy.skill.SkillDamageSource.explosion(player), damage);
            entity.invulnerableTime = -1;
        }

        float radius = entityData.get(RADIUS);
        // Even KEEP/NONE explosions trace terrain and entity exposure. Merely disabling
        // block destruction is not a safe fallback at a cold chunk boundary.
        AABB blastArea = new AABB(destination, destination).inflate(radius * 2 + 2);
        boolean resident = LoadedRaycast.isAreaLoaded(level, blastArea);
        if (resident) for (Entity entity : level.getEntities(this, blastArea)) {
            if (!LoadedRaycast.isAreaLoaded(level, entity.getBoundingBox().inflate(1))) {
                resident = false;
                break;
            }
        }
        if (resident) {
            boolean mayBreak = player instanceof ServerPlayer
                    ? DynamicSkillRules.destroysBlocks(level, "plasma_cannon") : mobBreaksBlocks;
            level.explode(player, com.mohistmc.academy.skill.SkillDamageSource.explosion(player),
                    new ProtectedBlast(level, player), destination, radius, false,
                    mayBreak ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
        } else {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                    destination.x, destination.y, destination.z, 1, 0, 0, 0, 0);
            level.playSound(null, destination.x, destination.y, destination.z,
                    net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.BLOCKS, 4, .7f);
        }
        discard();
    }

    /** A skill explosion observes the same player/claim boundary as direct skill mining. */
    private static final class ProtectedBlast extends net.minecraft.world.level.ExplosionDamageCalculator {
        private final ServerLevel level;
        private final LivingEntity player;
        private final java.util.Map<BlockPos, Boolean> permissions = new java.util.HashMap<>();
        private ProtectedBlast(ServerLevel level, LivingEntity player) { this.level = level; this.player = player; }
        /** A mob owner's blast spares what its skills may not hit (itself, allies, its own kind). */
        @Override public boolean shouldDamageEntity(net.minecraft.world.level.Explosion explosion,
                net.minecraft.world.entity.Entity entity) {
            return super.shouldDamageEntity(explosion, entity)
                    && (player instanceof Player || AcademyDamageHelper.allowsTarget(player, entity));
        }
        @Override public float getKnockbackMultiplier(net.minecraft.world.entity.Entity entity) {
            return player instanceof Player || AcademyDamageHelper.allowsTarget(player, entity)
                    ? super.getKnockbackMultiplier(entity) : 0;
        }
        /** The vanilla explosion bypasses AcademyDamageHelper; apply the same config and attribute stages. */
        @Override public float getEntityDamageAmount(net.minecraft.world.level.Explosion explosion,
                net.minecraft.world.entity.Entity entity) {
            float vanilla = super.getEntityDamageAmount(explosion, entity);
            return com.mohistmc.academy.skill.SkillDamageMath.applyAttributes(
                    DynamicSkillRules.damage("plasma_cannon", vanilla), player, entity);
        }
        @Override public boolean shouldBlockExplode(net.minecraft.world.level.Explosion explosion,
                net.minecraft.world.level.BlockGetter getter, BlockPos pos,
                net.minecraft.world.level.block.state.BlockState state, float power) {
            if (state.isAir()) return true;
            // A mob owner reaches this only when its cast may grief; it has no per-block permission.
            if (!(player instanceof Player breaker)) return state.getDestroySpeed(level, pos) >= 0;
            return permissions.computeIfAbsent(pos.immutable(), key -> {
                if (!breaker.mayInteract(level, key) || state.getDestroySpeed(level, key) < 0) return false;
                var event = new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(level, key, state, breaker);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
                return !event.isCanceled() && level.getBlockState(key) == state;
            });
        }
    }

    private Vec3 destination() {
        return new Vec3(entityData.get(DEST_X), entityData.get(DEST_Y), entityData.get(DEST_Z));
    }

    private static boolean valid(Vec3 destination, float damage, float radius) {
        return Double.isFinite(destination.x) && Double.isFinite(destination.y) && Double.isFinite(destination.z)
                && Float.isFinite(damage) && Float.isFinite(radius)
                && Math.abs(destination.x) <= 29_999_984 && Math.abs(destination.z) <= 29_999_984
                && destination.y >= -2048 && destination.y <= 2048
                && damage >= 0 && damage <= 1000 && radius >= 1 && radius <= 32;
    }
}
