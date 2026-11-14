package com.mohistmc.academy.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import java.util.Optional;

/**
 * Imaginary Fusor recipe. Optional {@code "time"} (ticks) and {@code "energy"} (IF per tick)
 * override the machine's {@code machines.imagFusor.processTime / energyPerTick} config.
 */
public record ImagFusorRecipe(Ingredient input, int inputCount, int phaseLiquid, ItemStack output,
                              Optional<Integer> time, Optional<Double> energy)
        implements Recipe<ImagFusorRecipeInput> {
    public static final int MAX_TIME = 72_000;
    public static final double MAX_ENERGY_PER_TICK = 1_000_000.0;

    public ImagFusorRecipe {
        if (inputCount <= 0) throw new IllegalArgumentException("inputCount must be positive");
        if (phaseLiquid <= 0) throw new IllegalArgumentException("phase_liquid must be positive");
        output = output.copy();
        time = time == null ? Optional.empty() : time;
        energy = energy == null ? Optional.empty() : energy;
        if (time.isPresent() && (time.get() < 1 || time.get() > MAX_TIME))
            throw new IllegalArgumentException("time must be in 1.." + MAX_TIME);
        if (energy.isPresent() && !(energy.get() >= 0 && energy.get() <= MAX_ENERGY_PER_TICK))
            throw new IllegalArgumentException("energy must be in 0.." + MAX_ENERGY_PER_TICK);
    }

    public ImagFusorRecipe(Ingredient input, int inputCount, int phaseLiquid, ItemStack output) {
        this(input, inputCount, phaseLiquid, output, Optional.empty(), Optional.empty());
    }

    public ImagFusorRecipe(Ingredient input, int phaseLiquid, ItemStack output) {
        this(input, 1, phaseLiquid, output);
    }

    /** Ticks per craft: the recipe's {@code "time"} or {@code machines.imagFusor.processTime}. */
    public int processTime() {
        return time.orElseGet(com.mohistmc.academy.config.MachineConfig.FUSOR_PROCESS_TIME::get);
    }

    /** IF per tick: the recipe's {@code "energy"} or {@code machines.imagFusor.energyPerTick}. */
    public double energyPerTick() {
        return energy.orElseGet(com.mohistmc.academy.config.MachineConfig.FUSOR_ENERGY_PER_TICK::get);
    }

    @Override public boolean matches(ImagFusorRecipeInput in, Level level) {
        return in.input().getCount() >= inputCount && input.test(in.input());
    }
    @Override public ItemStack assemble(ImagFusorRecipeInput in, HolderLookup.Provider provider) { return output.copy(); }
    @Override public boolean canCraftInDimensions(int w, int h) { return true; }
    @Override public ItemStack getResultItem(HolderLookup.Provider provider) { return output.copy(); }
    @Override public RecipeSerializer<?> getSerializer() { return AcademyRecipeSerializers.IMAG_FUSOR.get(); }
    @Override public RecipeType<?> getType() { return AcademyRecipeTypes.IMAG_FUSOR.get(); }
    @Override public boolean isSpecial() { return true; }
    @Override public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, input);
    }

    public static final class Serializer implements RecipeSerializer<ImagFusorRecipe> {
        private static final MapCodec<ImagFusorRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Ingredient.CODEC.fieldOf("input").forGetter(ImagFusorRecipe::input),
                com.mojang.serialization.Codec.INT.optionalFieldOf("inputCount", 1)
                        .forGetter(ImagFusorRecipe::inputCount),
                // MohistMC upstream 00e9cf09 uses phaseLiquid. The optional
                // snake-case field is decode-only compatibility for rebuild
                // 0.0.4-0.0.10 datapacks.
                com.mojang.serialization.Codec.INT.optionalFieldOf("phaseLiquid")
                        .forGetter(recipe -> Optional.of(recipe.phaseLiquid())),
                com.mojang.serialization.Codec.INT.optionalFieldOf("phase_liquid")
                        .forGetter(recipe -> Optional.empty()),
                ItemStack.CODEC.fieldOf("output").forGetter(ImagFusorRecipe::output),
                // Optional per-recipe processing; absent = machine config value.
                com.mojang.serialization.Codec.intRange(1, MAX_TIME).optionalFieldOf("time")
                        .forGetter(ImagFusorRecipe::time),
                com.mojang.serialization.Codec.doubleRange(0.0, MAX_ENERGY_PER_TICK).optionalFieldOf("energy")
                        .forGetter(ImagFusorRecipe::energy)
        ).apply(i, (input, inputCount, currentAmount, legacyAmount, output, time, energy) -> new ImagFusorRecipe(
                input,
                inputCount,
                currentAmount.orElseGet(() -> legacyAmount.orElseThrow(
                        () -> new IllegalArgumentException("phaseLiquid is required"))),
                output,
                time,
                energy)));
        private static final StreamCodec<RegistryFriendlyByteBuf, ImagFusorRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, ImagFusorRecipe::input,
                ByteBufCodecs.VAR_INT, ImagFusorRecipe::inputCount,
                ByteBufCodecs.VAR_INT, ImagFusorRecipe::phaseLiquid,
                ItemStack.STREAM_CODEC, ImagFusorRecipe::output,
                ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), ImagFusorRecipe::time,
                ByteBufCodecs.optional(ByteBufCodecs.DOUBLE), ImagFusorRecipe::energy,
                ImagFusorRecipe::new);
        @Override public MapCodec<ImagFusorRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, ImagFusorRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
