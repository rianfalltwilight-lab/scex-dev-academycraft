package com.mohistmc.academy.world.entity;

import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.skill.ability.LoadedRaycast;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

/** Persistent, single-owner material carrier used by Mag Manip. */
public final class MagManipBlockEntity extends Entity {
    private static final Map<UUID,MagManipBlockEntity> BY_OWNER=new ConcurrentHashMap<>();
    private static final EntityDataAccessor<Integer> STATE=SynchedEntityData.defineId(MagManipBlockEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> THROWN=SynchedEntityData.defineId(MagManipBlockEntity.class,EntityDataSerializers.BOOLEAN);
    private UUID owner,transaction; private BlockPos source=BlockPos.ZERO; private CompoundTag blockEntityPayload; private ResourceLocation blockEntityType; private ItemStack sourceItem=ItemStack.EMPTY; private float damage; private int age; private boolean settling;
    public MagManipBlockEntity(EntityType<?> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    protected void defineSynchedData(SynchedEntityData.Builder b){b.define(STATE,Block.getId(Blocks.IRON_BLOCK.defaultBlockState()));b.define(THROWN,false);}
    public BlockState blockState(){return Block.stateById(entityData.get(STATE));}
    public void initializeMaterial(ServerPlayer p,UUID tx,BlockState state,BlockPos source,CompoundTag payload,ResourceLocation type,float damage){this.owner=p.getUUID();this.transaction=tx;this.source=source.immutable();this.blockEntityPayload=payload==null?null:payload.copy();this.blockEntityType=type;this.damage=damage;entityData.set(STATE,Block.getId(state));claimLocal();}
    public void initializeMaterial(ServerPlayer p,UUID tx,BlockState state,BlockPos source,CompoundTag payload,ResourceLocation type,ItemStack item,float damage){initializeMaterial(p,tx,state,source,payload,type,damage);sourceItem=item==null?ItemStack.EMPTY:item.copyWithCount(1);}
    @Deprecated public void initializeProjection(ServerPlayer p,BlockState state,float damage){owner=p.getUUID();this.damage=damage;entityData.set(STATE,Block.getId(state));}
    @Deprecated public boolean claim(){return claimLocal();}
    @Deprecated public void releaseUnspawnedClaim(){if(owner!=null)BY_OWNER.remove(owner,this);}
    /** Compatibility entry point for old tests; it creates a BLOCK transaction. */
    public void initialize(ServerPlayer p,BlockState state,BlockPos source,CompoundTag payload,float damage){initialize(p,state,source,payload,null,damage);}
    public void initialize(ServerPlayer p,BlockState state,BlockPos source,CompoundTag payload,ResourceLocation type,float damage){this.owner=p.getUUID();this.transaction=UUID.randomUUID();this.source=source;this.blockEntityPayload=payload==null?null:payload.copy();this.blockEntityType=type;this.damage=damage;entityData.set(STATE,Block.getId(state));}
    public boolean reserveForSpawn(){if(owner==null||transaction==null||!(level() instanceof ServerLevel sl))return false;return MagManipTransactionData.get(sl).reserve(owner,transaction,getUUID(),sl.dimension().location().toString(),sl.getGameTime());}
    public boolean commitSpawnReservation(){if(!(level() instanceof ServerLevel sl))return false;MagManipTransactionData d=MagManipTransactionData.get(sl);String hash=MagManipTransactionData.sourceHash("BLOCK",blockState(),blockEntityPayload,"",0);return d.prepare(transaction,getUUID(),blockPosition(),source,blockState(),blockEntityPayload,blockEntityType,"BLOCK",-1,0,hash,sl.getGameTime())&&d.markSourceConsumed(transaction,getUUID(),hash,sl.getGameTime())&&d.markActive(transaction,getUUID(),sl.getGameTime());}
    public void releaseSpawnReservation(){if(level() instanceof ServerLevel sl&&transaction!=null)MagManipTransactionData.get(sl).release(transaction,getUUID());}
    public UUID transactionId(){return transaction;} public boolean recoveryOnly(){return false;}
    private boolean claimLocal(){if(owner==null)return false;MagManipBlockEntity old=BY_OWNER.putIfAbsent(owner,this);return old==null||old==this;}
    public static MagManipBlockEntity findOwned(ServerPlayer p){MagManipBlockEntity x=BY_OWNER.get(p.getUUID());if(x!=null&&x.isAlive()&&x.level()==p.level())return x;if(x!=null&&!x.isAlive())BY_OWNER.remove(p.getUUID(),x);return null;}
    /** Recreate the exact durable carrier after a crash, but only when its recorded chunk is loaded. */
    public static void resumeLoadedTransaction(ServerPlayer p){
        ServerLevel canonical=p.serverLevel().getServer().overworld();MagManipTransactionData ledger=MagManipTransactionData.get(canonical);MagManipTransactionData.Entry e=ledger.inspectOwner(p.getUUID());if(e==null||findOwned(p)!=null)return;
        if("PREPARED".equals(e.state())){ledger.abortPrepared(canonical,e.transaction(),e.entity());return;}
        ServerLevel level=dimension(canonical,e.dimension());if(level==null)return;int cx=net.minecraft.world.level.ChunkPos.getX(e.entityChunk()),cz=net.minecraft.world.level.ChunkPos.getZ(e.entityChunk());if(level.getChunkSource().getChunkNow(cx,cz)==null||level.getEntity(e.entity())!=null||!LoadedRaycast.isLoaded(level,BlockPos.of(e.source()).above()))return;
        if("SOURCE_CONSUMED".equals(e.state())&&!ledger.markActive(e.transaction(),e.entity(),level.getGameTime()))return;if(!"ACTIVE".equals(ledger.inspect(e.transaction()).state()))return;
        MagManipBlockEntity carrier=new MagManipBlockEntity(com.mohistmc.academy.world.AcademyEntities.MAG_MANIP_BLOCK.get(),level);carrier.setUUID(e.entity());BlockPos at=BlockPos.of(e.source()).above();carrier.setPos(at.getX()+.5,at.getY()+.5,at.getZ()+.5);boolean inventory="INVENTORY".equals(e.sourceKind())||"CREATIVE".equals(e.sourceKind());ItemStack item=inventory&&e.blockEntity()!=null?ItemStack.parse(level.registryAccess(),e.blockEntity()).orElse(ItemStack.EMPTY):ItemStack.EMPTY;carrier.initializeMaterial(p,e.transaction(),Block.stateById(e.blockState()),BlockPos.of(e.source()),inventory?null:e.blockEntity(),ResourceLocation.tryParse(e.blockEntityType()),item,e.damage());carrier.entityData.set(THROWN,e.thrown());carrier.setDeltaMovement(e.velocityX(),e.velocityY(),e.velocityZ());carrier.noPhysics=!e.thrown();carrier.setNoGravity(!e.thrown());
        if(!level.addFreshEntity(carrier)||carrier.isRemoved())carrier.discard();
    }
    public boolean belongsTo(ServerPlayer p){return owner!=null&&owner.equals(p.getUUID())&&p.level()==level();}
    public void hold(ServerPlayer p){if(!entityData.get(THROWN)){Vec3 target=p.getEyePosition().add(p.getLookAngle().scale(2)).add(0,-.1,0),delta=target.subtract(position());double distSq=delta.lengthSqr();Vec3 motion=distSq<1.0e-8?Vec3.ZERO:delta.normalize().scale(.2*(distSq<4?distSq/4:1));if(!loadedMovement(motion)){setDeltaMovement(Vec3.ZERO);return;}setDeltaMovement(motion);move(MoverType.SELF,motion);hurtMarked=true;}}
    private boolean loadedMovement(Vec3 motion){return Double.isFinite(motion.lengthSqr())&&LoadedRaycast.isAreaLoaded(level(),getBoundingBox().expandTowards(motion).inflate(1));}
    public void throwFrom(ServerPlayer p,double speed){throwFrom(p,p.getEyePosition().add(p.getLookAngle().scale(20)),speed);}
    public void throwFrom(ServerPlayer p,Vec3 target,double speed){if(!belongsTo(p))return;entityData.set(THROWN,true);noPhysics=false;setNoGravity(false);Vec3 delta=target.subtract(position());if(delta.lengthSqr()<1.0e-8)delta=p.getLookAngle();setDeltaMovement(delta.normalize().scale(speed));hurtMarked=true;if(level() instanceof ServerLevel sl&&transaction!=null)MagManipTransactionData.get(sl).updateRuntime(transaction,getUUID(),damage,true,getDeltaMovement().x,getDeltaMovement().y,getDeltaMovement().z,sl.getGameTime());}
    /** 1.0.7 abort/resource failure released the captured block to gravity instead of rewinding it. */
    public void dropFromHold(ServerPlayer p){if(!belongsTo(p)){recoverMaterial();return;}entityData.set(THROWN,true);noPhysics=false;setNoGravity(false);hurtMarked=true;if(level() instanceof ServerLevel sl&&transaction!=null)MagManipTransactionData.get(sl).updateRuntime(transaction,getUUID(),damage,true,getDeltaMovement().x,getDeltaMovement().y,getDeltaMovement().z,sl.getGameTime());}

    public void recoverMaterial() {
        if (level().isClientSide || transaction == null || settling || isRemoved()) return;
        settling = true;
        try {
            ServerLevel sl = (ServerLevel) level();
            MagManipTransactionData.Entry entry = MagManipTransactionData.get(sl).inspect(transaction);
            if (entry == null) { discard(); return; }
            ServerPlayer player = sl.getServer().getPlayerList().getPlayer(owner);
            if ("BLOCK".equals(entry.sourceKind())) {
                ServerLevel origin = dimension(sl, entry.dimension());
                if (origin == null || !LoadedRaycast.isLoaded(origin,BlockPos.of(entry.source())) || player == null
                        || !place(origin, BlockPos.of(entry.source()), player, true)) return;
            } else if ("INVENTORY".equals(entry.sourceKind())) {
                if (player == null) return;
                ItemStack item = sourceItem.isEmpty() && entry.blockEntity() != null
                        ? ItemStack.parse(sl.registryAccess(), entry.blockEntity()).orElse(ItemStack.EMPTY)
                        : sourceItem.copyWithCount(1);
                if (item.isEmpty()) item = new ItemStack(blockState().getBlock());
                player.getInventory().add(item);
                if (!item.isEmpty()) {
                    var drop = new net.minecraft.world.entity.item.ItemEntity(level(), getX(), getY(), getZ(), item);
                    drop.setDefaultPickUpDelay();
                    if (!level().addFreshEntity(drop)) return;
                }
                sourceItem = ItemStack.EMPTY;
            }
            settle(sl, entry);
            discard();
        } finally { settling = false; }
    }
    private void settle(ServerLevel caller,MagManipTransactionData.Entry entry){if("BLOCK".equals(entry.sourceKind())){ServerLevel origin=dimension(caller,entry.dimension());if(origin!=null){BlockPos pos=BlockPos.of(entry.source());if(LoadedRaycast.isLoaded(origin,pos)&&origin.getBlockState(pos).is(AcademyBlocks.MAG_MANIP_ESCROW.get()))origin.removeBlock(pos,false);}}MagManipTransactionData.get(caller).release(transaction,getUUID());}
    private static ServerLevel dimension(ServerLevel caller,String id){ResourceLocation key=ResourceLocation.tryParse(id);return key==null?null:caller.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,key));}
    private boolean mayPlace(ServerLevel target, BlockPos pos, ServerPlayer actor) {
        var entry = transaction == null ? null : MagManipTransactionData.get(target).inspect(transaction);
        return !isRemoved() && actor.isAlive() && actor.level() == target && LoadedRaycast.isLoaded(target,pos)
                && entry != null && entry.entity().equals(getUUID()) && "ACTIVE".equals(entry.state())
                && actor.getAbilities().mayBuild && actor.mayInteract(target, pos) && target.mayInteract(actor, pos);
    }

