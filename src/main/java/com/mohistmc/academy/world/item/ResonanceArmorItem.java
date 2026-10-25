package com.mohistmc.academy.world.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Resonance armour receives its ability-damage reduction in ExtraEquipmentHandler. */
public final class ResonanceArmorItem extends ArmorItem {
    public ResonanceArmorItem(Type type) {
        super(ExtraArmorMaterials.RESONANCE, type, new Item.Properties().stacksTo(1));
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        // 1.21.1's default requires MAX_DAMAGE. Legacy resonance armour can be
        // enchanted (material value 25), but its damageArmor callback never wears it.
        // Keep the existing non-damageable stack; vanilla tags select the enchantments.
        return stack.getCount() == 1;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.academy.resonance_armor.desc")
                .withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
