package com.mohistmc.academy.world.item;

import com.mohistmc.academy.capability.IEnergyItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** 100,000 IF powered armour; damage absorption is settled atomically by the event handler. */
public final class ImagEnergyArmorItem extends ArmorItem implements IEnergyItem {
    public static final int MAX_ENERGY = 100_000;
    private static int rate() { return com.mohistmc.academy.config.MachineConfig.scale(200, com.mohistmc.academy.config.MachineConfig.Scale.ITEM); }
    public static int maxEnergy() { return com.mohistmc.academy.config.MachineConfig.scale(MAX_ENERGY, com.mohistmc.academy.config.MachineConfig.Scale.ITEM); }

    public ImagEnergyArmorItem(Type type) {
        super(ExtraArmorMaterials.IMAGINARY, type, new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON)
                .stacksTo(1));
    }

    @Override public boolean isBookEnchantable(ItemStack stack, ItemStack book) { return false; }

    @Override public void verifyComponentsAfterLoad(ItemStack stack) {
        super.verifyComponentsAfterLoad(stack);
        ExtraItemData.migrateEnergy(stack, maxEnergy());
    }

    /** The add-on changes the worn shell at 500 IF; inventory icons remain constant. */
    @Override
    public net.minecraft.resources.ResourceLocation getArmorTexture(ItemStack stack,
            net.minecraft.world.entity.Entity entity, net.minecraft.world.entity.EquipmentSlot slot,
            net.minecraft.world.item.ArmorMaterial.Layer layer, boolean innerModel) {
        String state = getEnergyStored(stack) >= 500 ? "energy" : "noenergy";
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy",
                "textures/models/armor/extraacc/" + state + "_layer_" + (innerModel ? 2 : 1) + ".png");
    }

    @Override public int getEnergyStored(ItemStack stack) {
        return ExtraItemData.energy(stack, maxEnergy());
    }
    @Override public int getMaxEnergyStored(ItemStack stack) { return maxEnergy(); }
    @Override public void setEnergy(ItemStack stack, int energy) {
        ExtraItemData.setEnergy(stack, energy, maxEnergy());
    }
    @Override public int extractEnergy(ItemStack stack, int maximum, boolean simulate) {
        int extracted = Math.min(getEnergyStored(stack), Math.min(Math.max(0, maximum), rate()));
        if (!simulate && extracted > 0) setEnergy(stack, getEnergyStored(stack) - extracted);
        return extracted;
    }
    @Override public int receiveEnergy(ItemStack stack, int maximum, boolean simulate) {
        int received = Math.min(maxEnergy() - getEnergyStored(stack), Math.min(Math.max(0, maximum), rate()));
        if (!simulate && received > 0) setEnergy(stack, getEnergyStored(stack) + received);
        return received;
    }

    int consumeForDamage(ItemStack stack, int amount) {
        int consumed = Math.min(getEnergyStored(stack), Math.max(0, amount));
        if (consumed > 0) setEnergy(stack, getEnergyStored(stack) - consumed);
        return consumed;
    }

    @Override public boolean isBarVisible(ItemStack stack) {
        return getEnergyStored(stack) < maxEnergy();
    }

    @Override public int getBarWidth(ItemStack stack) {
        return Math.round(13F * getEnergyStored(stack) / maxEnergy());
    }

    @Override public int getBarColor(ItemStack stack) { return 0x38C7FF; }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(com.mohistmc.academy.capability.ExternalEnergyConversion.describe(getEnergyStored(stack), maxEnergy()))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.academy.imag_energy_armor.desc")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
