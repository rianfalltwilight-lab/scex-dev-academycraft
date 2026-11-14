package com.mohistmc.academy.world.menu;

import com.mohistmc.academy.capability.EnergyItemHelper;
import com.mohistmc.academy.skill.AbilityInterferenceRules;
import com.mohistmc.academy.world.AcademyMenus;
import com.mohistmc.academy.world.block.entity.AbilityInterfererBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Legacy layout: one battery slot plus the hotbar; the middle area belongs to the whitelist. */
public final class AbilityInterfererMenu extends AcademyMenu {
    private static final int HOTBAR_SLOTS = 9;
    private final ContainerData data;

    public AbilityInterfererMenu(int windowId, Inventory inventory, FriendlyByteBuf buffer) {
        super(AcademyMenus.ABILITY_INTERFERER_MENU.get(), windowId, inventory, buffer, false);
        for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
            addSlot(new Slot(inventory, slot, INV_X + slot * 18, HOTBAR_Y));
        }

        if (!inventory.player.level().isClientSide()
                && inventory.player.level().getBlockEntity(pos) instanceof AbilityInterfererBlockEntity be) {
            data = new ContainerData() {
                @Override public int get(int index) {
                    return switch (index) {
                        case 0 -> MenuDataWords.low(Math.max(0, be.getEnergyStored()));
                        case 1 -> MenuDataWords.low(Math.max(0, be.getMaxEnergyStored()));
                        case 2 -> be.getRange();
                        case 3 -> be.isEnabled() ? 1 : 0;
                        case 4 -> MenuDataWords.high(Math.max(0, be.getEnergyStored()));
                        case 5 -> MenuDataWords.high(Math.max(0, be.getMaxEnergyStored()));
                        default -> 0;
                    };
                }
                @Override public void set(int index, int value) {}
                @Override public int getCount() { return 6; }
            };
        } else {
            data = new SimpleContainerData(6);
        }
        addDataSlots(data);

        addAcademySlot(new Slot(container, AbilityInterfererBlockEntity.BATTERY_SLOT, 141, 25) {
            @Override public boolean mayPlace(ItemStack stack) {
                return EnergyItemHelper.isEnergyItem(stack);
            }
            @Override public int getMaxStackSize() { return 1; }
            @Override public int getMaxStackSize(ItemStack stack) { return 1; }
        });
    }

    public int getEnergy() { return Math.clamp(MenuDataWords.join(data.get(0), data.get(4)), 0, com.mohistmc.academy.config.MachineConfig.INTERFERER_BUFFER.get()); }
    public int getMaxEnergy() { return Math.max(1, MenuDataWords.join(data.get(1), data.get(5))); }
    public int getRange() { return AbilityInterferenceRules.clampRange(data.get(2)); }
    public boolean isEnabled() { return data.get(3) != 0; }

    @Override protected int getPlayerSlotEnd() { return HOTBAR_SLOTS; }
    @Override protected int getMachineSlotStart() { return HOTBAR_SLOTS; }
    @Override protected int getMachineSlotEnd() { return HOTBAR_SLOTS + 1; }
}
