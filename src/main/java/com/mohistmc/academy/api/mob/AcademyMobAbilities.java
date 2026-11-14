package com.mohistmc.academy.api.mob;

import com.mohistmc.academy.api.event.AbilityEvents;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.config.MobCastConfig;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.Skill;
import com.mohistmc.academy.skill.SkillRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * Public, server-thread API for non-player entities casting AcademyCraft skills (KubeJS, AI goals,
 * commands, other mods).  Players keep using their own ability data and key input.
 */
public final class AcademyMobAbilities {
    private AcademyMobAbilities() {}

    /** How a cast should be performed; {@link #defaults()} uses the caster's stored proficiency. */
    public record CastOptions(@Nullable Float proficiency, @Nullable LivingEntity target, boolean ignoreCooldown) {
        public static CastOptions defaults() { return new CastOptions(null, null, false); }
        public static CastOptions at(@Nullable LivingEntity target) { return new CastOptions(null, target, false); }
        public CastOptions withProficiency(float value) { return new CastOptions(value, target, ignoreCooldown); }
        public CastOptions withTarget(@Nullable LivingEntity value) { return new CastOptions(proficiency, value, ignoreCooldown); }
        public CastOptions ignoringCooldown() { return new CastOptions(proficiency, target, true); }
    }

    /** Why a cast did or did not happen. */
    public enum Result { CAST, FAILED, UNKNOWN_SKILL, UNSUPPORTED, DISABLED, NOT_KNOWN, ON_COOLDOWN, BUSY, CANCELLED, INVALID_CASTER }

    private record Budget(long tick, int used) {}
    private static final Map<ResourceKey<Level>, Budget> BUDGETS = new ConcurrentHashMap<>();

    public static MobAbilityData data(LivingEntity entity) {
        return entity.getData(AcademyAttachments.MOB_ABILITY);
    }

    /** Skill ids whose effect implements {@link MobCastable}. */
    public static List<String> supportedSkills() {
        return SkillRegistry.getAllSkills().stream().filter(s -> s.getEffect() instanceof MobCastable)
                .map(Skill::getId).toList();
    }

    public static boolean isSupported(String skillId) {
        Skill skill = SkillRegistry.getSkill(normalize(skillId));
        return skill != null && skill.getEffect() instanceof MobCastable;
    }

    public static Result cast(LivingEntity caster, String skillId) {
        return cast(caster, skillId, CastOptions.defaults());
    }

    public static Result cast(LivingEntity caster, String rawSkillId, CastOptions options) {
        if (caster == null || caster instanceof Player || !caster.isAlive()
                || !(caster.level() instanceof ServerLevel level)) return Result.INVALID_CASTER;
        String skillId = normalize(rawSkillId);
        Skill skill = SkillRegistry.getSkill(skillId);
        if (skill == null) return Result.UNKNOWN_SKILL;
        if (!(skill.getEffect() instanceof MobCastable castable)) return Result.UNSUPPORTED;
        if (!DynamicSkillRules.enabled(skillId)) return Result.DISABLED;
        MobAbilityData data = data(caster);
        Float stored = data.proficiency(skillId);
        Float chosen = options.proficiency() != null ? options.proficiency() : stored;
        if (chosen == null || !Float.isFinite(chosen)) return Result.NOT_KNOWN;
        float proficiency = Math.clamp(chosen, 0F, 1F);
        long now = level.getGameTime();
        if (!options.ignoreCooldown() && data.isOnCooldown(skillId, now)) return Result.ON_COOLDOWN;
        if (!takeBudget(level, now)) return Result.BUSY;
        LivingEntity target = options.target() != null && options.target().isAlive() ? options.target() : null;
        if (NeoForge.EVENT_BUS.post(new AbilityEvents.MobSkillCast(caster, skill, proficiency, target)).isCanceled())
            return Result.CANCELLED;
        Vec3 eye = caster.getEyePosition();
        Vec3 look = target == null ? caster.getLookAngle()
                : target.getEyePosition().subtract(eye).normalize();
        if (look.lengthSqr() < 1.0e-8) look = caster.getLookAngle();
        boolean done = castable.mobCast(new MobCastContext(caster, level, skillId, proficiency, target, eye, look));
        if (!done) return Result.FAILED;
        int cooldown = Math.max(MobCastConfig.minCooldown(),
                DynamicSkillRules.cooldown(skillId, castable.mobCooldownTicks(proficiency)));
        data.setCooldown(skillId, now, cooldown);
        return Result.CAST;
    }

    private static boolean takeBudget(ServerLevel level, long now) {
        int limit = MobCastConfig.castsPerTick();
        Budget next = BUDGETS.compute(level.dimension(), (key, old) ->
                old == null || old.tick() != now ? new Budget(now, 1) : new Budget(now, old.used() + 1));
        return next.used() <= limit;
    }

    static String normalize(String id) {
        if (id == null) return "";
        return id.startsWith("academy:") ? id.substring("academy:".length()) : id;
    }
}