    private boolean place(ServerLevel target, BlockPos pos, ServerPlayer actor, boolean allowEscrow) {
        if (!mayPlace(target, pos, actor)) return false;
        BlockState existing = target.getBlockState(pos), material = blockState();
        if (!(existing.canBeReplaced() || (allowEscrow && existing.is(AcademyBlocks.MAG_MANIP_ESCROW.get())))
                || target.getBlockEntity(pos) != null) return false;
        BlockSnapshot snapshot = BlockSnapshot.create(target.dimension(), target, pos, 3);
        if (!target.setBlock(pos, material, 3)) return false;
        BlockEntity placed = target.getBlockEntity(pos);
        boolean canceled = EventHooks.onBlockPlace(actor, snapshot, Direction.UP);
        // A place callback may replace the destination or cancel/logout the owner.
        // Never settle our receipt or write its payload into that replacement.
        boolean stillOurs = LoadedRaycast.isLoaded(target,pos) && target.getBlockState(pos) == material
                && target.getBlockEntity(pos) == placed;
        if (canceled || !stillOurs || !mayPlace(target, pos, actor)) {
            if (stillOurs) snapshot.restore();
            return false;
        }
        if (blockEntityPayload != null) {
            if (placed == null || !MagManipTransferPolicy.sameType(placed, blockEntityType)) {
                snapshot.restore(); return false;
            }
            try { placed.loadWithComponents(blockEntityPayload.copy(), target.registryAccess()); placed.setChanged(); }
            catch (RuntimeException ex) { snapshot.restore(); return false; }
        }
        return true;
    }

