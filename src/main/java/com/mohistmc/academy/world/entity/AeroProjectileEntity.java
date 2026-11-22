package com.mohistmc.academy.world.entity;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.ability.LoadedRaycast;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.joml.Vector3f;

/** ExtraAcC's three finite-lived air projectiles, with server-owned swept collisions. */
public final class AeroProjectileEntity extends Projectile {
    public enum Kind { VOLCANIC_BALL, AIR_BLADE, BOMBER_LANCE }
    private static final EntityDataAccessor<Float> PROFICIENCY = SynchedEntityData.defineId(AeroProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(AeroProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> START = SynchedEntityData.defineId(AeroProjectileEntity.class, EntityDataSerializers.VECTOR3);
    private static final ResourceKey<DamageType> BLADE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("academy", "air_blade"));
    private final Kind kind;
    private Vec3 launchDirection = Vec3.ZERO;
    private Vec3 lateralOffset = Vec3.ZERO;
    private boolean armed;
    private boolean impacting;
    /** Decided once at a mob cast (config + mobGriefing); players keep their own per-block checks. */
    private boolean mobBreaksBlocks;

    public AeroProjectileEntity(EntityType<? extends AeroProjectileEntity> type, Level level, Kind kind) {
        super(type, level);
        this.kind = kind;
        setNoGravity(true);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PROFICIENCY, 0F);
        builder.define(AGE, 0);
        builder.define(START, new Vector3f());
    }

    public void prepare(ServerPlayer owner, float proficiency) {
        prepare(owner, proficiency, owner.getEyePosition(), owner.getLookAngle());
    }

    /** Any living caster; a player passes its own eye and look so the result is unchanged. */
    public void prepare(LivingEntity owner, float proficiency, Vec3 eye, Vec3 look) {
        setOwner(owner);
        setPos(eye);
        entityData.set(PROFICIENCY, Math.clamp(proficiency, 0, 1));
        entityData.set(START, position().toVector3f());
        launchDirection = look.normalize();
        float speed = switch (kind) {
            case VOLCANIC_BALL -> .3F + .3F * proficiency();
            case AIR_BLADE -> .8F;
            case BOMBER_LANCE -> 1F + .5F * proficiency();
        };
        setDeltaMovement(launchDirection.scale(speed));
    }

    public void arm() { armed = true; }
    public void setMobBreaksBlocks(boolean value) { mobBreaksBlocks = value; }
    public Kind kind() { return kind; }
    public float proficiency() { return entityData.get(PROFICIENCY); }
    public int legacyAge() { return entityData.get(AGE); }
    public Vec3 getStartPos() { return new Vec3(entityData.get(START)); }
    public String skill() { return switch (kind) {
        case VOLCANIC_BALL -> "volcanic_ball";
        case AIR_BLADE -> "air_blade";
        case BOMBER_LANCE -> "bomber_lance";
    }; }
    private int lifespan() { return switch (kind) {
        case VOLCANIC_BALL -> 80;
        case AIR_BLADE -> (int) (40 + 20 * proficiency());
        case BOMBER_LANCE -> 20;
    }; }

    @Override public EntityDimensions getDimensions(Pose pose) {
        if (kind == Kind.VOLCANIC_BALL) {
            float size = .2F + .01F * legacyAge();
            return EntityDimensions.scalable(size, size);
        }
        return super.getDimensions(pose);
    }

    @Override protected boolean canHitEntity(Entity entity) {
        return entity != getOwner() && super.canHitEntity(entity);
    }

