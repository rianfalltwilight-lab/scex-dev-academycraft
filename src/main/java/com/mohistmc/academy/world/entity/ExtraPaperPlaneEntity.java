package com.mohistmc.academy.world.entity;

import com.mohistmc.academy.world.AcademyItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Recoverable paper glider; Perfect Paper turns impact into a terrain-safe blast. */
public final class ExtraPaperPlaneEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Boolean> REINFORCED =
            SynchedEntityData.defineId(ExtraPaperPlaneEntity.class, EntityDataSerializers.BOOLEAN);
    private boolean armed;
    private boolean recovering;
    private boolean settling;
    private int retry;

    public ExtraPaperPlaneEntity(EntityType<? extends ExtraPaperPlaneEntity> type, Level level) {
        super(type, level);
    }

    public void launch(LivingEntity owner, boolean reinforced) {
        setOwner(owner);
        setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
        entityData.set(REINFORCED, reinforced);
        Vec3 look = owner.getLookAngle().normalize();
        setDeltaMovement(look.scale(reinforced ? 0.6 : 0.3));
        hasImpulse = true;
    }

    public boolean isReinforced() { return entityData.get(REINFORCED); }
    public void arm() { armed = true; }

    @Override protected Item getDefaultItem() { return AcademyItems.PAPER_PLANE.get(); }
    @Override protected double getDefaultGravity() { return 0.01; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(REINFORCED, false);
    }

    @Override
    public void tick() {
        if (settling || isRemoved() || (!level().isClientSide && !armed)) return;
        if (recovering) {
            if (!level().isClientSide && --retry <= 0) impact(position());
            return;
        }
        Vec3 beforeMotion = getDeltaMovement();
        var swept = getBoundingBox().expandTowards(beforeMotion).inflate(.3);
        if (!level().isClientSide && (!Double.isFinite(beforeMotion.lengthSqr())
                || !level().hasChunksAt(net.minecraft.core.BlockPos.containing(swept.minX, swept.minY, swept.minZ),
                        net.minecraft.core.BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ)))) {
            impact(position()); return;
        }
        super.tick();
        if (isRemoved() || recovering) return;
        // ThrowableProjectile already applies .99 drag; replace that result, do not multiply twice.
        setDeltaMovement(beforeMotion.scale(isInWater() ? .8F : isReinforced() ? .99F : .98F).add(0, -.01F, 0));
        if (getDeltaMovement().y <= 0) {
            Vec3 horizontal = new Vec3(getDeltaMovement().x, 0, getDeltaMovement().z);
            if (horizontal.lengthSqr() > 1.0e-6) {
                Vec3 next = getDeltaMovement().add(horizontal.normalize().scale(0.01));
                setDeltaMovement(next);
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        if (level().isClientSide) { super.onHit(result); return; }
        // Continue the same sweep after harvesting a fragile block, so a second
        // protected wall in this tick still stops the glider.
        for (int pass = 0; pass < 16; pass++) {
            if (!(result instanceof net.minecraft.world.phys.BlockHitResult block) || !breakFragile(block)) break;
            if (isRemoved()) return;
            result = net.minecraft.world.entity.projectile.ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (result.getType() == HitResult.Type.MISS) return;
            settling = true;
            boolean canceled;
            try { canceled = net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this, result); }
            finally { settling = false; }
            if (isRemoved() || canceled) return;
        }
        if (isRemoved()) return;
        super.onHit(result);
        impact(result.getLocation());
    }

    private boolean breakFragile(net.minecraft.world.phys.BlockHitResult hit) {
        if (!(getOwner() instanceof net.minecraft.server.level.ServerPlayer owner) || !owner.isAlive()
                || owner.level() != level() || !level().isLoaded(hit.getBlockPos())) return false;
        var pos = hit.getBlockPos();
        var state = level().getBlockState(pos);
        float hardness = state.getDestroySpeed(level(), pos);
        if (state.isAir() || hardness < 0 || hardness > .2F || !owner.hasCorrectToolForDrops(state)
                || !owner.getAbilities().mayBuild || !owner.mayInteract(level(), pos) || !level().mayInteract(owner, pos)) return false;
        var event = new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(level(), pos, state, owner);
        settling = true;
        try {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
            return !event.isCanceled() && !isRemoved() && owner.isAlive() && owner.level() == level()
                    && level().getBlockState(pos) == state && level().destroyBlock(pos, true, owner);
        } finally { settling = false; }
    }

    private void impact(Vec3 at) {
        if (isRemoved() || settling) return;
        settling = true;
        recovering = true;
        retry = 20;
        setDeltaMovement(Vec3.ZERO);
        try {
            if (isReinforced()) {
                discard();
                level().explode(getOwner(), at.x, at.y, at.z, 2.0F, ExplosionInteraction.NONE);
            } else if (level().addFreshEntity(new ItemEntity(level(), getX(), getY(), getZ(),
                    new ItemStack(AcademyItems.PAPER_PLANE.get())))) discard();
        } finally { settling = false; }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Reinforced", isReinforced());
        tag.putBoolean("Recovering", recovering);
        tag.putBoolean("Armed", armed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(REINFORCED, tag.getBoolean("Reinforced"));
        recovering = tag.getBoolean("Recovering");
        armed = !tag.contains("Armed") || tag.getBoolean("Armed");
    }
}