    private boolean land(ServerLevel sl, ServerPlayer p) {
        if (settling || isRemoved()) return false;
        settling = true;
        try {
            BlockPos base = blockPosition();
            for (BlockPos pos : new BlockPos[]{base, base.above(), base.north(), base.south(), base.west(), base.east()}) {
                if (place(sl, pos, p, false)) {
                    MagManipTransactionData.Entry entry = MagManipTransactionData.get(sl).inspect(transaction);
                    settle(sl, entry);
                    discard();
                    return true;
                }
            }
            return false;
        } finally { settling = false; }
    }
    public void tick(){if(settling||isRemoved())return;if(!level().isClientSide&&!loadedMovement(Vec3.ZERO))return;super.tick();if(level().isClientSide)return;age++;ServerLevel sl=(ServerLevel)level();ServerPlayer p=owner==null?null:sl.getServer().getPlayerList().getPlayer(owner);if(transaction==null||owner==null){discard();return;}MagManipTransactionData data=MagManipTransactionData.get(sl);data.touch(transaction,getUUID(),sl.getGameTime(),"ACTIVE");if(entityData.get(THROWN))data.updateRuntime(transaction,getUUID(),damage,true,getDeltaMovement().x,getDeltaMovement().y,getDeltaMovement().z,sl.getGameTime());
        if(p==null||!p.isAlive()||p.level()!=level()){recoverMaterial();return;}
        if(!entityData.get(THROWN)){if(age>1200)recoverMaterial();else hold(p);return;}
        if(!loadedMovement(getDeltaMovement())){setDeltaMovement(Vec3.ZERO);recoverMaterial();return;}
        Vec3 before=position();move(MoverType.SELF,getDeltaMovement());setDeltaMovement(getDeltaMovement().scale(.99).add(0,-.04,0));AABB sweep=getBoundingBox().expandTowards(position().subtract(before)).inflate(.25);
        for(Entity target:level().getEntities(p,sweep,e->e.isAlive()&&e!=p&&e.isPickable())){com.mohistmc.academy.skill.AcademyDamageHelper.hurt(p,target,com.mohistmc.academy.skill.SkillDamageSource.attack(p),damage);if(!land(sl,p))recoverMaterial();return;}
        if(horizontalCollision||verticalCollision||onGround()||age>400){if(!land(sl,p))recoverMaterial();}
    }
    @Override public boolean shouldBeSaved(){return true;}
    protected void addAdditionalSaveData(CompoundTag t){if(owner!=null)t.putUUID("Owner",owner);if(transaction!=null)t.putUUID("Transaction",transaction);t.putLong("Source",source.asLong());t.putInt("BlockState",entityData.get(STATE));t.put("MaterialState",net.minecraft.nbt.NbtUtils.writeBlockState(blockState()));t.putBoolean("Thrown",entityData.get(THROWN));t.putFloat("Damage",damage);t.putInt("Age",age);if(blockEntityPayload!=null)t.put("BlockEntity",blockEntityPayload.copy());if(blockEntityType!=null)t.putString("BlockEntityType",blockEntityType.toString());if(!sourceItem.isEmpty())t.put("SourceItem",sourceItem.save(registryAccess()));}
    protected void readAdditionalSaveData(CompoundTag t){if(t.hasUUID("Owner"))owner=t.getUUID("Owner");if(t.hasUUID("Transaction"))transaction=t.getUUID("Transaction");source=BlockPos.of(t.getLong("Source"));entityData.set(STATE,MagManipTransactionData.readMaterialState(t));entityData.set(THROWN,t.getBoolean("Thrown"));damage=t.getFloat("Damage");age=t.getInt("Age");blockEntityPayload=t.contains("BlockEntity")?t.getCompound("BlockEntity"):null;blockEntityType=t.contains("BlockEntityType")?ResourceLocation.tryParse(t.getString("BlockEntityType")):null;sourceItem=t.contains("SourceItem")?ItemStack.parse(registryAccess(),t.getCompound("SourceItem")).orElse(ItemStack.EMPTY):ItemStack.EMPTY;}
    @Override public void onAddedToLevel(){super.onAddedToLevel();if(!level().isClientSide&&owner!=null&&transaction!=null){ServerLevel sl=(ServerLevel)level();boolean durable=MagManipTransactionData.get(sl).claim(owner,transaction,getUUID(),sl.dimension().location().toString(),1L,"CARRIER",sl.getGameTime());if(!durable||!claimLocal())discard();}}
    @Override public void onRemovedFromLevel(){if(owner!=null)BY_OWNER.remove(owner,this);super.onRemovedFromLevel();}
    @Override public void remove(RemovalReason reason){if(owner!=null)BY_OWNER.remove(owner,this);super.remove(reason);}
}
