package com.mohistmc.academy.regression;

import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.gametest.recheck.RecheckPlayers;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.aerohand.AeroPassiveRuntime;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.item.ImagEnergyArmorItem;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real vanilla attack/collision entry points in the base mod test runtime, not a third-party pack gate. */
@GameTestHolder("academy_native_combat")
@PrefixGameTestTemplate(false)
public final class NativeCombatGameTests {
    private NativeCombatGameTests() {}

    @GameTest(template = "empty", batch = "native_combat_mace", timeoutTicks = 100)
    public static void actualMaceSmashAndDensityKeepVanillaDamageAndDurability(GameTestHelper h) {
        Fixture f = new Fixture(h, 2);
        f.mace(0, null, 0);
        f.mace(1, Enchantments.DENSITY, 3);
        f.ready(() -> {
            for (int i = 0; i < 2; i++) {
                f.resetTarget();
                ServerPlayer attacker = f.prepareAttack(i, true);
                int before = f.observer.hits.size();
                attacker.attack(f.target);
                Hit hit = f.observer.onlyHitAfter(before, f.target);
                // Three fallen blocks: (6 base + 12 smash [+ 4.5 Density III]) * 1.5 critical.
                float expected = i == 0 ? 27 : 33.75F;
                near(hit.amount, expected, "Native mace incoming damage");
                near(100 - f.target.getHealth(), expected, "Native mace health loss");
                check(hit.source.is(DamageTypes.PLAYER_ATTACK) && hit.source.getEntity() == attacker
                        && hit.source.getDirectEntity() == attacker && f.target.getKillCredit() == attacker,
                        "Mace lost the actual player damage/kill attribution");
                check(attacker.getMainHandItem().getDamageValue() == 1 && attacker.fallDistance == 0,
                        "Mace did not execute native postHurtEnemy durability/fall reset");
                LogUtils.getLogger().info("NATIVE_COMBAT_MACE density={} raw={} hp={} durability={} fall={}",
                        i == 0 ? 0 : 3, hit.amount, f.target.getHealth(), attacker.getMainHandItem().getDamageValue(), attacker.fallDistance);
            }
        });
    }

