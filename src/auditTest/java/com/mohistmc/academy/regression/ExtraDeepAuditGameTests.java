package com.mohistmc.academy.regression;

import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.aerohand.*;
import com.mohistmc.academy.skill.ability.telekinesis.TelekinesisPassiveHandler;
import com.mohistmc.academy.skill.ability.telekinesis.CruiseBombEffect;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.entity.*;
import com.mohistmc.academy.world.item.ExtraItemData;
import com.mohistmc.academy.world.item.LaserGunItem;
import com.mohistmc.academy.world.item.ExtraPaperReturns;
import com.mohistmc.academy.world.item.ExtraEquipmentHandler;
import com.mohistmc.academy.skill.ability.telekinesis.PaperDrillEffect;
import com.mohistmc.academy.skill.ability.telekinesis.PsychoTransmissionEffect;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual entry points and world ticks; every denial case has a successful counterpart. */
@GameTestHolder("academy_audit")
@PrefixGameTestTemplate(false)
public final class ExtraDeepAuditGameTests {
    private static ServerPlayer player(GameTestHelper h, AbilityCategory category, String... skills) {
        var p = FollowupGameTests.player(h, category);
        p.setNoGravity(true);
        for (String skill : skills) data(p).learnSkill(skill);
        return p;
    }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getData(AcademyAttachments.PLAYER_ABILITY); }
    private static void close(ServerPlayer p) { p.server.getPlayerList().remove(p); }
    private static void near(GameTestHelper h, double actual, double expected, String label) {
        h.assertTrue(Math.abs(actual - expected) < .001, label + ": " + actual + " != " + expected);
    }
    private static AeroProjectileEntity launch(GameTestHelper h, ServerPlayer p) {
        h.assertTrue(new VolcanicBallEffect().executeAndReport(p, data(p)), "Volcanic launch failed");
        var shots = h.getLevel().getEntitiesOfClass(AeroProjectileEntity.class, p.getBoundingBox().inflate(3), e -> e.getOwner() == p);
        h.assertTrue(shots.size() == 1, "Launch did not create exactly one projectile");
        return shots.getFirst();
    }

    @GameTest(template="empty") public static void coinAdmissionFailureKeepsItemAndAllowsRetry(GameTestHelper h) {
        var p = player(h, AbilityCategory.ELECTROMASTER);
        var stack = new ItemStack(AcademyItems.COIN.get(), 2);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Consumer<EntityJoinLevelEvent> veto = e -> { if (e.getEntity() instanceof CoinEntity coin && coin.getThrower() == p) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, veto);
        try {
            h.assertTrue(stack.use(h.getLevel(), p, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL, "Canceled coin launch reported success");
            h.assertTrue(stack.getCount() == 2 && !CoinEntity.hasPlayerCoinInAir(p), "Canceled coin consumed item or retained reservation");
        } finally { NeoForge.EVENT_BUS.unregister(veto); }
        try {
            h.assertTrue(stack.use(h.getLevel(), p, InteractionHand.MAIN_HAND).getResult().consumesAction(), "Retry failed");
            h.assertTrue(stack.getCount() == 1 && CoinEntity.hasPlayerCoinInAir(p), "Retry did not consume one item/create one coin");
            CoinEntity.getPlayerCoinInAir(p).discard();
            h.assertTrue(!CoinEntity.hasPlayerCoinInAir(p), "Removed coin retained owner mapping");
            h.succeed();
        } finally { close(p); }
    }

    @GameTest(template="empty") public static void coinRemovalAfterOwnerLeavesClearsMapping(GameTestHelper h) {
        var p = player(h, AbilityCategory.ELECTROMASTER);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AcademyItems.COIN.get()));
        p.getMainHandItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        var coin = CoinEntity.getPlayerCoinInAir(p);
        h.assertTrue(coin != null, "No coin before logout");
        close(p);
        coin.discard();
        h.assertTrue(!CoinEntity.hasPlayerCoinInAir(p), "Offline owner mapping survived removal");
        h.succeed();
    }

    @GameTest(template="empty") public static void airProjectileAdmissionRollsBackPayment(GameTestHelper h) {
        var p = player(h, AbilityCategory.AEROHAND, "volcanic_ball");
        var before = data(p).captureDynamicResources();
        Consumer<EntityJoinLevelEvent> veto = e -> { if (e.getEntity() instanceof AeroProjectileEntity shot && shot.getOwner() == p) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, veto);
        try {
            h.assertTrue(!new VolcanicBallEffect().executeAndReport(p, data(p)), "Canceled air projectile reported success");
            h.assertTrue(before.equals(data(p).captureDynamicResources()) && data(p).getProficiency("volcanic_ball") == 0,
                    "Rejected projectile charged resources or granted experience");
        } finally { NeoForge.EVENT_BUS.unregister(veto); close(p); }
        h.succeed();
    }

    @GameTest(template="empty") public static void airProjectileActuallyMovesGrowsAndExpires(GameTestHelper h) {
        var p = player(h, AbilityCategory.AEROHAND, "volcanic_ball");
        var shot = launch(h, p);
        Vec3 start = shot.position();
        try {
            shot.tick();
            near(h, shot.getZ() - start.z, .3, "First update distance");
            near(h, shot.getDeltaMovement().z, .294, "Air drag");
            near(h, shot.getBbWidth(), .22, "Growing collision width");
            h.assertTrue(shot.legacyAge() == 2 && shot.isAlive(), "Old double-age semantics lost");
            for (int i = 1; i < 40; i++) shot.tick();
            h.assertTrue(shot.isRemoved(), "Expired air projectile persisted");
            h.succeed();
        } finally { shot.discard(); close(p); }
    }

    private static void delayedHit(GameTestHelper h, boolean dodge) {
        var p = player(h, AbilityCategory.AEROHAND, "volcanic_ball");
        var cow = EntityType.COW.create(h.getLevel());
        h.assertTrue(cow != null, "No victim");
        cow.setNoAi(true); cow.setNoGravity(true); cow.setPos(p.position().add(0, .7, 2.5));
        h.assertTrue(h.getLevel().addFreshEntity(cow), "Victim admission failed");
        float health = cow.getHealth(); var shot = launch(h, p);
        near(h, cow.getHealth(), health, "Launch must not hit immediately");
        if (dodge) cow.setPos(cow.position().add(4, 0, 0));
        h.runAfterDelay(14, () -> {
            try {
                h.assertTrue(dodge ? cow.getHealth() == health : cow.getHealth() < health, "World flight hit/evasion mismatch");
                h.assertTrue(dodge ? shot.isAlive() : shot.isRemoved(), "Impact lifecycle mismatch");
                h.succeed();
            } finally { cow.discard(); shot.discard(); close(p); }
        });
    }
    @GameTest(template="empty",timeoutTicks=40) public static void airProjectileDelayedWorldHit(GameTestHelper h) { delayedHit(h, false); }
    @GameTest(template="empty",timeoutTicks=40) public static void movingTargetEvadesAirProjectile(GameTestHelper h) { delayedHit(h, true); }

    @GameTest(template="empty") public static void unbreakableWallStopsAirProjectileAndDamage(GameTestHelper h) {
        var p = player(h, AbilityCategory.AEROHAND, "volcanic_ball");
        var wall = p.blockPosition().south().above();
        h.getLevel().setBlock(wall, Blocks.BEDROCK.defaultBlockState(), 3);
        var cow = EntityType.COW.create(h.getLevel()); cow.setNoAi(true); cow.setNoGravity(true); cow.setPos(p.position().add(0,.7,2.5));
        h.getLevel().addFreshEntity(cow); float health = cow.getHealth(); var shot = launch(h, p);
        try {
            shot.setDeltaMovement(0, 0, 4); shot.tick();
            h.assertTrue(shot.isRemoved() && h.getLevel().getBlockState(wall).is(Blocks.BEDROCK), "Bedrock was crossed or broken");
            near(h, cow.getHealth(), health, "Victim behind wall"); h.succeed();
        } finally { shot.discard(); cow.discard(); close(p); }
    }

    @GameTest(template="empty") public static void airJetPreservesMomentumAndDownwardAim(GameTestHelper h) {
        var p = player(h, AbilityCategory.AEROHAND, "air_jet");
        try {
            p.setXRot(90); p.setDeltaMovement(.7, -.5, .2);
            new AirJetEffect().execute(p, data(p));
            near(h, p.getDeltaMovement().x, .7, "Existing horizontal motion");
            near(h, p.getDeltaMovement().y, -2.5, "Downward impulse");
            near(h, p.getDeltaMovement().z, .2, "Existing lateral motion"); h.succeed();
        } finally { close(p); }
    }

    @GameTest(template="empty") public static void coolingExtinguishesButNetherOnlyCoolsQuarter(GameTestHelper h) {
        var p = player(h, AbilityCategory.AEROHAND, "air_cooling");
        var nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "Missing Nether test dimension");
        var n = new ServerPlayer(p.server, nether, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "nether-cooling"),
                net.minecraft.server.level.ClientInformation.createDefault());
        try {
            data(p).setCurrentOverload(200); p.setRemainingFireTicks(100);
            near(h, data(p).getCurrentOverload(), 200, "Overworld fixture overload");
            new AirCoolingEffect().execute(p, data(p));
            near(h, data(p).getCurrentOverload(), 0, "Overworld cooling");
            h.assertTrue(!p.isOnFire(), "Overworld cooling did not extinguish");
            data(n).setCurrentAbility(AbilityCategory.AEROHAND); data(n).setPlayerLevel(5);
            data(n).setDevMode(true); data(n).setCurrentOverload(200); n.setRemainingFireTicks(100);
            near(h, data(n).getCurrentOverload(), 200, "Nether fixture overload");
            new AirCoolingEffect().execute(n, data(n));
            near(h, data(n).getCurrentOverload(), 150, "Nether quarter cooling");
            h.assertTrue(n.getRemainingFireTicks() == 100, "Nether cooling incorrectly extinguished");
            h.succeed();
        } finally { close(p); }
    }

    @GameTest(template="empty") public static void paperPlaneAdmissionVetoKeepsItemCpAndExperience(GameTestHelper h) {
        var p = player(h, AbilityCategory.TELEKINESIS, "perfect_paper");
        var stack = new ItemStack(AcademyItems.PAPER_PLANE.get(), 2); p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var before = data(p).captureDynamicResources();
        Consumer<EntityJoinLevelEvent> veto = e -> { if (e.getEntity() instanceof ExtraPaperPlaneEntity plane && plane.getOwner() == p) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, veto);
        try {
            h.assertTrue(stack.use(h.getLevel(),p,InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL, "Rejected plane succeeded");
            h.assertTrue(stack.getCount()==2 && before.equals(data(p).captureDynamicResources()) && data(p).getProficiency("perfect_paper")==0,
                    "Rejected plane lost material/resources or gained experience");
        } finally { NeoForge.EVENT_BUS.unregister(veto); }
        try {
            h.assertTrue(stack.use(h.getLevel(),p,InteractionHand.MAIN_HAND).getResult().consumesAction(), "Accepted plane failed");
            h.assertTrue(stack.getCount()==1 && data(p).getCurrentCp()<before.cp() && data(p).getProficiency("perfect_paper")>0,
                    "Accepted reinforced plane did not commit item/payment/experience");
            h.getLevel().getEntitiesOfClass(ExtraPaperPlaneEntity.class,p.getBoundingBox().inflate(4),e->e.getOwner()==p).forEach(e->e.discard());
            h.succeed();
        } finally { close(p); }
    }

    @GameTest(template="empty") public static void holdingPaperDoesNotTrainPerfectPaper(GameTestHelper h) {
        var p = player(h, AbilityCategory.TELEKINESIS, "perfect_paper");
        try {
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER));
            for(int i=0;i<100;i++){p.tickCount=i;TelekinesisPassiveHandler.playerTick(new PlayerTickEvent.Post(p));}
            near(h,data(p).getProficiency("perfect_paper"),0,"Idle paper proficiency"); h.succeed();
        } finally { close(p); }
    }

    @GameTest(template="empty") public static void shadowAdmissionCallbackCannotRemoveBucketAndStillCommit(GameTestHelper h) {
        var p=player(h,AbilityCategory.TELEKINESIS,"liquid_shadow");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WATER_BUCKET));
        var before=data(p).captureDynamicResources();
        Consumer<EntityJoinLevelEvent> alter=e->{if(e.getEntity().getTags().contains("academy_liquid_shadow_owner_"+p.getUUID())) p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);};
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class,alter);
        try {
            h.assertTrue(!TelekinesisPassiveHandler.toggleLiquidShadow(p),"Bucketless callback committed shadow");
            h.assertTrue(!TelekinesisPassiveHandler.hasLiquidShadow(p)&&before.equals(data(p).captureDynamicResources()),"Failed shadow retained session/payment");
        } finally { NeoForge.EVENT_BUS.unregister(alter); }
        try {
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WATER_BUCKET));
            h.assertTrue(TelekinesisPassiveHandler.toggleLiquidShadow(p),"Normal shadow failed");
            h.assertTrue(p.getMainHandItem().is(Items.BUCKET)&&TelekinesisPassiveHandler.hasLiquidShadow(p),"Water bucket was not replaced exactly once");
            h.succeed();
        } finally { close(p); }
    }

    @GameTest(template="empty",timeoutTicks=35) public static void separatorWaitsFiveTicksAndPulsesOnlyOnce(GameTestHelper h) {
        var p=player(h,AbilityCategory.AEROHAND,"aero_separator"); data(p).setDevMode(true);
        var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(p.position().add(1,0,0));
        h.getLevel().addFreshEntity(cow);float health=cow.getHealth();
        h.assertTrue(new AeroSeparatorEffect().executeAndReport(p,data(p)),"Separator cast failed");
        near(h,cow.getHealth(),health,"Separator hurt during cast");
        h.runAfterDelay(3,()->near(h,cow.getHealth(),health,"Separator hurt before delay"));
        h.runAfterDelay(7,()->{
            h.assertTrue(cow.getHealth()<health,"Delayed separator never dealt damage");
            var second=EntityType.COW.create(h.getLevel());second.setNoAi(true);second.setNoGravity(true);second.setPos(p.position().add(1,0,0));
            h.getLevel().addFreshEntity(second);float secondHealth=second.getHealth();
            h.runAfterDelay(7,()->{
                try { near(h,second.getHealth(),secondHealth,"Separator pulsed repeatedly");h.succeed(); }
                finally {cow.discard();second.discard();close(p);}
            });
        });
    }

    @GameTest(template="empty") public static void hookCanceledDropKeepsExactlyOneRecoverableItem(GameTestHelper h) {
        var p=player(h,AbilityCategory.ELECTROMASTER);
        var hook=new EntityMagHook(AcademyEntities.MAG_HOOK.get(),h.getLevel());hook.launch(p,true);h.getLevel().addFreshEntity(hook);
        hook.tickCount=EntityMagHook.MAX_FLIGHT_TICKS+1;
        Consumer<EntityJoinLevelEvent> veto=e->{if(e.getEntity() instanceof ItemEntity item&&item.getItem().is(AcademyItems.MAG_HOOK.get()))e.setCanceled(true);};
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class,veto);
        try { hook.tick();h.assertTrue(hook.isAlive(),"Canceled hook return destroyed material owner"); }
        finally {NeoForge.EVENT_BUS.unregister(veto);}
        try {
            for(int i=0;i<21;i++)hook.tick();
            h.assertTrue(hook.isRemoved(),"Hook failed to settle when drop became available");
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(4),e->e.getItem().is(AcademyItems.MAG_HOOK.get()));
            h.assertTrue(drops.stream().mapToInt(e->e.getItem().getCount()).sum()==1,"Hook returned zero/multiple items");
            drops.forEach(e->e.discard());h.succeed();
        } finally {hook.discard();close(p);}
    }

    @GameTest(template="empty") public static void planeCanceledDropSurvivesSaveAndReturnsOnce(GameTestHelper h) {
        var p=player(h,AbilityCategory.TELEKINESIS);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AcademyItems.PAPER_PLANE.get()));
        p.getMainHandItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND);
        var plane=h.getLevel().getEntitiesOfClass(ExtraPaperPlaneEntity.class,p.getBoundingBox().inflate(3),e->e.getOwner()==p).getFirst();
        h.getLevel().setBlock(net.minecraft.core.BlockPos.containing(plane.position()).south(),Blocks.BEDROCK.defaultBlockState(),3);
        plane.setDeltaMovement(0,0,2);
        Consumer<EntityJoinLevelEvent> veto=e->{if(e.getEntity() instanceof ItemEntity item&&item.getItem().is(AcademyItems.PAPER_PLANE.get()))e.setCanceled(true);};
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class,veto);
        var saved=new CompoundTag();
        try {plane.tick();h.assertTrue(plane.isAlive(),"Canceled plane return lost material");plane.saveWithoutId(saved);}
        finally {NeoForge.EVENT_BUS.unregister(veto);plane.discard();}
        var restored=AcademyEntities.PAPER_PLANE.get().create(h.getLevel());restored.load(saved);h.getLevel().addFreshEntity(restored);
        try {
            restored.tick();h.assertTrue(restored.isRemoved(),"Loaded pending plane failed return");
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(4),e->e.getItem().is(AcademyItems.PAPER_PLANE.get()));
            h.assertTrue(drops.stream().mapToInt(e->e.getItem().getCount()).sum()==1,"Plane return was lost/duplicated across save");
            drops.forEach(e->e.discard());h.succeed();
        } finally {restored.discard();close(p);}
    }

    @GameTest(template="empty") public static void carrierRegistryStateWinsOverStaleNumericId(GameTestHelper h) {
        var p=player(h,AbilityCategory.ELECTROMASTER);
        var carrier=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),h.getLevel());
        var state=Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,net.minecraft.core.Direction.Axis.X);
        carrier.initializeMaterial(p,java.util.UUID.randomUUID(),state,p.blockPosition(),null,null,4);
        CompoundTag saved=new CompoundTag();carrier.saveWithoutId(saved);carrier.discard();
        saved.putInt("BlockState",net.minecraft.world.level.block.Block.getId(Blocks.DIAMOND_BLOCK.defaultBlockState()));
        var restored=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),h.getLevel());
        try {restored.load(saved);h.assertTrue(restored.blockState()==state,"Saved registry name/properties lost to stale raw ID");h.succeed();}
        finally {restored.discard();close(p);}
    }

    private static void cruiseFlight(GameTestHelper h,boolean wall) {
        var p=player(h,AbilityCategory.TELEKINESIS,"cruise_bomb");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WATER_BUCKET));
        var victim=EntityType.COW.create(h.getLevel());victim.setNoAi(true);victim.setNoGravity(true);victim.setPos(p.position().add(0,0,3.5));
        h.getLevel().addFreshEntity(victim);float health=victim.getHealth();
        if(wall)for(int x=-3;x<=3;x++)for(int y=0;y<4;y++)h.getLevel().setBlock(p.blockPosition().south(2).offset(x,y,0),Blocks.BEDROCK.defaultBlockState(),3);
        h.assertTrue(new CruiseBombEffect().executeAndReport(p,data(p)),"Cruise startup failed");
        CruiseBombEffect.tick(new PlayerTickEvent.Post(p));
        var orbs=h.getLevel().getEntitiesOfClass(CruiseBombEntity.class,p.getBoundingBox().inflate(3),e->e.getOwner()==p);
        h.assertTrue(orbs.size()==1&&orbs.getFirst().target()==victim,"No independently locked cruise entity");
        near(h,victim.getHealth(),health,"Cruise attacked without flight");
        h.runAfterDelay(28,()->{
            try {
                h.assertTrue(wall?victim.getHealth()==health:victim.getHealth()<health,"Cruise flight/occlusion mismatch");
                new CruiseBombEffect().executeAndReport(p,data(p));
                h.assertTrue(h.getLevel().getEntitiesOfClass(CruiseBombEntity.class,p.getBoundingBox().inflate(20),e->e.getOwner()==p&&e.isAlive()).isEmpty(),"Cruise toggle leaked live projectiles");
                h.succeed();
            } finally {victim.discard();close(p);}
        });
    }
    @GameTest(template="empty",timeoutTicks=50) public static void cruiseBombUsesRealDelayedFlight(GameTestHelper h){cruiseFlight(h,false);}
    @GameTest(template="empty",timeoutTicks=50) public static void cruiseBombCannotStrikeThroughWall(GameTestHelper h){cruiseFlight(h,true);}

    private static void laserOre(GameTestHelper h,int vetoMode) {
        var p=player(h,AbilityCategory.ELECTROMASTER);
        var stack=new ItemStack(AcademyItems.LASOR_GUN.get());p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var laser=(LaserGunItem)stack.getItem();laser.setEnergy(stack,1000);
        var pos=p.blockPosition().south(3).above();h.getLevel().setBlock(pos,Blocks.IRON_ORE.defaultBlockState(),3);
        int[] callbacks={0};
        Consumer<net.neoforged.neoforge.event.level.BlockEvent.BreakEvent> listener=e->{
            if(e.getPlayer()==p&&e.getPos().equals(pos)) {
                callbacks[0]++;
                if(vetoMode==1)e.setCanceled(true);
                if(vetoMode==2)h.getLevel().setBlock(pos,Blocks.BEDROCK.defaultBlockState(),3);
            }
        };
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent.class,listener);
        try {
            stack.use(h.getLevel(),p,InteractionHand.MAIN_HAND);
            h.assertTrue(ExtraItemData.isActive(stack),"Laser did not activate");
            for(int i=0;i<20;i++)laser.inventoryTick(stack,h.getLevel(),p,0,true);
            h.assertTrue(callbacks[0]>0,"Laser never attempted a real break");
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(8),e->e.getItem().is(Items.RAW_IRON));
            int amount=drops.stream().mapToInt(e->e.getItem().getCount()).sum();
            if(vetoMode==0)h.assertTrue(h.getLevel().getBlockState(pos).isAir()&&amount==1,"Laser destroyed ore without its normal drop");
            else h.assertTrue(h.getLevel().getBlockState(pos).is(vetoMode==1?Blocks.IRON_ORE:Blocks.BEDROCK)&&amount==0,"Break veto/replacement was ignored");
            h.assertTrue(laser.getEnergyStored(stack)<1000,"Laser performed work without energy cost");
            drops.forEach(e->e.discard());h.succeed();
        } finally {NeoForge.EVENT_BUS.unregister(listener);close(p);}
    }
    @GameTest(template="empty") public static void laserHarvestsOreLoot(GameTestHelper h){laserOre(h,0);}
    @GameTest(template="empty") public static void laserHonorsBreakCancellation(GameTestHelper h){laserOre(h,1);}
    @GameTest(template="empty") public static void laserRechecksBedrockReplacementAfterBreakHook(GameTestHelper h){laserOre(h,2);}

    private static void lanceGlass(GameTestHelper h,boolean cancel) {
        var p=player(h,AbilityCategory.AEROHAND,"bomber_lance");data(p).setDevMode(true);
        var wall=p.blockPosition().south().above();h.getLevel().setBlock(wall,Blocks.GLASS_PANE.defaultBlockState(),3);
        Consumer<net.neoforged.neoforge.event.level.BlockEvent.BreakEvent> veto=e->{if(e.getPlayer()==p&&e.getPos().equals(wall)&&cancel)e.setCanceled(true);};
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent.class,veto);
        AeroProjectileEntity shot=null;
        try {
            h.assertTrue(new BomberLanceEffect().executeAndReport(p,data(p)),"Lance cast failed");
            shot=h.getLevel().getEntitiesOfClass(AeroProjectileEntity.class,p.getBoundingBox().inflate(3),e->e.getOwner()==p).getFirst();
            for(int i=0;i<3&&!shot.isRemoved();i++)shot.tick();
            h.assertTrue(cancel?h.getLevel().getBlockState(wall).is(Blocks.GLASS_PANE):h.getLevel().getBlockState(wall).isAir(),"Lance fragile-block/permission mismatch");
            h.assertTrue(cancel?shot.isRemoved():shot.isAlive(),"Lance block collision lifecycle mismatch");h.succeed();
        } finally {NeoForge.EVENT_BUS.unregister(veto);if(shot!=null)shot.discard();close(p);}
    }
    @GameTest(template="empty") public static void bomberLanceHarvestsOriginalFragileBlocks(GameTestHelper h){lanceGlass(h,false);}
    @GameTest(template="empty") public static void bomberLanceHonorsFragileBlockProtection(GameTestHelper h){lanceGlass(h,true);}

    @GameTest(template="empty") public static void drillReturnVetoKeepsSavedReceiptThenReturnsOneStack(GameTestHelper h) {
        var p=player(h,AbilityCategory.TELEKINESIS,"paper_drill","perfect_paper");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,64));
        var drill=new PaperDrillEffect();drill.onChargingStart(p,data(p));
        h.assertTrue(drill.onChargingTick(p,data(p),1),"Paper drill did not actually consume its startup resources");
        h.assertTrue(p.getInventory().countItem(Items.PAPER)==0,"Paper drill did not consume exactly 64 paper");
        for(int i=0;i<p.getInventory().items.size();i++)p.getInventory().items.set(i,new ItemStack(Items.STONE,64));
        Consumer<EntityJoinLevelEvent> veto=e->{if(e.getEntity() instanceof ItemEntity item&&item.getItem().is(Items.PAPER))e.setCanceled(true);};
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class,veto);
        try {
            h.assertTrue(drill.tryRelease(p,data(p),1),"Paid drill failed release");
            h.assertTrue(p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).getInt("academy_extra_paper_due")==64,
                    "Vetoed return was not retained in the persistent player receipt");
            CompoundTag saved=new CompoundTag();p.saveWithoutId(saved);
            h.assertTrue(saved.toString().contains("academy_extra_paper_due:64"),"Pending material did not enter actual player NBT");
        } finally {NeoForge.EVENT_BUS.unregister(veto);}
        try {
            p.getInventory().items.set(0,ItemStack.EMPTY);p.tickCount=20;ExtraPaperReturns.tick(new PlayerTickEvent.Post(p));
            h.assertTrue(p.getInventory().countItem(Items.PAPER)==64,"Deferred drill return lost paper");
            h.assertTrue(!drill.tryRelease(p,data(p),1),"Released drill could return again");
            ExtraPaperReturns.tick(new PlayerTickEvent.Post(p));
            h.assertTrue(p.getInventory().countItem(Items.PAPER)==64,"Deferred receipt returned twice");h.succeed();
        } finally {drill.onChargingAbort(p,data(p));close(p);}
    }

    @GameTest(template="empty") public static void paperArmorBreakupSurvivesDropCancellation(GameTestHelper h) {
        var p=player(h,AbilityCategory.TELEKINESIS);
        for(int i=0;i<p.getInventory().items.size();i++)p.getInventory().items.set(i,new ItemStack(Items.STONE,64));
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new ItemStack(AcademyItems.PAPER_HELMET.get()));
        Consumer<EntityJoinLevelEvent> veto=e->{if(e.getEntity() instanceof ItemEntity item&&item.getItem().is(Items.PAPER))e.setCanceled(true);};
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class,veto);
        try {
            ExtraEquipmentHandler.paperArmorTick(new PlayerTickEvent.Post(p));
            h.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty(),"Unaffordable paper armour remained equipped");
            h.assertTrue(p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).getInt("academy_extra_paper_due")==5,"Paper helmet breakup lost its five paper");
        } finally {NeoForge.EVENT_BUS.unregister(veto);}
        try {
            p.getInventory().items.set(0,ItemStack.EMPTY);p.tickCount=20;ExtraPaperReturns.tick(new PlayerTickEvent.Post(p));
            h.assertTrue(p.getInventory().countItem(Items.PAPER)==5,"Paper helmet breakup returned wrong quantity");h.succeed();
        } finally {close(p);}
    }

    private static void broadTransmission(GameTestHelper h,boolean denied) {
        var p=player(h,AbilityCategory.TELEKINESIS,"psycho_transmission");
        for(int i=0;i<p.getInventory().items.size();i++)p.getInventory().items.set(i,new ItemStack(Items.STONE,64));
        var item=new ItemEntity(h.getLevel(),p.getX()+.7,p.getEyeY()+.3,p.getZ()+3,new ItemStack(Items.DIAMOND,3));
        item.setNoGravity(true);item.setDeltaMovement(Vec3.ZERO);item.setPickUpDelay(0);h.getLevel().addFreshEntity(item);
        Vec3 original=item.position();
        h.assertTrue(new PsychoTransmissionEffect().executeAndReport(p,data(p)),"Transmission startup failed");
        float before=data(p).getCurrentCp();
        Consumer<net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre> permission=e->{if(denied&&e.getPlayer()==p&&e.getItemEntity()==item)e.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);};
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre.class,permission);
        try {
            PsychoTransmissionEffect.tick(new PlayerTickEvent.Post(p));
            h.assertTrue(item.isAlive()&&item.getItem().getCount()==3,"Transmission lost or duplicated full-inventory stack");
            if(denied)near(h,item.position().distanceTo(original),0,"Vetoed stack moved");
            else {near(h,item.position().distanceTo(p.position()),0,"Wide-box target was not brought to caster");h.assertTrue(data(p).getCurrentCp()<before-.5F,"Full-inventory transmission did not pay distance cost");}
            h.succeed();
        } finally {NeoForge.EVENT_BUS.unregister(permission);item.discard();close(p);}
    }
    @GameTest(template="empty") public static void transmissionUsesOriginalWideBoxWithFullInventory(GameTestHelper h){broadTransmission(h,false);}
    @GameTest(template="empty") public static void fullInventoryTransmissionHonorsPickupVeto(GameTestHelper h){broadTransmission(h,true);}
}
