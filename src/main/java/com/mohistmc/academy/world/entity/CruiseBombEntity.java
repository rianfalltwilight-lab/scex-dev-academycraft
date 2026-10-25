package com.mohistmc.academy.world.entity;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.ability.LoadedRaycast;
import com.mohistmc.academy.skill.ability.telekinesis.CruiseBombEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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

/** One independently orbiting/accelerating water bomb owned by a paid cruise session. */
public final class CruiseBombEntity extends Projectile {
    private static final EntityDataAccessor<Float> PROFICIENCY = SynchedEntityData.defineId(CruiseBombEntity.class, EntityDataSerializers.FLOAT);
    private static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("academy", "cruise_bomb"));
    private Entity target;
    private double latitude, longitude;
    private float speed;
    private int lockedTicks;
    private boolean armed, impacting;

    public CruiseBombEntity(EntityType<? extends CruiseBombEntity> type, Level level) { super(type, level); setNoGravity(true); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(PROFICIENCY, 0F); }
    public float proficiency() { return entityData.get(PROFICIENCY); }
    public Entity target() { return target; }
    public void prepare(ServerPlayer owner, float p) {
        setOwner(owner); entityData.set(PROFICIENCY, Math.clamp(p,0,1));
        latitude=random.nextDouble()*Math.PI*2; longitude=random.nextDouble()*Math.PI*2;
        orbit(owner);
    }
    public void arm() { armed=true; }
    public void lock(Entity target) {
        if (this.target != null || !armed || isRemoved()) return;
        this.target=target;
        speed += acceleration();
        // Preserve the upstream initial outward kick before the subsequent homing updates.
        setDeltaMovement(position().subtract(target.position().add(0,target.getBbHeight()/2,0)).normalize().scale(speed));
        hasImpulse=true;
    }
    private float acceleration() { return .1F + .1F*proficiency(); }
    private void orbit(ServerPlayer owner) {
        setPos(owner.getX()+Math.sin(latitude),owner.getY()+1+Math.cos(latitude)*Math.cos(longitude),
                owner.getZ()+Math.cos(latitude)*Math.sin(longitude));
    }
    @Override protected boolean canHitEntity(Entity entity) {
        if (entity==getOwner() || entity instanceof CruiseBombEntity bomb && bomb.getOwner()==getOwner()) return false;
        return entity instanceof Projectile ? entity.isAlive() : super.canHitEntity(entity);
    }

    @Override public void tick() {
        if (isRemoved() || impacting) return;
        if (level().isClientSide) { super.tick(); return; }
        if (!armed) return;
        if (!(getOwner() instanceof ServerPlayer owner) || !owner.isAlive() || owner.level()!=level()
                || !CruiseBombEffect.owns(owner,this)) { discard(); return; }
        super.tick();
        if (target==null) {
            latitude=(latitude+Math.PI*2/80)%(Math.PI*2);
            orbit(owner);
            return;
        }
        if (!target.isAlive() || target.level()!=level()) { discard(); return; }
        Vec3 from=position(), movement=getDeltaMovement(), to=from.add(movement);
        var swept=getBoundingBox().expandTowards(movement).inflate(.01);
        if (!Double.isFinite(movement.lengthSqr()) || to.y<level().getMinBuildHeight() || to.y>=level().getMaxBuildHeight()
                || !level().getWorldBorder().isWithinBounds(BlockPos.containing(to))
                || !level().hasChunksAt(BlockPos.containing(swept.minX,swept.minY,swept.minZ),BlockPos.containing(swept.maxX,swept.maxY,swept.maxZ))) {
            discard(); return;
        }
        for(int impacts=0;impacts<16;impacts++) {
            HitResult hit=LoadedRaycast.clip(level(),new ClipContext(from,to,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,this));
            Vec3 end=hit.getType()==HitResult.Type.MISS?to:hit.getLocation();
            var entityHit=ProjectileUtil.getEntityHitResult(level(),this,from,end,swept,this::canHitEntity,0);
            if(entityHit!=null) {
                Vec3 contact=entityHit.getEntity().getBoundingBox().clip(from,end).orElse(from);
                if(hit.getType()==HitResult.Type.MISS||from.distanceToSqr(contact)<from.distanceToSqr(end))hit=new EntityHitResult(entityHit.getEntity(),contact);
            }
            if(hit.getType()==HitResult.Type.MISS)break;
            impacting=true;
            boolean canceled;
            try { canceled=EventHooks.onProjectileImpact(this,hit); } finally { impacting=false; }
            if(isRemoved()||!CruiseBombEffect.owns(owner,this)||owner.level()!=level()||!position().equals(from))return;
            if(canceled)break;
            if(hit instanceof BlockHitResult block && breakFragile(block,owner)) {
                if(impacts==15){discard();return;}
                continue;
            }
            if(isRemoved()||!CruiseBombEffect.owns(owner,this))return;
            impacting=true;
            try { setPos(hit.getLocation()); super.onHit(hit); } finally { discard();impacting=false; }
            return;
        }
        setPos(to);updateRotation();
        if(lockedTicks<=20) {
            speed+=acceleration();
            setDeltaMovement(target.position().subtract(position()).normalize().scale(speed));
        } else if(lockedTicks>=40) { discard(); }
        lockedTicks++;
    }

    private boolean breakFragile(BlockHitResult hit,ServerPlayer owner) {
        BlockPos pos=hit.getBlockPos();
        if(!level().isLoaded(pos)||!DynamicSkillRules.destroysBlocks(level(),"cruise_bomb"))return false;
        var state=level().getBlockState(pos);
        if(state.isAir()||state.getDestroySpeed(level(),pos)!=0||!owner.hasCorrectToolForDrops(state)
                ||!owner.getAbilities().mayBuild||!owner.mayInteract(level(),pos)||!level().mayInteract(owner,pos))return false;
        var event=new BlockEvent.BreakEvent(level(),pos,state,owner);
        impacting=true;
        try {
            NeoForge.EVENT_BUS.post(event);
            return !event.isCanceled()&&!isRemoved()&&CruiseBombEffect.owns(owner,this)&&owner.level()==level()
                    &&level().getBlockState(pos)==state&&level().destroyBlock(pos,true,owner);
        } finally { impacting=false; }
    }
    @Override protected void onHitEntity(EntityHitResult hit) {
        if(!(getOwner() instanceof ServerPlayer owner)||!CruiseBombEffect.owns(owner,this))return;
        if(hit.getEntity() instanceof Projectile projectile) {
            if(projectile.getOwner()!=owner)projectile.discard();
        } else {
            AcademyDamageHelper.hurt(owner,hit.getEntity(),com.mohistmc.academy.skill.SkillDamageSource.projectile(owner,this,DAMAGE),DynamicSkillRules.damage("cruise_bomb",
                    com.mohistmc.academy.skill.ability.aerohand.AeroBehaviorMath.cruiseBombDamage(proficiency())));
        }
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); discard(); }
}
