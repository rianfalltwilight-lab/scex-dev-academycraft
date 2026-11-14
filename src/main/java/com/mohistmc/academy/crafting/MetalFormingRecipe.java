package com.mohistmc.academy.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * 金属成型机配方 —— 数据驱动（JSON 配方文件），按模式匹配输入输出。
 */
public class MetalFormingRecipe implements Recipe<MetalFormingRecipeInput> {

    private final MetalFormerRecipes.Mode mode;
    private final Ingredient input;
    private final int inputCount;
    private final ItemStack output;
    private final TagKey<Item> outputTag;
    private final int tagOutputCount;
    /** Optional {@code "time"} (ticks); empty = machines.metalFormer.processTime. */
    private final java.util.Optional<Integer> time;
    /** Optional {@code "energy"} (IF per tick); empty = machines.metalFormer.energyPerTick. */
    private final java.util.Optional<Double> energy;

    public static final int MAX_TIME = 72_000;
    public static final double MAX_ENERGY_PER_TICK = 1_000_000.0;

    public MetalFormingRecipe(MetalFormerRecipes.Mode mode, Ingredient input, int inputCount, ItemStack output) {
        this.mode = mode;
        this.input = input;
        this.inputCount = inputCount;
        this.output = output.copy();
        this.outputTag = null;
        this.tagOutputCount = 0;
        this.time = java.util.Optional.empty();
        this.energy = java.util.Optional.empty();
        validate();
    }

    /**
     * Tag-backed output used for the six legacy OreDictionary refining recipes.
     * The first entry retains 1.0.7's "first registered ingot" behaviour while
     * allowing any NeoForge mod that populates the common tags to participate.
     */
    public MetalFormingRecipe(MetalFormerRecipes.Mode mode, Ingredient input, int inputCount,
                              TagKey<Item> outputTag, int outputCount) {
        this.mode = mode;
        this.input = input;
        this.inputCount = inputCount;
        this.output = ItemStack.EMPTY;
        this.outputTag = outputTag;
        this.tagOutputCount = outputCount;
        this.time = java.util.Optional.empty();
        this.energy = java.util.Optional.empty();
        validate();
    }

    private MetalFormingRecipe(MetalFormingRecipe base, java.util.Optional<Integer> time,
                               java.util.Optional<Double> energy) {
        this.mode = base.mode;
        this.input = base.input;
        this.inputCount = base.inputCount;
        this.output = base.output.copy();
        this.outputTag = base.outputTag;
        this.tagOutputCount = base.tagOutputCount;
        this.time = time == null ? java.util.Optional.empty() : time;
        this.energy = energy == null ? java.util.Optional.empty() : energy;
        validate();
    }

    /** Copy with per-recipe processing overrides (empty = use the machine config). */
    public MetalFormingRecipe withProcessing(java.util.Optional<Integer> time, java.util.Optional<Double> energy) {
        return new MetalFormingRecipe(this, time, energy);
    }

    public java.util.Optional<Integer> getTime() { return time; }

    public java.util.Optional<Double> getEnergy() { return energy; }

    /** Ticks per craft: the recipe's {@code "time"} or {@code machines.metalFormer.processTime}. */
    public int processTime() {
        return time.orElseGet(com.mohistmc.academy.config.MachineConfig.FORMER_PROCESS_TIME::get);
    }

    /** IF per tick: the recipe's {@code "energy"} or {@code machines.metalFormer.energyPerTick}. */
    public double energyPerTick() {
        return energy.orElseGet(com.mohistmc.academy.config.MachineConfig.FORMER_ENERGY_PER_TICK::get);
    }

