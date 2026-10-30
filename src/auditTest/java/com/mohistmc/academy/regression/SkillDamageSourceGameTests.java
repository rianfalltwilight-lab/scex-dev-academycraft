package com.mohistmc.academy.regression;

import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.gametest.recheck.RecheckPlayers;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillDamageSource;
import com.mohistmc.academy.skill.ability.aerohand.AeroSeparatorEffect;
import com.mohistmc.academy.skill.ability.vecmanip.DirShockEffect;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loaded damage tags and real ability/hurt/login/tick entrypoints; no damage-method overrides. */
@GameTestHolder("academy_native_combat")
@PrefixGameTestTemplate(false)
public final class SkillDamageSourceGameTests {
    private record Profile(ResourceKey<DamageType> skill, ResourceKey<DamageType> nativeType) {}

    @GameTest(template = "empty", batch = "native_skill_sources")
    public static void loadedSkillProfilesPreserveAllNativeDamageTags(GameTestHelper h) {
        var registry = h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        for (Profile pair : new Profile[]{new Profile(SkillDamageSource.ABILITY, DamageTypes.PLAYER_ATTACK),
                new Profile(SkillDamageSource.MAGIC, DamageTypes.MAGIC),
                new Profile(SkillDamageSource.THROWN, DamageTypes.THROWN),
                new Profile(SkillDamageSource.DROWNING, DamageTypes.DROWN)}) {
            var skill = registry.getHolderOrThrow(pair.skill());
            var original = registry.getHolderOrThrow(pair.nativeType());
            DamageType a = skill.value(), b = original.value();
            h.assertTrue(a.scaling() == b.scaling() && a.exhaustion() == b.exhaustion()
                            && a.effects() == b.effects() && a.deathMessageType() == b.deathMessageType(),
                    "Skill profile changed native damage properties: " + pair.skill().location());
            registry.getTagNames().filter(tag -> tag.location().getNamespace().equals("minecraft"))
                    .forEach(tag -> h.assertTrue(skill.is(tag) == original.is(tag),
                            "Native tag mismatch " + tag.location() + " for " + pair.skill().location()));
        }
        for (String id : new String[]{"psycho_needle", "air_blade", "cruise_bomb"}) {
            var type = registry.getHolderOrThrow(academyType(id));
            h.assertTrue(type.is(DamageTypeTags.BYPASSES_ARMOR) && type.is(DamageTypeTags.IS_PROJECTILE)
                            && !type.is(DamageTypeTags.BYPASSES_RESISTANCE)
                            && !type.is(DamageTypeTags.BYPASSES_ENCHANTMENTS),
                    "Released piercing-projectile classification changed: " + id);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "native_skill_sources")
    public static void skillSourcesKeepDirectOwnerPositionAndHonestDeathMessages(GameTestHelper h) {
        var session = session(h);
        ServerPlayer caster = session.player();
        try {
            var weapon = new ItemStack(Items.MACE);
            weapon.set(DataComponents.CUSTOM_NAME, Component.literal("UNRELATED_NAMED_WEAPON"));
            caster.setItemSlot(EquipmentSlot.MAINHAND, weapon);
            var projectile = EntityType.SNOWBALL.create(h.getLevel());
            projectile.setPos(caster.position().add(0, 1, 2));
            var victim = h.spawn(EntityType.COW, new BlockPos(5, 2, 5));
            victim.setCustomName(Component.literal("SOURCE_VICTIM"));
            DamageSource[] sources = {SkillDamageSource.attack(caster), SkillDamageSource.magic(caster),
                    SkillDamageSource.thrown(caster, projectile), SkillDamageSource.drowning(caster, false),
                    SkillDamageSource.projectile(caster, projectile, academyType("psycho_needle")),
                    SkillDamageSource.projectile(caster, projectile, academyType("air_blade")),
                    SkillDamageSource.projectile(caster, projectile, academyType("cruise_bomb")),
                    SkillDamageSource.explosion(caster)};
            for (DamageSource source : sources) {
                h.assertTrue(source.getEntity() == caster && source.getWeaponItem() == null,
                        "Skill source lost owner or borrowed held weapon: " + source);
                if (source.is(SkillDamageSource.DROWNING)) {
                    h.assertTrue(source.getDirectEntity() == null && source.getSourcePosition() == null,
                            "Vacuum acquired a fake direct entity or impact position");
                } else {
                    boolean thrown = source.is(DamageTypeTags.IS_PROJECTILE);
                    h.assertTrue(source.getDirectEntity() == (thrown ? projectile : caster)
                                    && source.getSourcePosition().equals((thrown ? projectile : caster).position()),
                            "Skill source changed direct entity/position: " + source);
                }
                assertMessage(h, source, victim, true);
            }
            DamageSource self = SkillDamageSource.drowning(caster, true);
            h.assertTrue(self.getEntity() == null && self.getDirectEntity() == null,
                    "Vacuum self damage acquired a hostile owner");
            assertMessage(h, self, victim, false);
            h.assertTrue(SkillDamageSource.explosion(caster).is(DamageTypes.PLAYER_EXPLOSION),
                    "Plasma Cannon no longer has its native player-explosion type");
            h.succeed();
        } finally { session.close(); }
    }

    @GameTest(template = "empty", batch = "native_skill_sources")
    public static void explicitSourcesPreservePublicIdentityAcrossNestedOrdinaryDamage(GameTestHelper h) {
        var session = session(h);
        ServerPlayer caster = session.player();
        var outerTarget = h.spawn(EntityType.COW, new BlockPos(4, 2, 4));
        var innerTarget = h.spawn(EntityType.COW, new BlockPos(6, 2, 4));
        DamageSource outer = SkillDamageSource.attack(caster);
        DamageSource inner = caster.damageSources().generic();
        int[] seen = {0, 0};
        Consumer<LivingIncomingDamageEvent> observer = event -> {
            if (event.getEntity() == outerTarget) {
                h.assertTrue(event.getSource() == outer && AcademyDamageHelper.isAbilityDamageInProgress(),
                        "Public ability source identity changed before incoming event");
                seen[0]++;
                h.assertTrue(AcademyDamageHelper.hurtNonAbility(innerTarget, inner, 2),
                        "Nested ordinary hurt was rejected");
                h.assertTrue(AcademyDamageHelper.isAbilityDamageInProgress(),
                        "Nested ordinary hurt did not restore outer ability scope");
            } else if (event.getEntity() == innerTarget) {
                h.assertTrue(event.getSource() == inner && !AcademyDamageHelper.isAbilityDamageInProgress(),
                        "Ordinary nested source inherited ability scope or changed identity");
                seen[1]++;
            }
        };
        NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, observer);
        try {
            h.assertTrue(AcademyDamageHelper.hurt(caster, outerTarget, outer, 4), "Real ability hurt failed");
            h.assertTrue(seen[0] == 1 && seen[1] == 1 && outerTarget.getHealth() == 6
                            && innerTarget.getHealth() == 8 && !AcademyDamageHelper.isAbilityDamageInProgress(),
                    "Nested accepted damage or final scope differs from the explicit calls");
            h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(observer); session.close(); }
    }

