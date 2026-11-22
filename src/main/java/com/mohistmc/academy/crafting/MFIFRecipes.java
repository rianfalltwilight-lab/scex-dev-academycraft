package com.mohistmc.academy.crafting;

import com.mohistmc.academy.world.AcademyItems;
import net.minecraft.world.item.ItemStack;

/**
 * Legacy hardcoded ImagFusor recipe initializer; superseded by datapack {@code data/academy/recipe/*.json}.
 */
@Deprecated(forRemoval = false)
public final class MFIFRecipes {

    private MFIFRecipes() {}

    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        // ==================== ImagFusor Recipes ====================
        ImagFusorRecipes ifr = ImagFusorRecipes.INSTANCE;
        ifr.add(new ItemStack(AcademyItems.CRYSTAL_LOW.get()), 3000,
                new ItemStack(AcademyItems.CRYSTAL_NORMAL.get()));
        ifr.add(new ItemStack(AcademyItems.CRYSTAL_NORMAL.get()), 8000,
                new ItemStack(AcademyItems.CRYSTAL_PURE.get()));
    }
}