    @Override public void tick() {
        if (isRemoved() || impacting || (!level().isClientSide && !armed)) return;
        if (!level().isClientSide && (!(getOwner() instanceof LivingEntity owner)
                || !owner.isAlive() || owner.level() != level())) { discard(); return; }
        super.tick();
        // 1.12 World and EntityFlying each increment age once per actual update.
        int previousAge = legacyAge();
        entityData.set(AGE, previousAge + 2);
        if (legacyAge() >= lifespan()) { if (!level().isClientSide) discard(); return; }
        Vec3 movement = getDeltaMovement();
        Vec3 from = position();
        if (kind == Kind.AIR_BLADE) {
            Vec3 horizontal = new Vec3(movement.x, 0, movement.z).normalize();
            Vec3 offset = new Vec3(horizontal.z, 0, -horizontal.x)
                    .scale(2 * Math.sin((previousAge + 1) * Math.PI / 10));
            movement = movement.add(offset.subtract(lateralOffset));
            lateralOffset = offset;
        }
        Vec3 to = from.add(movement);
        var swept = getBoundingBox().expandTowards(movement).inflate(.001);
        if (!Double.isFinite(movement.lengthSqr()) || to.y < level().getMinBuildHeight()
                || to.y >= level().getMaxBuildHeight()
                || !level().getWorldBorder().isWithinBounds(BlockPos.containing(to))
                || !com.mohistmc.academy.skill.ability.LoadedRaycast.isAreaLoaded(level(), swept)) {
            if (!level().isClientSide) discard();
            return;
        }
        if (!level().isClientSide) {
            for (int impacts = 0; impacts < 16; impacts++) {
                HitResult hit = LoadedRaycast.clip(level(), new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this));
                Vec3 end = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
                EntityHitResult entityHit = firstVolumeHit(from, end, swept);
                if (entityHit != null && (hit.getType() == HitResult.Type.MISS
                        || from.distanceToSqr(entityHit.getLocation()) < from.distanceToSqr(end))) hit = entityHit;
                if (hit.getType() == HitResult.Type.MISS) break;
                impacting = true;
                boolean canceled;
                try { canceled = EventHooks.onProjectileImpact(this, hit); }
                finally { impacting = false; }
                if (isRemoved() || !(getOwner() instanceof LivingEntity owner) || !owner.isAlive()
                        || owner.level() != level() || !position().equals(from)) return;
                if (canceled) break;
                if (hit instanceof BlockHitResult block && breakFragile(block, owner)) {
                    if (impacts == 15) { discard(); return; }
                    continue;
                }
                if (isRemoved() || !owner.isAlive() || owner.level() != level()) return;
                setPos(hit.getLocation());
                impacting = true;
                try { super.onHit(hit); }
                finally { discard(); impacting = false; }
                return;
            }
        }
        setPos(to);
        updateRotation();
        double drag = isInWater() ? .8F : switch (kind) {
            case VOLCANIC_BALL -> .98F;
            case AIR_BLADE -> .99F;
            case BOMBER_LANCE -> 1F;
        };
        setDeltaMovement(getDeltaMovement().scale(drag));
        if (kind == Kind.VOLCANIC_BALL) refreshDimensions();
    }

    private EntityHitResult firstVolumeHit(Vec3 from, Vec3 to, net.minecraft.world.phys.AABB swept) {
        EntityHitResult first = null;
        double closest = Double.POSITIVE_INFINITY;
        for (Entity candidate : level().getEntities(this, swept, this::canHitEntity)) {
            var box = candidate.getBoundingBox();
            var expanded = box.inflate(getBbWidth() / 2, 0, getBbWidth() / 2).expandTowards(0, -getBbHeight(), 0);
            Vec3 contact = expanded.contains(from) ? from : expanded.clip(from, to).orElse(null);
            if (contact == null || from.distanceToSqr(contact) >= closest) continue;
            // A growing ball/wide blade may touch off its center ray, but cannot reach a
            // target's real surface through a thin obstacle merely because boxes overlap.
            Vec3 surface = new Vec3(Math.clamp(contact.x, box.minX, box.maxX),
                    Math.clamp(contact.y, box.minY, box.maxY), Math.clamp(contact.z, box.minZ, box.maxZ));
            var occlusion = LoadedRaycast.clip(level(), new ClipContext(from, surface,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (occlusion.getType() != HitResult.Type.MISS) continue;
            closest = from.distanceToSqr(contact);
            first = new EntityHitResult(candidate, contact);
        }
        return first;
    }

    private boolean breakFragile(BlockHitResult hit, LivingEntity caster) {
        BlockPos pos = hit.getBlockPos();
        if (!level().isLoaded(pos) || !DynamicSkillRules.destroysBlocks(level(), skill())) return false;
        var state = level().getBlockState(pos);
        float hardness = state.getDestroySpeed(level(), pos);
        float limit = kind == Kind.BOMBER_LANCE ? .4F : .2F;
        if (!(caster instanceof ServerPlayer owner)) {
            // Mob caster: no player permissions or BreakEvent; the cast-time griefing decision rules.
            if (!mobBreaksBlocks || state.isAir() || hardness < 0 || hardness > limit) return false;
            impacting = true;
            try {
                return !isRemoved() && caster.isAlive() && caster.level() == level()
                        && level().destroyBlock(pos, true, caster);
            } finally { impacting = false; }
        }
        if (state.isAir() || hardness < 0 || hardness > limit || !owner.hasCorrectToolForDrops(state, level(), pos)
                || !owner.getAbilities().mayBuild || !owner.mayInteract(level(), pos) || !level().mayInteract(owner, pos)) return false;
        var event = new BlockEvent.BreakEvent(level(), pos, state, owner);
        impacting = true;
        try {
            NeoForge.EVENT_BUS.post(event);
            return !event.isCanceled() && !isRemoved() && owner.isAlive() && owner.level() == level()
                    && level().getBlockState(pos) == state && level().destroyBlock(pos, true, owner);
        } finally { impacting = false; }
    }

    @Override protected void onHitEntity(EntityHitResult hit) {
        if (!(getOwner() instanceof LivingEntity owner) || !owner.isAlive() || owner.level() != level()) return;
        float p = proficiency();
        float ratio = (float) legacyAge() / lifespan();
        float damage = switch (kind) {
            case VOLCANIC_BALL -> com.mohistmc.academy.skill.ability.aerohand.AeroBehaviorMath.volcanicDamage(10 + 10 * p, legacyAge(), lifespan());
            case AIR_BLADE -> (12 + 6 * p) * (1 - .25F * ratio);
            case BOMBER_LANCE -> 48 + 24 * p;
        };
        var type = kind == Kind.AIR_BLADE ? BLADE_DAMAGE : com.mohistmc.academy.skill.SkillDamageSource.THROWN;
        if (AcademyDamageHelper.hurt(owner, hit.getEntity(), com.mohistmc.academy.skill.SkillDamageSource.projectile(owner, this, type),
                DynamicSkillRules.damage(skill(), damage)) && kind != Kind.AIR_BLADE) {
            double impulse = (kind == Kind.BOMBER_LANCE ? 10 + 5 * p : 3 + 3 * p)
                    / Math.max(.01F, hit.getEntity().getBbHeight());
            hit.getEntity().push(launchDirection.x * impulse, launchDirection.y * impulse, launchDirection.z * impulse);
            hit.getEntity().setAirSupply(hit.getEntity().getMaxAirSupply());
            hit.getEntity().hurtMarked = true;
        }
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); discard(); }
}
