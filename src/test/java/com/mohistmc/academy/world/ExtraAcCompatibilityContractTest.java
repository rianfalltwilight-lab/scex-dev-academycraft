package com.mohistmc.academy.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Inventory and skill-tree contract audited against ExtraAcC master@d66a190e. */
class ExtraAcCompatibilityContractTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final List<String> ITEM_IDS = List.of(
            "optical_chip", "lasor_component", "etched_cobblestone", "ray_twister",
            "energy_unit_group", "electricalibur", "avalon", "cp_potion", "lasor_gun",
            "air_jet", "teleporter", "paper_plane", "drop_item_magnet",
            "reso_helmet", "reso_chestplate", "reso_leggings", "reso_boots",
            "imag_helmet", "imag_chestplate", "imag_leggings", "imag_boots",
            "paper_helmet", "paper_chestplate", "paper_leggings", "paper_boots");

    private static String read(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/com/mohistmc/academy").resolve(path));
    }

    @Test
    void allTwentyFiveAddonItemsHaveRegistryModelRecipeAndBilingualNames() throws Exception {
        String registry = read("world/AcademyItems.java");
        String en = Files.readString(RESOURCES.resolve("assets/academy/lang/en_us.json"));
        String zh = Files.readString(RESOURCES.resolve("assets/academy/lang/zh_cn.json"));
        for (String id : ITEM_IDS) {
            assertTrue(registry.contains("ITEMS.register(\"" + id + "\""), id + " registry");
            assertTrue(Files.isRegularFile(RESOURCES.resolve("assets/academy/models/item/" + id + ".json")),
                    id + " model");
            assertTrue(Files.isRegularFile(RESOURCES.resolve("data/academy/recipe/extra_" + id + ".json")),
                    id + " recipe");
            assertTrue(en.contains("\"item.academy." + id + "\""), id + " en_us");
            assertTrue(zh.contains("\"item.academy." + id + "\""), id + " zh_cn");
        }
        assertEquals(25, ITEM_IDS.size());
        assertTrue(Files.readString(RESOURCES.resolve("data/academy/recipe/extra_cp_potion.json"))
                .contains("\"type\":\"academy:imag_fusor\""));
        assertTrue(Files.readString(RESOURCES.resolve("data/academy/recipe/extra_etched_cobblestone.json"))
                .contains("\"type\":\"academy:metal_forming\""));
        assertTrue(Files.readString(RESOURCES.resolve("data/academy/recipe/extra_etched_cobblestone.json"))
                .contains("\"mode\":\"etch\""));
        for (String recipe : List.of("extra_fuse_constraint_ingot.json",
                "extra_fuse_reso_crystal.json", "extra_fuse_imag_silicon.json",
                "extra_fuse_crystal_low.json")) {
            assertTrue(Files.isRegularFile(RESOURCES.resolve("data/academy/recipe/" + recipe)), recipe);
        }
        assertTrue(Files.readString(RESOURCES.resolve("data/academy/recipe/extra_fuse_crystal_low.json"))
                .contains("\"inputCount\":2"));
    }

    @Test
    void dynamicAddonModelsAndArmorSemanticsCannotRegressToStaticVanillaPlaceholders() throws Exception {
        String client = read("listener/ClientModListener.java");
        for (String property : List.of("active", "bound", "charge")) {
            assertTrue(client.contains("AcademyCraft.MODID, \"" + property + "\""), property);
        }
        for (String model : List.of("ray_twister_active", "teleporter_bound",
                "energy_unit_group_1", "energy_unit_group_2", "energy_unit_group_3",
                "energy_unit_group_4", "energy_unit_group_5")) {
            assertTrue(Files.isRegularFile(RESOURCES.resolve("assets/academy/models/item/" + model + ".json")), model);
        }
        String resonance = read("world/item/ResonanceArmorItem.java");
        String imaginary = read("world/item/ImagEnergyArmorItem.java");
        String paper = read("world/item/PaperArmorItem.java");
        assertFalse(resonance.contains("ArmorMaterials.IRON"));
        assertFalse(imaginary.contains("ArmorMaterials.IRON"));
        assertFalse(paper.contains("ArmorMaterials.LEATHER"));
        assertTrue(imaginary.contains("ExtraItemData.energy"));
        String energy = read("world/item/ExtraEnergyItem.java");
        assertTrue(energy.contains("ExtraItemData.energy(stack, capacity)"),
                "IF devices must use the independent native charge component");
        assertFalse(energy.contains(".durability("), "IF must not become repairable equipment wear");
        assertTrue(read("world/AcademyItems.java").contains(
                "energyItem.setEnergy(full, energyItem.getMaxEnergyStored(full))"),
                "creative inventory must still expose an explicit full variant");
    }

    @Test
    void legacyToggleContextsOwnTerminationCooldownAndTransmissionStaysActive() throws Exception {
        String packet = read("network/UseSkillPacket.java");
        assertTrue(packet.contains("!effect.managesOwnCooldown()"));
        for (String path : List.of(
                "skill/ability/aerohand/AeroToggleEffect.java",
                "skill/ability/telekinesis/TelekinesisToggleEffect.java",
                "skill/ability/telekinesis/CruiseBombEffect.java",
                "skill/ability/telekinesis/PsychoTransmissionEffect.java")) {
            assertTrue(read(path).contains("managesOwnCooldown() { return true; }"), path);
        }
        String transmission = read("skill/ability/telekinesis/PsychoTransmissionEffect.java");
        assertTrue(transmission.contains("PlayerTickEvent.Post"));
        assertTrue(transmission.contains("Map<UUID, Session> SESSIONS"));
        assertTrue(transmission.contains("0.5F, 0"));
        assertTrue(transmission.contains("2.5F"));
    }

    @Test
    void damagingRaysShareTheAuditedCollisionBoundary() throws Exception {
        // This is a wiring contract only. ExtraAdversarialGameTests exercises the
        // actual glass-pane and overlapping-box counterexamples on a real server.
        for (String path : List.of("skill/ability/telekinesis/PsychoSlamEffect.java")) {
            String source = read(path);
            assertTrue(source.contains("SkillRaycast.trace(") || source.contains("SkillRaycast.traceEntities("), path);
            assertFalse(source.contains("getBoundingBox().inflate("), path);
        }
        // Paper Drill is the exception: ExtraAcC's EntityPaperDrill hit every living entity in
        // the eye..eye+5*look box without a ray trace, so walls never shielded a target
        // (ExtraAcRestoreGameTests.paperDrillHitsThroughWallsLikeTheLegacyBox).
        String drill = read("skill/ability/telekinesis/PaperDrillEffect.java");
        assertFalse(drill.contains("SkillRaycast"));
        assertTrue(drill.contains("new AABB(from, to)"));
        assertTrue(drill.contains("AcademyDamageHelper.allowsTarget(entity)"));
        String boundary = read("skill/ability/SkillRaycast.java");
        assertTrue(boundary.contains("ClipContext.Block.COLLIDER"));
        assertTrue(boundary.contains("box.clip(from, end)"));
        assertTrue(boundary.contains("Hit::distanceSquared"));
        assertTrue(read("world/entity/AeroProjectileEntity.java").contains("LoadedRaycast.clip"));
        assertTrue(read("skill/ability/telekinesis/PsychoTransmissionEffect.java").contains("LoadedRaycast.clip"));
    }
    @Test
    void offenseArmourRestoresItsLegacyKnockbackResistanceLifecycle() throws Exception {
        String runtime = read("skill/ability/aerohand/AeroPassiveRuntime.java");
        assertTrue(runtime.contains("Attributes.KNOCKBACK_RESISTANCE"));
        assertTrue(runtime.contains("0.9, AttributeModifier.Operation.ADD_VALUE"));
        assertTrue(runtime.contains("removeArmourKnockbackResistance(player)"));
        // ExtraAcC never pushed the attacker back; its s_terminate always set the 40-tick cooldown.
        assertFalse(runtime.contains("attacker.push("));
        assertFalse(runtime.contains("boolean cooldown"));
    }

    @Test
    void resonanceArmourKeepsItsLegacyArmourArrayAndPaperDrillIsAToggle() throws Exception {
        // ItemResoArmor: ArmorVars {2, 5, 6, 2} (boots, legs, chest, helmet) with toughness 1.
        assertTrue(read("world/item/ExtraArmorMaterials.java")
                .contains("material(\"extraacc/reso\", defenses(2, 5, 6, 2), 25, 1.0F)"));
        String drill = read("skill/ability/telekinesis/PaperDrillEffect.java");
        assertTrue(drill.contains("getSessionTimeoutTicks(PlayerAbilityData data)"));
        assertTrue(drill.contains("return Integer.MAX_VALUE;"));
        assertTrue(drill.contains("public boolean canRelease("));
    }

    @Test
    void addonCooldownsTruncateLikeTheLegacyIntCast() throws Exception {
        // ExtraAcC: ctx.setCooldown((int) MathUtils.lerpf(a, b, exp)); Math.round could add one tick.
        assertTrue(read("skill/ability/aerohand/VolcanicBallEffect.java")
                .contains("return (int) lerpf(20, 5, Math.clamp(p, 0, 1));"));
        for (String path : List.of("AeroSeparatorEffect", "AirBladeEffect", "AirCoolingEffect", "AirJetEffect",
                "AirWallEffect", "BomberLanceEffect", "StormCoreEffect", "VolcanicBallEffect")) {
            assertFalse(read("skill/ability/aerohand/" + path + ".java").contains("Math.round(l")
                    || read("skill/ability/aerohand/" + path + ".java").contains("return Math.round("), path);
        }
        for (String path : List.of("OverloadThinkingEffect", "PsychoNeedlingEffect", "PsychoSlamEffect",
                "PsychoThrowingEffect")) {
            assertFalse(read("skill/ability/telekinesis/" + path + ".java").contains("return Math.round("), path);
        }
    }

    @Test
    void formerlyPassiveSustainedSkillsAreSelectableActiveEffects() throws Exception {
        String registry = read("skill/SkillRegistry.java");
        for (String id : List.of("offense_armour", "flying", "psycho_harden", "liquid_shadow")) {
            int start = registry.indexOf("new Skill.Builder(\"" + id + "\"");
            assertTrue(start >= 0, id);
            String definition = registry.substring(start, Math.min(start + 260, registry.length()));
            assertFalse(definition.contains(".type(SkillType.PASSIVE)"), id + " must be key-assignable");
            assertTrue(registry.contains("ToggleEffect(\"" + id + "\")"), id + " effect");
        }
    }

    @Test
    void addonSkillTreesRetainAuditedLevelsPositionsAndParentEdges() throws Exception {
        String registry = read("skill/SkillRegistry.java");
        for (String signature : List.of(
                "new Skill.Builder(\"volcanic_ball\", cat, 1)\n                .position(20, 25)",
                "new Skill.Builder(\"flying\", cat, 4)\n                .position(165, 85)\n                .prereq(\"air_jet\", 0.5f)",
                "new Skill.Builder(\"aero_separator\", cat, 5)\n                .position(200, 25)\n                .prereq(\"bomber_lance\", 0.5f)",
                "new Skill.Builder(\"insulation\", cat, 1)\n                .position(70, 70)",
                "new Skill.Builder(\"psycho_harden\", cat, 4)\n                .position(165, 60)\n                .prereq(\"overload_thinking\", 0.5f)",
                "new Skill.Builder(\"liquid_shadow\", cat, 5)\n                .position(190, 15)\n                .prereq(\"cruise_bomb\", 0.9f)",
                "new Skill.Builder(\"paper_drill\", cat, 5)\n                .position(205, 80)\n                .prereq(\"perfect_paper\", 0.5f)")) {
            assertTrue(registry.replace("\r\n", "\n").contains(signature), signature);
        }
    }
}