    private void validate() {
        if (inputCount <= 0) throw new IllegalArgumentException("Metal Former input count must be positive");
        if (outputTag == null && output.isEmpty()) {
            throw new IllegalArgumentException("Metal Former recipe requires an item or tag output");
        }
        if (outputTag != null && (tagOutputCount <= 0 || tagOutputCount > 64)) {
            throw new IllegalArgumentException("Metal Former tag output count must be in 1..64");
        }
        if (time != null && time.isPresent() && (time.get() < 1 || time.get() > MAX_TIME)) {
            throw new IllegalArgumentException("Metal Former time must be in 1.." + MAX_TIME);
        }
        if (energy != null && energy.isPresent()
                && !(energy.get() >= 0 && energy.get() <= MAX_ENERGY_PER_TICK)) {
            throw new IllegalArgumentException("Metal Former energy must be in 0.." + MAX_ENERGY_PER_TICK);
        }
    }

    public MetalFormerRecipes.Mode getMode() {
        return mode;
    }

    /** 检查物品是否是该配方的输入（用于输入槽限制） */
    public boolean matchesItem(ItemStack stack) {
        return input.test(stack);
    }

    /** 需要消耗的输入数量 */
    public int getInputCount() {
        return inputCount;
    }

    public ItemStack getOutput() {
        if (outputTag == null) return output.copy();
        return BuiltInRegistries.ITEM.getTag(outputTag)
                .flatMap(set -> set.stream().findFirst())
                .map(holder -> new ItemStack(holder.value(), tagOutputCount))
                .orElse(ItemStack.EMPTY);
    }

    public ItemStack getOutput(HolderLookup.Provider provider) {
        if (outputTag == null) return output.copy();
        return provider.lookup(Registries.ITEM)
                .flatMap(lookup -> lookup.get(outputTag))
                .flatMap(set -> set.stream().findFirst())
                .map(holder -> new ItemStack(holder.value(), tagOutputCount))
                .orElse(ItemStack.EMPTY);
    }

    public TagKey<Item> getOutputTag() {
        return outputTag;
    }

    @Override
    public boolean matches(MetalFormingRecipeInput input_, Level level) {
        return input_.mode() == mode && input_.input().getCount() >= inputCount && input.test(input_.input())
                && !getOutput(level.registryAccess()).isEmpty();
    }

    @Override
    public ItemStack assemble(MetalFormingRecipeInput input_, HolderLookup.Provider provider) {
        return getOutput(provider);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider provider) {
        return getOutput(provider);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return AcademyRecipeSerializers.METAL_FORMING.get();
    }

    @Override
    public RecipeType<?> getType() {
        return AcademyRecipeTypes.METAL_FORMING.get();
    }