    @GameTest(template = "empty", batch = "native_skill_sources")
    public static void realDirectedShockDoesNotBorrowHeldBreach(GameTestHelper h) {
        var session = session(h);
        ServerPlayer caster = session.player();
        var data = ability(caster, AbilityCategory.VECMANIP);
        data.learnSkill("dir_shock");
        var target = h.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 4));
        target.setNoAi(true); target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        // Native armour calculation is exercised without random armour drops/enchantments.
        target.getAttribute(Attributes.ARMOR).setBaseValue(20);
        target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
        var mace = new ItemStack(Items.MACE);
        mace.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.BREACH), 4);
        int[] accepted = {0};
        Consumer<LivingIncomingDamageEvent> observer = event -> {
            if (event.getEntity() != target) return;
            h.assertTrue(event.getSource() instanceof SkillDamageSource
                            && event.getSource().is(SkillDamageSource.ABILITY)
                            && event.getSource().getWeaponItem() == null,
                    "Directed Shock did not use its production skill source");
            accepted[0]++;
        };
        NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, observer);
        try {
            float[] bare = directedShock(caster, target, data, ItemStack.EMPTY);
            float[] held = directedShock(caster, target, data, mace);
            h.assertTrue(accepted[0] == 2 && bare[0] > 0 && near(bare[0], held[0])
                            && bare[1] > 0 && near(bare[1], held[1]) && near(bare[2], held[2]),
                    "Held Breach changed real skill damage/resource cost: bare=" + java.util.Arrays.toString(bare)
                            + " held=" + java.util.Arrays.toString(held));
            h.assertTrue(caster.getMainHandItem() == mace && mace.getDamageValue() == 0,
                    "Casting wore or replaced the unrelated held mace");
            h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(observer); session.close(); }
    }

    @GameTest(template = "empty", batch = "native_skill_sources", timeoutTicks = 120)
    public static void airflowMitigatesBothNativeAndAbilityDrowning(GameTestHelper h) {
        var session = session(h);
        ServerPlayer caster = session.player();
        PlayerAbilityData data = ability(caster, AbilityCategory.AEROHAND);
        data.learnSkill("airflow"); data.learnSkill("ascending_air");
        h.runAfterDelay(65, () -> {
            try {
                float[] nativeResult = drowning(h, caster, data, caster.damageSources().drown());
                float[] skillResult = drowning(h, caster, data, SkillDamageSource.drowning(caster, true));
                h.assertTrue(nativeResult[0] > 0 && nativeResult[0] < 8 && nativeResult[1] > 0
                                && near(nativeResult[0], skillResult[0]) && near(nativeResult[1], skillResult[1]),
                        "IS_DROWNING skill profile changed Airflow mitigation/payment: native="
                                + java.util.Arrays.toString(nativeResult) + " skill=" + java.util.Arrays.toString(skillResult));
                h.succeed();
            } finally { session.close(); }
        });
    }

    @GameTest(template = "empty", batch = "native_skill_pvp", timeoutTicks = 160)
    public static void realVacuumPulseHonorsPvpTeamSelfDamageAndKillCredit(GameTestHelper h) {
        var casterSession = session(h);
        var victimSession = session(h);
        ServerPlayer caster = casterSession.player(), victim = victimSession.player();
        PlayerAbilityData data = ability(caster, AbilityCategory.AEROHAND);
        data.learnSkill("aero_separator");
        caster.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        victim.getData(AcademyAttachments.PLAYER_ABILITY).setAbilityActive(false);
        var weapon = new ItemStack(Items.MACE);
        weapon.set(DataComponents.CUSTOM_NAME, Component.literal("UNRELATED_NAMED_WEAPON"));
        caster.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        var server = h.getLevel().getServer();
        boolean oldPvp = server.isPvpAllowed(), oldAcademyPvp = ACConfig.Server.PVP_ENABLED.get();
        var scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam("vacuum" + UUID.randomUUID().toString().substring(0, 8));
        cleanup(h, () -> {
            server.setPvpAllowed(oldPvp); ACConfig.Server.PVP_ENABLED.set(oldAcademyPvp);
            scoreboard.removePlayerTeam(team); casterSession.close(); victimSession.close();
        });
        h.runAfterDelay(65, () -> vacuumStage(h, caster, victim, data, team, 0));
    }

    private static void vacuumStage(GameTestHelper h, ServerPlayer caster, ServerPlayer victim,
                                    PlayerAbilityData data, PlayerTeam team, int stage) {
        var server = h.getLevel().getServer();
        server.setPvpAllowed(stage != 0);
        ACConfig.Server.PVP_ENABLED.set(stage != 1);
        if (stage == 2) {
            server.getScoreboard().addPlayerToTeam(caster.getScoreboardName(), team);
            server.getScoreboard().addPlayerToTeam(victim.getScoreboardName(), team);
            team.setAllowFriendlyFire(false);
        } else if (stage == 3) team.setAllowFriendlyFire(true);
        caster.setHealth(200); caster.invulnerableTime = 0; caster.setAirSupply(300);
        victim.setHealth(stage == 3 ? 20 : 200); victim.invulnerableTime = 0; victim.setAirSupply(300);
        caster.setDeltaMovement(Vec3.ZERO); victim.setDeltaMovement(Vec3.ZERO);
        victim.setPos(caster.position().add(1, 0, 0));
        new AeroSeparatorEffect().execute(caster, data);
        h.runAfterDelay(8, () -> {
            h.assertTrue(caster.getHealth() < 200 && caster.isAlive(),
                    "Actual delayed vacuum lost ownerless self damage at stage " + stage);
            if (stage < 3) {
                h.assertTrue(victim.getHealth() == 200 && victim.getKillCredit() == null,
                        "Actual vacuum bypassed native PvP, Academy PvP or team rejection at stage " + stage);
                vacuumStage(h, caster, victim, data, team, stage + 1);
            } else {
                DamageSource source = victim.getLastDamageSource();
                h.assertTrue(victim.isDeadOrDying() && victim.getKillCredit() == caster
                                && source instanceof SkillDamageSource && source.getEntity() == caster
                                && source.is(DamageTypeTags.IS_DROWNING),
                        "Accepted actual vacuum lost caster kill credit or drowning classification");
                assertMessage(h, source, victim, true);
                h.succeed();
            }
        });
    }

    private static float[] directedShock(ServerPlayer caster, LivingEntity target, PlayerAbilityData data, ItemStack held) {
        caster.setItemSlot(EquipmentSlot.MAINHAND, held);
        caster.setYRot(0); caster.setXRot(0);
        target.setPos(caster.position().add(0, 0, 2)); target.setDeltaMovement(Vec3.ZERO);
        target.setHealth(100); target.invulnerableTime = 0;
        data.setProficiency("dir_shock", .5F); data.setCurrentCp(data.getMaxCp()); data.setCurrentOverload(0);
        float cp = data.getCurrentCp(), overload = data.getCurrentOverload();
        new DirShockEffect().onChargingRelease(caster, data, 7);
        return new float[]{100 - target.getHealth(), cp - data.getCurrentCp(), data.getCurrentOverload() - overload};
    }

    private static float[] drowning(GameTestHelper h, ServerPlayer player, PlayerAbilityData data, DamageSource source) {
        player.setHealth(player.getMaxHealth()); player.invulnerableTime = 0;
        data.setCurrentCp(data.getMaxCp()); data.setCurrentOverload(0);
        data.setProficiency("ascending_air", .5F); data.setProficiency("airflow", .5F);
        float health = player.getHealth(), cp = data.getCurrentCp();
        h.assertTrue(AcademyDamageHelper.hurtSelf(player, player, source, 8), "Drowning mitigation test did not hurt");
        return new float[]{health - player.getHealth(), cp - data.getCurrentCp()};
    }

    private static PlayerAbilityData ability(ServerPlayer player, AbilityCategory category) {
        var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(category); data.setPlayerLevel(5); data.setAbilityActive(true);
        data.setCurrentCp(data.getMaxCp()); data.setCurrentOverload(0);
        return data;
    }

    private static ResourceKey<DamageType> academyType(String id) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("academy", id));
    }

    private static void assertMessage(GameTestHelper h, DamageSource source, LivingEntity victim, boolean owner) {
        Component message = source.getLocalizedDeathMessage(victim);
        h.assertTrue(message.getContents() instanceof TranslatableContents, "Death message lost translation key");
        var contents = (TranslatableContents) message.getContents();
        String expected = "death.attack.academy." + source.typeHolder().unwrapKey().orElseThrow().location().getPath()
                + (owner ? ".player" : "");
        h.assertTrue(contents.getKey().equals(expected) && contents.getArgs().length == (owner ? 2 : 1)
                        && contents.getArgs()[0].equals(victim.getDisplayName())
                        && (!owner || contents.getArgs()[1].equals(source.getEntity().getDisplayName()))
                        && !message.getString().contains("UNRELATED_NAMED_WEAPON"),
                "Death message borrowed weapon name or lost victim/caster: " + message);
    }

    private static RecheckPlayers.Session session(GameTestHelper h) {
        var session = RecheckPlayers.connect(h);
        cleanup(h, session::close);
        return session;
    }

    private static void cleanup(GameTestHelper h, Runnable action) {
        h.testInfo.addListener(new GameTestListener() {
            private boolean closed;
            private void close() { if (!closed) { closed = true; action.run(); } }
            @Override public void testStructureLoaded(GameTestInfo info) {}
            @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
            @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            @Override public void testAddedForRerun(GameTestInfo previous, GameTestInfo next, GameTestRunner runner) { close(); }
        });
    }

    private static boolean near(float a, float b) { return Math.abs(a - b) < .001F; }
}
