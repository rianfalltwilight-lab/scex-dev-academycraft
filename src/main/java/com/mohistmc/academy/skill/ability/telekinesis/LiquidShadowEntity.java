package com.mohistmc.academy.skill.ability.telekinesis;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.world.AcademyEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.jetbrains.annotations.Nullable;

/** ExtraAcC Waterman: a transient owned mob using fixed vanilla navigation/goal sets. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class LiquidShadowEntity extends TamableAnimal {
    public LiquidShadowEntity(EntityType<? extends LiquidShadowEntity> type, Level level) {
        super(type, level);
        setTame(true, false);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 1.0)
                .add(Attributes.MAX_HEALTH, 100.0).add(Attributes.ATTACK_DAMAGE, 20.0);
    }

    @SubscribeEvent public static void attributes(EntityAttributeCreationEvent event) {
        event.put(AcademyEntities.LIQUID_SHADOW.get(), createAttributes().build());
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.0, 10.0F, 2.0F));
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        // Legacy owner retaliation used checkSight=false. The modern owner goals
        // pass DEFAULT targeting conditions, which reintroduce a sight check even
        // though TargetGoal.mustSee is false. Preserve acquisition behind cover;
        // vanilla navigation and the actual melee clear-path check still apply.
        targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this) {
            @Override protected boolean canAttack(@Nullable LivingEntity target, TargetingConditions conditions) {
                return super.canAttack(target, conditions.copy().ignoreLineOfSight());
            }
        });
        targetSelector.addGoal(2, new OwnerHurtTargetGoal(this) {
            @Override protected boolean canAttack(@Nullable LivingEntity target, TargetingConditions conditions) {
                return super.canAttack(target, conditions.copy().ignoreLineOfSight());
            }
        });
        targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers());
    }

    UUID abilityOwner() { return getOwnerUUID(); }

    @Override @Nullable public LivingEntity getOwner() {
        if (!level().isClientSide()) return TelekinesisPassiveHandler.shadowOwner(this);
        UUID owner = getOwnerUUID();
        return owner == null ? null : level().getPlayerByUUID(owner);
    }

    @Override public boolean canAttack(LivingEntity target) {
        return super.canAttack(target) && TelekinesisPassiveHandler.validShadowVictim(this, target);
    }

    @Override public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        return TelekinesisPassiveHandler.validShadowVictim(this, target);
    }

    @Override public boolean doHurtTarget(Entity target) {
        return target instanceof LivingEntity living && canAttack(living)
                && isWithinMeleeAttackRange(living)
                && com.mohistmc.academy.skill.ability.SkillRaycast.hasClearPath(this, living)
                && TelekinesisPassiveHandler.attackWithShadow(this, living);
    }

    @Override public void tick() {
        if (!level().isClientSide() && (!TelekinesisPassiveHandler.isCurrentShadow(this) || isInWater())) {
            TelekinesisPassiveHandler.endShadow(this);
            return;
        }
        super.tick();
        if (!level().isClientSide()) {
            if (isInWater()) TelekinesisPassiveHandler.endShadow(this);
            else if (getTarget() != null && !canAttack(getTarget())) setTarget(null);
        }
    }

    // The original follower teleports only when following its owner, never to an attack target.
    // Reject pending chunks before vanilla checks navigation/collision at the destination.
    @Override public void tryToTeleportToOwner() {
        LivingEntity owner = getOwner();
        if (owner != null && level() instanceof ServerLevel server
                && com.mohistmc.academy.world.block.LoadedMachineChunks.containsArea(server,
                        owner.blockPosition().offset(-3, 0, -3), owner.blockPosition().offset(3, 0, 3))) {
            super.tryToTeleportToOwner();
        }
    }

    @Override public boolean isFood(ItemStack stack) { return false; }
    @Override @Nullable public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) { return null; }
    @Override public boolean canBeLeashed() { return false; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.WATER_AMBIENT, .15F, 1F); }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.BOAT_PADDLE_WATER; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.WATER_AMBIENT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.WATER_AMBIENT; }
    @Override protected float getSoundVolume() { return .4F; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean save(CompoundTag tag) { return false; }
    @Override public boolean saveAsPassenger(CompoundTag tag) { return false; }
}
