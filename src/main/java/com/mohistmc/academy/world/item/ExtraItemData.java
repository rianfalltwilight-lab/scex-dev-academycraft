package com.mohistmc.academy.world.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Native persistent values plus narrowly scoped readers for pre-0.0.31 item saves. */
public final class ExtraItemData {
    private static final String ACTIVE = "academy_extra_active";
    private static final String TELEPORT_SET = "academy_extra_teleport_set";
    private static final String TELEPORT_DIMENSION = "academy_extra_teleport_dimension";
    private static final String TELEPORT_X = "academy_extra_teleport_x";
    private static final String TELEPORT_Y = "academy_extra_teleport_y";
    private static final String TELEPORT_Z = "academy_extra_teleport_z";
    private static final String HARVEST_POS = "academy_extra_harvest_pos";
    private static final String HARVEST_PROGRESS = "academy_extra_harvest_progress";
    private static final String HARVEST_STATE = "academy_extra_harvest_state";
    private static final String LAST_USE = "academy_extra_last_use";
    private static final String ENERGY = "academy_extra_energy";

    private ExtraItemData() {}

    private static CompoundTag tag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void update(ItemStack stack, java.util.function.Consumer<CompoundTag> change) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, change);
    }

    public static boolean isActive(ItemStack stack) {
        Boolean component = stack.get(AcademyDataComponents.ACTIVE.get());
        if (component != null) return component;
        return tag(stack).getBoolean(ACTIVE);
    }

    public static void setActive(ItemStack stack, boolean active) {
        stack.set(AcademyDataComponents.ACTIVE.get(), active);
        removeLegacyKeys(stack, ACTIVE);
    }

    public static void toggleActive(ItemStack stack) {
        setActive(stack, !isActive(stack));
    }

    public static void setTeleport(ItemStack stack, ResourceLocation dimension,
                                   double x, double y, double z) {
        stack.set(AcademyDataComponents.TELEPORT_TARGET.get(), new TeleportTarget(dimension, x, y, z));
        removeLegacyTeleport(stack);
    }

    public static void clearTeleport(ItemStack stack) {
        stack.remove(AcademyDataComponents.TELEPORT_TARGET.get());
        removeLegacyTeleport(stack);
    }

    public static TeleportTarget teleport(ItemStack stack) {
        TeleportTarget target = stack.get(AcademyDataComponents.TELEPORT_TARGET.get());
        if (target != null) return target;
        CompoundTag nbt = tag(stack);
        if (!nbt.getBoolean(TELEPORT_SET)) return null;
        ResourceLocation dimension = ResourceLocation.tryParse(nbt.getString(TELEPORT_DIMENSION));
        if (dimension == null) return null;
        double x = nbt.getDouble(TELEPORT_X), y = nbt.getDouble(TELEPORT_Y), z = nbt.getDouble(TELEPORT_Z);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;
        return new TeleportTarget(dimension, x, y, z);
    }

    public static void migrateTeleport(ItemStack stack) {
        TeleportTarget target = teleport(stack);
        if (target != null) stack.set(AcademyDataComponents.TELEPORT_TARGET.get(), target);
        removeLegacyTeleport(stack);
    }

    private static void removeLegacyTeleport(ItemStack stack) {
        removeLegacyKeys(stack, TELEPORT_SET, TELEPORT_DIMENSION, TELEPORT_X, TELEPORT_Y, TELEPORT_Z);
    }

    /** Mining progress belongs to a live server action, never to a copied or saved stack. */
    public static void clearLegacyHarvest(ItemStack stack) {
        removeLegacyKeys(stack, HARVEST_POS, HARVEST_PROGRESS, HARVEST_STATE);
    }

    private static void removeLegacyKeys(ItemStack stack, String... keys) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return;
        CompoundTag nbt = custom.copyTag();
        boolean changed = false;
        for (String key : keys) {
            if (nbt.contains(key)) { nbt.remove(key); changed = true; }
        }
        if (changed) {
            if (nbt.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
            else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        }
    }

    public static long lastUse(ItemStack stack) {
        Long component = stack.get(AcademyDataComponents.LAST_USE.get());
        if (component != null) return component;
        CompoundTag nbt = tag(stack);
        return nbt.contains(LAST_USE) ? nbt.getLong(LAST_USE) : Long.MIN_VALUE;
    }

    public static void setLastUse(ItemStack stack, long gameTime) {
        stack.set(AcademyDataComponents.LAST_USE.get(), gameTime);
        removeLegacyKeys(stack, LAST_USE);
    }

    /** Reads do not mutate stacks, including simulated capability calls and client tooltips. */
    public static int energy(ItemStack stack, int capacity) {
        Integer component = stack.get(AcademyDataComponents.ENERGY.get());
        if (component != null) return Math.clamp(component, 0, capacity);
        CompoundTag nbt = tag(stack);
        if (nbt.contains(ENERGY)) return Math.clamp(nbt.getInt(ENERGY), 0, capacity);
        if (stack.has(DataComponents.DAMAGE)) {
            return (int) Math.clamp((long) capacity - stack.getOrDefault(DataComponents.DAMAGE, 0), 0L, capacity);
        }
        // The legacy IF NBT manager treated an absent tag as an empty item.
        return 0;
    }

    public static void setEnergy(ItemStack stack, int energy, int capacity) {
        stack.set(AcademyDataComponents.ENERGY.get(), Math.clamp(energy, 0, capacity));
        removeLegacyKeys(stack, ENERGY);
        // Neither old durability encoding nor an explicitly saved maximum may become equipment wear.
        stack.remove(DataComponents.DAMAGE);
        stack.remove(DataComponents.MAX_DAMAGE);
    }

    public static void migrateEnergy(ItemStack stack, int capacity) {
        setEnergy(stack, energy(stack, capacity), capacity);
    }

    public record TeleportTarget(ResourceLocation dimension, double x, double y, double z) {
        private static final Codec<Double> FINITE_DOUBLE = Codec.DOUBLE.validate(value ->
                Double.isFinite(value) ? DataResult.success(value) : DataResult.error(() -> "Non-finite teleport coordinate"));
        public static final Codec<TeleportTarget> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("dimension").forGetter(TeleportTarget::dimension),
                FINITE_DOUBLE.fieldOf("x").forGetter(TeleportTarget::x),
                FINITE_DOUBLE.fieldOf("y").forGetter(TeleportTarget::y),
                FINITE_DOUBLE.fieldOf("z").forGetter(TeleportTarget::z)
        ).apply(instance, TeleportTarget::new));
        public static final StreamCodec<ByteBuf, TeleportTarget> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, TeleportTarget::dimension,
                ByteBufCodecs.DOUBLE, TeleportTarget::x,
                ByteBufCodecs.DOUBLE, TeleportTarget::y,
                ByteBufCodecs.DOUBLE, TeleportTarget::z,
                TeleportTarget::new);

        public TeleportTarget {
            java.util.Objects.requireNonNull(dimension);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Non-finite teleport coordinate");
        }
    }
}