    /** 不参与合成台，仅在金属成型机中使用 */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        // NonNullList.of(defaultValue, elements...) treats its first argument as
        // the list's default value. Passing only input therefore produced an
        // empty list, which made recipe viewers (including JEI) see no input.
        return NonNullList.of(Ingredient.EMPTY, input);
    }

    public static class Serializer implements RecipeSerializer<MetalFormingRecipe> {

        private static final MapCodec<MetalFormingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                com.mojang.serialization.Codec.STRING.fieldOf("mode").forGetter(r -> r.mode.name().toLowerCase(java.util.Locale.ROOT)),
                Ingredient.CODEC.fieldOf("input").forGetter(r -> r.input),
                com.mojang.serialization.Codec.INT.optionalFieldOf("count", 1).forGetter(r -> r.inputCount),
                ItemStack.CODEC.optionalFieldOf("output").forGetter(r ->
                        r.outputTag == null ? java.util.Optional.of(r.output) : java.util.Optional.empty()),
                TagKey.codec(Registries.ITEM).optionalFieldOf("output_tag").forGetter(r ->
                        java.util.Optional.ofNullable(r.outputTag)),
                com.mojang.serialization.Codec.INT.optionalFieldOf("output_count", 2).forGetter(r ->
                        r.outputTag == null ? 2 : r.tagOutputCount),
                // Optional per-recipe processing; absent = machine config value.
                com.mojang.serialization.Codec.intRange(1, MAX_TIME).optionalFieldOf("time")
                        .forGetter(r -> r.time),
                com.mojang.serialization.Codec.doubleRange(0.0, MAX_ENERGY_PER_TICK).optionalFieldOf("energy")
                        .forGetter(r -> r.energy)
        ).apply(instance, Serializer::decode));

        private static MetalFormingRecipe decode(String modeName, Ingredient input, int inputCount,
                                                  java.util.Optional<ItemStack> output,
                                                  java.util.Optional<TagKey<Item>> outputTag,
                                                  int outputCount,
                                                  java.util.Optional<Integer> time,
                                                  java.util.Optional<Double> energy) {
            return decode(modeName, input, inputCount, output, outputTag, outputCount).withProcessing(time, energy);
        }

        private static MetalFormingRecipe decode(String modeName, Ingredient input, int inputCount,
                                                  java.util.Optional<ItemStack> output,
                                                  java.util.Optional<TagKey<Item>> outputTag,
                                                  int outputCount) {
            MetalFormerRecipes.Mode mode = MetalFormerRecipes.Mode.valueOf(
                    modeName.toUpperCase(java.util.Locale.ROOT));
            if (output.isPresent() == outputTag.isPresent()) {
                throw new IllegalArgumentException(
                        "Metal Former recipe must define exactly one of output or output_tag");
            }
            return output.<MetalFormingRecipe>map(stack ->
                            new MetalFormingRecipe(mode, input, inputCount, stack))
                    .orElseGet(() -> new MetalFormingRecipe(
                            mode, input, inputCount, outputTag.orElseThrow(), outputCount));
        }

        private static final StreamCodec<io.netty.buffer.ByteBuf, java.util.Optional<Integer>> OPTIONAL_TIME =
                ByteBufCodecs.optional(ByteBufCodecs.VAR_INT);
        private static final StreamCodec<io.netty.buffer.ByteBuf, java.util.Optional<Double>> OPTIONAL_ENERGY =
                ByteBufCodecs.optional(ByteBufCodecs.DOUBLE);

        private static final StreamCodec<RegistryFriendlyByteBuf, MetalFormingRecipe> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public MetalFormingRecipe decode(RegistryFriendlyByteBuf buf) {
                        MetalFormerRecipes.Mode mode = MetalFormerRecipes.Mode.valueOf(
                                ByteBufCodecs.STRING_UTF8.decode(buf));
                        Ingredient input = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
                        int inputCount = ByteBufCodecs.VAR_INT.decode(buf);
                        boolean tagBacked = ByteBufCodecs.BOOL.decode(buf);
                        MetalFormingRecipe base;
                        if (!tagBacked) {
                            base = new MetalFormingRecipe(
                                    mode, input, inputCount, ItemStack.STREAM_CODEC.decode(buf));
                        } else {
                            TagKey<Item> tag = TagKey.create(
                                    Registries.ITEM, ResourceLocation.STREAM_CODEC.decode(buf));
                            int outputCount = ByteBufCodecs.VAR_INT.decode(buf);
                            base = new MetalFormingRecipe(mode, input, inputCount, tag, outputCount);
                        }
                        return base.withProcessing(OPTIONAL_TIME.decode(buf), OPTIONAL_ENERGY.decode(buf));
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buf, MetalFormingRecipe recipe) {
                        ByteBufCodecs.STRING_UTF8.encode(buf, recipe.mode.name());
                        Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.input);
                        ByteBufCodecs.VAR_INT.encode(buf, recipe.inputCount);
                        boolean tagBacked = recipe.outputTag != null;
                        ByteBufCodecs.BOOL.encode(buf, tagBacked);
                        if (!tagBacked) {
                            ItemStack.STREAM_CODEC.encode(buf, recipe.output);
                        } else {
                            ResourceLocation.STREAM_CODEC.encode(buf, recipe.outputTag.location());
                            ByteBufCodecs.VAR_INT.encode(buf, recipe.tagOutputCount);
                        }
                        OPTIONAL_TIME.encode(buf, recipe.time);
                        OPTIONAL_ENERGY.encode(buf, recipe.energy);
                    }
                };

        @Override
        public MapCodec<MetalFormingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, MetalFormingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
