package com.mohistmc.academy.skill;

import com.mohistmc.academy.AcademyCraft;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;


public class AcademyAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AcademyCraft.MODID);

    public static final Supplier<AttachmentType<PlayerAbilityData>> PLAYER_ABILITY =
            ATTACHMENT_TYPES.register("player_ability",
                    () -> AttachmentType.builder(PlayerAbilityData::new)
                            .serialize(new PlayerAbilityDataCodec())
                            .copyOnDeath()
                            .build());

    /** Skills a non-player entity knows; see {@link com.mohistmc.academy.api.mob.MobAbilityData}. */
    public static final Supplier<AttachmentType<com.mohistmc.academy.api.mob.MobAbilityData>> MOB_ABILITY =
            ATTACHMENT_TYPES.register("mob_ability",
                    () -> AttachmentType.builder(com.mohistmc.academy.api.mob.MobAbilityData::new)
                            .serialize(com.mohistmc.academy.api.mob.MobAbilityData.CODEC)
                            .build());
}