    @GameTest(template = "empty", batch = "native_combat_breach", timeoutTicks = 100)
    public static void actualMaceBreachAndProtectionResistanceAbsorptionUseVanillaOrder(GameTestHelper h) {
        Fixture f = new Fixture(h, 3);
        f.mace(0, null, 0);
        f.mace(1, Enchantments.BREACH, 4);
        f.mace(2, null, 0);
        ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        f.target.setItemSlot(EquipmentSlot.CHEST, chest);
        f.ready(() -> {
            near((float) f.target.getAttributeValue(Attributes.ARMOR), 8, "Real equipped chest armour");
            near((float) f.target.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 2, "Real equipped chest toughness");
            float[] expected = {4.656F, 6F}; // 6 * (1 - (8 - 6 / 2.5) / 25); Breach IV removes .6 effectiveness.
            for (int i = 0; i < 2; i++) {
                f.resetTarget();
                f.prepareAttack(i, false).attack(f.target);
                near(100 - f.target.getHealth(), expected[i], "Native Breach armour calculation");
            }
            f.enchant(chest, Enchantments.PROTECTION, 4);
            f.target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
            f.target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 0));
            f.resetTarget();
            near(f.target.getAbsorptionAmount(), 4, "Real Absorption I effect setup");
            f.prepareAttack(2, false).attack(f.target);
            // Armour, then Resistance I (.8), Protection IV (.84), then absorption hearts.
            near(f.target.getHealth(), 100, "Fully absorbed hit changed health");
            near(f.target.getAbsorptionAmount(), 4 - 4.656F * .8F * .84F, "Vanilla mitigation order");
            check(chest.getDamageValue() >= 3 && f.actor(2).getMainHandItem().getDamageValue() == 1,
                    "Absorbed accepted melee skipped native equipment durability");
            LogUtils.getLogger().info("NATIVE_COMBAT_MITIGATION plain={} breach={} protection=4 resistance=1 absorptionRemaining={}",
                    expected[0], expected[1], f.target.getAbsorptionAmount());
        });
    }

    @GameTest(template = "empty", batch = "native_combat_wind_burst", timeoutTicks = 100)
    public static void actualWindBurstOnlyExplodesOnSmashWithoutExtraHealthDamage(GameTestHelper h) {
        Fixture f = new Fixture(h, 2);
        f.mace(0, Enchantments.WIND_BURST, 2);
        f.mace(1, Enchantments.WIND_BURST, 2);
        f.ready(() -> {
            List<net.minecraft.world.level.Explosion> explosions = new ArrayList<>();
            java.util.function.Consumer<ExplosionEvent.Start> observeExplosion = event -> {
                if (event.getLevel() == h.getLevel()
                        && event.getExplosion().center().distanceToSqr(f.target.position()) < 100)
                    explosions.add(event.getExplosion());
            };
            NeoForge.EVENT_BUS.addListener(ExplosionEvent.Start.class, observeExplosion);
            try {
                f.prepareAttack(0, false).attack(f.target);
                check(f.observer.explosions == 0 && explosions.isEmpty(), "Grounded mace incorrectly triggered Wind Burst");
                near(100 - f.target.getHealth(), 6, "Grounded Wind Burst mace damage");
                f.resetTarget();
                ServerPlayer attacker = f.prepareAttack(1, true);
                // NeoForge preserves spectator flying across a game-mode change until the next tick.
                // This synchronous fixture switches the parked actor back immediately: remove only
                // that inherited flight flag so the real Wind Burst !isFlying predicate can match.
                boolean inheritedSpectatorFlight = attacker.getAbilities().flying;
                attacker.getAbilities().flying = false;
                attacker.onUpdateAbilities();
                check(!attacker.isSpectator() && !attacker.isFallFlying(), "Wind Burst fixture is not a survival smash");
                int before = f.observer.hits.size();
                attacker.attack(f.target);
                Vec3 impulse = explosions.size() == 1 ? explosions.getFirst().getHitPlayers().get(attacker) : null;
                LogUtils.getLogger().info("NATIVE_COMBAT_WIND_BURST explosions={} inheritedSpectatorFlight={} nativeImpulse={} attackerMotion={} hp={}",
                        f.observer.explosions, inheritedSpectatorFlight, impulse, attacker.getDeltaMovement(), f.target.getHealth());
                near(100 - f.target.getHealth(), 27, "Wind Burst added non-vanilla health damage");
                check(f.observer.onlyHitAfter(before, f.target).source.is(DamageTypes.PLAYER_ATTACK),
                        "Wind Burst target received another damage type");
                check(f.observer.explosions == 1 && impulse != null && impulse.y > 0 && attacker.getDeltaMovement().y > .01,
                        "Actual Wind Burst post-attack explosion/launch did not execute: explosions="
                                + f.observer.explosions + " impulse=" + impulse + " motion=" + attacker.getDeltaMovement());
                check(attacker.getDeltaMovement().subtract(impulse).distanceTo(new Vec3(0, .01F, 0)) < .000001,
                        "Server motion did not add the native explosion impulse to MaceItem's initial .01 lift");
            } finally {
                NeoForge.EVENT_BUS.unregister(observeExplosion);
            }
        });
    }

    @GameTest(template = "empty", batch = "native_combat_arrow", timeoutTicks = 100)
    public static void actualArrowCollisionPreservesOwnerAndPaysOrdinaryImaginaryIfOnce(GameTestHelper h) {
        Fixture f = new Fixture(h, 1);
        ItemStack chest = f.chargedChest(f.target);
        f.ready(() -> {
            f.prepareAttack(0, false);
            Arrow arrow = new Arrow(h.getLevel(), f.actor(0), new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
            arrow.setBaseDamage(2);
            f.collide(arrow);
            f.assertProjectile(arrow, DamageTypes.ARROW, 4);
            check(energy(chest) == ImagEnergyArmorItem.MAX_ENERGY - 720,
                    "Arrow did not settle exactly 4 * .40 * .90 * 500 ordinary IF");
            near(f.target.getHealth(), 100 - 4 * .64F, "Arrow powered chest mitigation");
            check(f.target.getArrowCount() == 1 && arrow.isRemoved(), "Actual non-piercing arrow hit did not complete");
            LogUtils.getLogger().info("NATIVE_COMBAT_ARROW raw=4 ifCost={} hp={} arrows={}",
                    ImagEnergyArmorItem.MAX_ENERGY - energy(chest), f.target.getHealth(), f.target.getArrowCount());
        });
    }

    @GameTest(template = "empty", batch = "native_combat_trident", timeoutTicks = 100)
    public static void actualTridentCollisionPaysOffenseArmourOnce(GameTestHelper h) {
        Fixture f = new Fixture(h, 1);
        f.ready(() -> {
            f.prepareAttack(0, false);
            PlayerAbilityData data = f.armour(f.target);
            float cp = data.getCurrentCp(), overload = data.getCurrentOverload();
            ThrownTrident trident = new ThrownTrident(h.getLevel(), f.actor(0), new ItemStack(Items.TRIDENT));
            f.collide(trident);
            f.assertProjectile(trident, DamageTypes.TRIDENT, 8);
            f.assertArmourCost(data, cp, overload, 8);
            near(f.target.getHealth(), 100 - 8 * .075F, "Trident offense-armour mitigation");
            check(trident.getDeltaMovement().z < 0, "Actual trident did not enter its post-hit bounce");
            LogUtils.getLogger().info("NATIVE_COMBAT_TRIDENT raw=8 cpCost={} overloadCost={} hp={}",
                    cp - data.getCurrentCp(), data.getCurrentOverload() - overload, f.target.getHealth());
        });
    }

    @GameTest(template = "empty", batch = "native_combat_wind_charge", timeoutTicks = 100)
    public static void actualWindChargeDirectHitAndGustKeepSingleOrdinaryIfPayment(GameTestHelper h) {
        Fixture f = new Fixture(h, 1);
        ItemStack chest = f.chargedChest(f.target);
        f.ready(() -> {
            f.prepareAttack(0, false);
            WindCharge charge = new WindCharge(f.actor(0), h.getLevel(), f.target.getX(), f.target.getY() + .9, f.target.getZ() - 2);
            f.collide(charge);
            f.assertProjectile(charge, DamageTypes.WIND_CHARGE, 1);
            check(f.observer.explosions == 1 && charge.isRemoved(), "Wind Charge did not execute its native gust/discard path");
            check(energy(chest) == ImagEnergyArmorItem.MAX_ENERGY - 180,
                    "Wind Charge direct hit or gust charged IF more than once");
            near(f.target.getHealth(), 99.36F, "Wind Charge gust incorrectly dealt additional health damage");
            LogUtils.getLogger().info("NATIVE_COMBAT_WIND_CHARGE raw=1 explosions={} ifCost={} hp={}",
                    f.observer.explosions, ImagEnergyArmorItem.MAX_ENERGY - energy(chest), f.target.getHealth());
        });
    }

    @GameTest(template = "empty", batch = "native_combat_shield", timeoutTicks = 100)
    public static void actualArrowShieldFrontRejectsAndRearPaysDefensesOnce(GameTestHelper h) {
        Fixture f = new Fixture(h, 1);
        ItemStack chest = f.chargedChest(f.target);
        f.shield();
        f.ready(() -> {
            f.prepareAttack(0, false);
            PlayerAbilityData data = f.armour(f.target);
            var before = data.toSyncTag().copy();
            f.faceTarget(180);
            check(f.target.isBlocking(), "Shield never reached its native use threshold");
            Arrow front = new Arrow(h.getLevel(), f.actor(0), new ItemStack(Items.ARROW), null);
            front.setBaseDamage(2);
            f.collide(front);
            near(f.target.getHealth(), 100, "Front arrow passed through shield");
            check(energy(chest) == ImagEnergyArmorItem.MAX_ENERGY && before.equals(data.toSyncTag()),
                    "Fully blocked actual arrow paid IF/CP/proficiency");
            check(f.target.getOffhandItem().getDamageValue() > 0 && front.getDeltaMovement().z < 0,
                    "Native shield durability/arrow rebound did not execute");
            front.discard();
            f.resetTarget();
            f.faceTarget(0); // Same trajectory now reaches the unguarded rear.
            float cp = data.getCurrentCp(), overload = data.getCurrentOverload();
            Arrow rear = new Arrow(h.getLevel(), f.actor(0), new ItemStack(Items.ARROW), null);
            rear.setBaseDamage(2);
            int hitCount = f.observer.hits.size();
            f.collide(rear);
            near(f.observer.onlyHitAfter(hitCount, f.target).amount, 4, "Rear arrow raw hit");
            f.assertArmourCost(data, cp, overload, 4);
            check(energy(chest) == ImagEnergyArmorItem.MAX_ENERGY - 54,
                    "Rear arrow did not settle IF after offense-armour reduction exactly once");
            near(f.target.getHealth(), 100 - 4 * .075F * .64F, "Rear shield damage/reduction");
            LogUtils.getLogger().info("NATIVE_COMBAT_SHIELD frontIfCost=0 rearIfCost={} rearCpCost={} hp={}",
                    ImagEnergyArmorItem.MAX_ENERGY - energy(chest), cp - data.getCurrentCp(), f.target.getHealth());
        });
    }

    @GameTest(template = "empty", batch = "native_combat_thorns", timeoutTicks = 100)
    public static void actualMeleeThornsSettlesOrdinaryDefenseAndSkipsCanceledOrShieldedHits(GameTestHelper h) {
        Fixture f = new Fixture(h, 3);
        for (int i = 0; i < 3; i++) f.mace(i, null, 0);
        ItemStack thorns = new ItemStack(Items.DIAMOND_CHESTPLATE);
        f.enchant(thorns, Enchantments.THORNS, 3);
        f.target.setItemSlot(EquipmentSlot.CHEST, thorns);
        f.shield();
        f.ready(() -> {
            for (int i = 0; i < 3; i++) {
                f.resetTarget();
                ServerPlayer attacker = f.prepareAttack(i, false);
                ItemStack chest = f.chargedChest(attacker);
                PlayerAbilityData data = f.armour(attacker);
                var before = data.toSyncTag().copy();
                float cp = data.getCurrentCp(), overload = data.getCurrentOverload();
                f.observer.cancelMelee = i == 1;
                f.faceTarget(i == 2 ? 180 : 0);
                int count = f.observer.hits.size();
                // Enchantment damage-context loot predicates use ServerLevel.random. A fixed seed in
                // this isolated synchronous batch makes genuine level-III Thorns deterministic.
                h.getLevel().getRandom().setSeed(4096L);
                attacker.attack(f.target);
                List<Hit> reflected = f.observer.hits.subList(count, f.observer.hits.size()).stream()
                        .filter(hit -> hit.victim == attacker && hit.source.is(DamageTypes.THORNS)).toList();
                if (i == 0) {
                    check(reflected.size() == 1, "Native successful melee did not dispatch exactly one real Thorns hit");
                    Hit hit = reflected.getFirst();
                    check(hit.source.getEntity() == f.target && !hit.ability,
                            "Thorns lost the wearer owner or inherited an Academy ability context");
                    f.assertArmourCost(data, cp, overload, hit.amount);
                    check(energy(chest) == ImagEnergyArmorItem.MAX_ENERGY - Math.round(hit.amount * .075F * .40F * .90F * 500),
                            "Nested real Thorns used the wrong IF rate or settled twice");
                    check(attacker.getHealth() < 100 && thorns.getDamageValue() >= 3,
                            "Thorns did not damage its actual attacker and enchanted item");
                    LogUtils.getLogger().info("NATIVE_COMBAT_THORNS raw={} cpCost={} ifCost={} abilityContext={}",
                            hit.amount, cp - data.getCurrentCp(), ImagEnergyArmorItem.MAX_ENERGY - energy(chest), hit.ability);
                } else {
                    check(reflected.isEmpty() && before.equals(data.toSyncTag()) && energy(chest) == ImagEnergyArmorItem.MAX_ENERGY,
                            "Canceled/shielded actual melee triggered Thorns or paid attacker defenses");
                    near(f.target.getHealth(), 100, "Rejected melee damaged the shield/Thorns wearer");
                    check(attacker.getMainHandItem().getDamageValue() == 0,
                            "Rejected native melee invoked postHurtEnemy durability");
                }
            }
        });
    }

    private static int energy(ItemStack stack) {
        return ((ImagEnergyArmorItem) stack.getItem()).getEnergyStored(stack);
    }

    private static void near(float actual, float expected, String label) {
        check(Math.abs(actual - expected) < .002F, label + ": expected=" + expected + " actual=" + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }

    private record Hit(ServerPlayer victim, DamageSource source, float amount, boolean ability) {}

    public static final class Observer {
        private final Fixture fixture;
        private final List<Hit> hits = new ArrayList<>();
        private boolean cancelMelee;
        private int explosions;
        private Observer(Fixture fixture) { this.fixture = fixture; }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void incoming(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player) || !fixture.players.contains(player)) return;
            hits.add(new Hit(player, event.getSource(), event.getAmount(),
                    AcademyDamageHelper.isAbilityDamageInProgress(player, event.getSource())));
            if (cancelMelee && player == fixture.target && event.getSource().is(DamageTypes.PLAYER_ATTACK)) event.setCanceled(true);
        }

        @SubscribeEvent
        public void explosion(ExplosionEvent.Start event) {
            if (event.getLevel() == fixture.h.getLevel()
                    && event.getExplosion().center().distanceToSqr(fixture.target.position()) < 100) explosions++;
        }

        private Hit onlyHitAfter(int count, ServerPlayer victim) {
            List<Hit> selected = hits.subList(count, hits.size()).stream().filter(hit -> hit.victim == victim).toList();
            check(selected.size() == 1, "Expected exactly one native incoming hit, got " + selected.size());
            return selected.getFirst();
        }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private final GameTestHelper h;
        private final List<RecheckPlayers.Session> sessions = new ArrayList<>();
        private final List<ServerPlayer> players = new ArrayList<>();
        private final List<Projectile> projectiles = new ArrayList<>();
        private final ServerPlayer target;
        private final Observer observer = new Observer(this);
        private boolean closed;

        private Fixture(GameTestHelper h, int attackers) {
            this.h = h;
            h.testInfo.addListener(this);
            for (int i = 0; i <= attackers; i++) {
                var session = RecheckPlayers.connect(h);
                sessions.add(session);
                ServerPlayer player = session.player();
                players.add(player);
                player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
                player.setHealth(100);
                position(player, 1.5, 2, 1.5);
            }
            target = players.getLast();
            resetTarget();
        }

        private ServerPlayer actor(int index) { return players.get(index); }

        private void mace(int index, ResourceKey<Enchantment> enchantment, int level) {
            ItemStack stack = new ItemStack(Items.MACE);
            if (enchantment != null) enchant(stack, enchantment, level);
            actor(index).setItemInHand(InteractionHand.MAIN_HAND, stack);
        }

        private void enchant(ItemStack stack, ResourceKey<Enchantment> key, int level) {
            stack.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key), level);
        }

        private ItemStack chargedChest(ServerPlayer player) {
            ItemStack chest = new ItemStack(AcademyItems.IMAG_CHESTPLATE.get());
            ((ImagEnergyArmorItem) chest.getItem()).setEnergy(chest, ImagEnergyArmorItem.MAX_ENERGY);
            player.setItemSlot(EquipmentSlot.CHEST, chest);
            return chest;
        }

        private void shield() {
            target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
            target.startUsingItem(InteractionHand.OFF_HAND);
        }

        private void ready(Runnable assertions) {
            // Real login protection and weapon-attribute/cooldown ticks; no hurt/tick override.
            h.runAfterDelay(65, () -> {
                boolean pvp = h.getLevel().getServer().isPvpAllowed();
                boolean academyPvp = ACConfig.Server.PVP_ENABLED.get();
                NeoForge.EVENT_BUS.register(observer);
                try {
                    h.getLevel().getServer().setPvpAllowed(true);
                    ACConfig.Server.PVP_ENABLED.set(true);
                    assertions.run();
                    h.succeed();
                } finally {
                    NeoForge.EVENT_BUS.unregister(observer);
                    h.getLevel().getServer().setPvpAllowed(pvp);
                    ACConfig.Server.PVP_ENABLED.set(academyPvp);
                    close();
                }
            });
        }

        private void resetTarget() {
            target.setHealth(100);
            target.invulnerableTime = 0;
            target.setDeltaMovement(Vec3.ZERO);
            position(target, 3.5, 2, 5.5);
            faceTarget(0);
        }

        private void faceTarget(float yaw) {
            target.setYRot(yaw);
            target.setYHeadRot(yaw);
            target.setXRot(0);
        }

        private ServerPlayer prepareAttack(int index, boolean smash) {
            for (int i = 0; i < players.size() - 1; i++) {
                ServerPlayer other = actor(i);
                other.setGameMode(i == index ? GameType.SURVIVAL : GameType.SPECTATOR);
                if (i != index) position(other, 1.5, 2, 1.5);
            }
            ServerPlayer player = actor(index);
            position(player, 3.5, 2, 2.5);
            player.setDeltaMovement(Vec3.ZERO);
            player.setSprinting(false);
            player.setOnGround(!smash);
            player.fallDistance = smash ? 3 : 0;
            player.setYRot(0);
            player.setXRot(0);
            player.invulnerableTime = 0;
            check(player.getAttackStrengthScale(.5F) == 1, "Native attack cooldown did not naturally recover");
            if (player.getMainHandItem().is(Items.MACE))
                near((float) player.getAttributeValue(Attributes.ATTACK_DAMAGE), 6, "Native mace equipment attribute");
            return player;
        }

        private void position(ServerPlayer player, double x, double y, double z) {
            BlockPos origin = h.absolutePos(BlockPos.ZERO);
            player.setPos(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
        }

        private void collide(Projectile projectile) {
            projectiles.add(projectile);
            projectile.setPos(target.getX(), target.getY() + .9, target.getZ() - 2);
            projectile.setDeltaMovement(0, 0, 2);
            projectile.setNoGravity(true);
            check(h.getLevel().addFreshEntity(projectile), "Native projectile could not enter the world");
            // One actual tick crosses the stationary target's bounding box. This includes the native
            // collision/deflection/PvP/impact-event path, without reflection or direct onHit calls.
            projectile.tick();
        }

        private void assertProjectile(Projectile projectile, ResourceKey<net.minecraft.world.damagesource.DamageType> type, float raw) {
            Hit hit = observer.onlyHitAfter(0, target);
            near(hit.amount, raw, "Native projectile incoming amount");
            check(hit.source.is(type) && hit.source.getDirectEntity() == projectile
                            && hit.source.getEntity() == actor(0) && target.getKillCredit() == actor(0) && !hit.ability,
                    "Native projectile source/owner/kill credit/ordinary context changed");
        }

        private PlayerAbilityData armour(ServerPlayer player) {
            PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            data.setCurrentAbility(AbilityCategory.AEROHAND);
            data.setPlayerLevel(5);
            data.setAbilityActive(true);
            data.setDevMode(false);
            data.learnSkill("offense_armour");
            data.setProficiency("offense_armour", .5F);
            data.setCurrentCp(8000);
            data.setCurrentOverload(0);
            check(AeroPassiveRuntime.toggleOffenseArmour(player, data), "Actual offense armour toggle failed");
            return data;
        }

        private void assertArmourCost(PlayerAbilityData data, float cp, float overload, float incoming) {
            near(cp - data.getCurrentCp(), DynamicSkillRules.cp("offense_armour", incoming * 20), "Single ordinary defense CP payment");
            near(data.getCurrentOverload() - overload, DynamicSkillRules.overload("offense_armour", incoming * .5F), "Single ordinary defense overload payment");
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            for (Projectile projectile : projectiles) projectile.discard();
            for (ServerPlayer player : players)
                AeroPassiveRuntime.terminateSustained(player, player.getData(AcademyAttachments.PLAYER_ABILITY));
            for (RecheckPlayers.Session session : sessions) session.close();
        }

        @Override public void testStructureLoaded(GameTestInfo info) {}
        @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo previous, GameTestInfo next, GameTestRunner runner) { close(); }
    }
}
