package com.mohistmc.academy.client.gui;

import com.mohistmc.academy.client.KeyInputHandler;
import com.mohistmc.academy.network.SetSkillSlotPacket;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.Skill;
import com.mohistmc.academy.skill.SkillPreset;
import com.mohistmc.academy.skill.SkillRegistry;
import com.mohistmc.academy.skill.SkillType;
import com.mohistmc.academy.utils.RenderUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public class SkillSlotGui extends AcademyScreen {

    private static final int GUI_WIDTH = 280;
    private static final int GUI_HEIGHT = 180;
    private static final int SLOT_SIZE = 48;
    private static final int SLOT_GAP = 12;
    private static final int TAB_WIDTH = 50;
    private static final int TAB_HEIGHT = 18;
    private static final int TAB_GAP = 4;
    private static final int DROPDOWN_ITEM_HEIGHT = 18;
    // The legacy selector shows an icon and readable skill name. 24px only
    // rendered the icon, making different skills indistinguishable.
    private static final int DROPDOWN_WIDTH = 156;
    private static final int DROPDOWN_PADDING = 2;

    private static final int COLOR_SLOT_ACTIVE = 0xFF3498db;
    private static final int COLOR_DROPDOWN_BG = 0xEE1a1a2e;
    private static final int COLOR_DROPDOWN_CLEAR = 0xFFe74c3c;

    private final List<AcademyActionButton> presetButtons = new ArrayList<>();
    private final List<AcademyActionButton> slotButtons = new ArrayList<>();
    private final List<AcademyActionButton> dropdownButtons = new ArrayList<>();
    private int viewPreset;
    private boolean viewPresetChosen;
    private int hoveredSlot = -1;
    private int hoveredTab = -1;
    private int dropdownSlot = -1;
    private int dropdownX;
    private int dropdownY;
    private int dropdownScroll;
    private List<DropdownEntry> dropdownEntries = new ArrayList<>();
    private int hoveredDropdownItem = -1;

    public SkillSlotGui() {
        super(Component.translatable("ac.skill_slot.title"));
    }

    @Override
    protected void init() {
        super.init();
        centerGui(GUI_WIDTH, GUI_HEIGHT);
        this.dropdownSlot = -1;

        Minecraft mc = Minecraft.getInstance();
        // init() also runs on resize; keep the page being edited rather than jumping back.
        if (mc.player != null && !viewPresetChosen) {
            PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
            this.viewPreset = data.getCurrentPresetIndex();
            viewPresetChosen = true;
        }
        presetButtons.clear(); slotButtons.clear(); dropdownButtons.clear();
        int tabWidth = PlayerAbilityData.PRESET_COUNT * TAB_WIDTH + (PlayerAbilityData.PRESET_COUNT - 1) * TAB_GAP;
        for (int i = 0; i < PlayerAbilityData.PRESET_COUNT; i++) {
            int index = i;
            presetButtons.add(addWidget(new AcademyActionButton(guiLeft + (GUI_WIDTH - tabWidth) / 2 + i * (TAB_WIDTH + TAB_GAP),
                    guiTop + 8, TAB_WIDTH, TAB_HEIGHT, Component.empty(), () -> {
                // PresetEditUI.startTransit only changed the page being edited; switching the
                // preset used in combat stays on the C key.
                viewPreset = index;
                updateControlMessages();
            })));
        }
        int slotsWidth = SkillPreset.SLOT_COUNT * SLOT_SIZE + (SkillPreset.SLOT_COUNT - 1) * SLOT_GAP;
        for (int i = 0; i < SkillPreset.SLOT_COUNT; i++) {
            int index = i;
            slotButtons.add(addWidget(new AcademyActionButton(guiLeft + (GUI_WIDTH - slotsWidth) / 2 + i * (SLOT_SIZE + SLOT_GAP),
                    guiTop + 50, SLOT_SIZE, SLOT_SIZE, Component.empty(), () -> {
                if (minecraft.player != null) openDropdown(index, minecraft);
            })));
        }
        updateControlMessages();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        pushZ(graphics);

        drawBackground(graphics, AcademyColors.BG);

        hoveredSlot = -1;
        hoveredTab = -1;
        hoveredDropdownItem = -1;

        drawTabs(graphics, mouseX, mouseY);
        drawSlots(graphics, mouseX, mouseY);
        drawHint(graphics);
        updateControlMessages();
        for (var widget : presetButtons) widget.render(graphics, mouseX, mouseY, partialTick);
        for (var widget : slotButtons) widget.render(graphics, mouseX, mouseY, partialTick);

        if (dropdownSlot >= 0) {
            drawDropdown(graphics, mouseX, mouseY);
        }

        if (hoveredSlot >= 0 && dropdownSlot < 0) {
            drawSlotTooltip(graphics, mouseX, mouseY);
        }

        if (hoveredDropdownItem >= 0 && dropdownSlot >= 0) {
            drawDropdownTooltip(graphics, mouseX, mouseY);
        }

        popZ(graphics);
    }

    private void drawTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int tabTotalWidth = PlayerAbilityData.PRESET_COUNT * TAB_WIDTH + (PlayerAbilityData.PRESET_COUNT - 1) * TAB_GAP;
        int tabStartX = guiLeft + (GUI_WIDTH - tabTotalWidth) / 2;
        int tabY = guiTop + 8;

        for (int i = 0; i < PlayerAbilityData.PRESET_COUNT; i++) {
            int tabX = tabStartX + i * (TAB_WIDTH + TAB_GAP);
            boolean isHovered = isHovered(tabX, tabY, TAB_WIDTH, TAB_HEIGHT, mouseX, mouseY);
            if (isHovered && dropdownSlot < 0) hoveredTab = i;

            boolean isActive = (i == viewPreset);
            int bgColor = isActive ? AcademyColors.SUCCESS : AcademyColors.BORDER;
            if (isHovered && !isActive && dropdownSlot < 0) bgColor = 0xFF4a6a7e;

            graphics.fill(tabX, tabY, tabX + TAB_WIDTH, tabY + TAB_HEIGHT, bgColor);
            Component text = Component.translatable("ac.skill_slot.preset", i + 1);
            int tw = this.font.width(text);
            graphics.drawString(this.font, text, tabX + (TAB_WIDTH - tw) / 2, tabY + 5, AcademyColors.TEXT);
        }
    }

    private void drawSlots(GuiGraphics graphics, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        SkillPreset preset = data.getPreset(viewPreset);

        int totalWidth = SkillPreset.SLOT_COUNT * SLOT_SIZE + (SkillPreset.SLOT_COUNT - 1) * SLOT_GAP;
        int startX = guiLeft + (GUI_WIDTH - totalWidth) / 2;
        int slotY = guiTop + 50;

        KeyMapping[] keys = KeyInputHandler.getSkillKeys();

        for (int i = 0; i < SkillPreset.SLOT_COUNT; i++) {
            int slotX = startX + i * (SLOT_SIZE + SLOT_GAP);
            boolean isHovered = isHovered(slotX, slotY, SLOT_SIZE, SLOT_SIZE, mouseX, mouseY);
            if (isHovered && dropdownSlot < 0) hoveredSlot = i;

            boolean isOpen = (dropdownSlot == i);
            graphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, AcademyColors.BG_PANEL);

            int borderColor = isOpen ? AcademyColors.BORDER_ACTIVE : (isHovered ? COLOR_SLOT_ACTIVE : AcademyColors.BORDER);
            drawBorder(graphics, slotX, slotY, SLOT_SIZE, SLOT_SIZE, borderColor);

            if (isHovered && !isOpen) {
                graphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, AcademyColors.HOVER);
            }

            String skillId = preset.getSlot(i);
            if (skillId != null) {
                Skill skill = SkillRegistry.getSkill(data.getCurrentAbility(), skillId);
                if (skill != null) {
                    ResourceLocation icon = skill.getIconLocation();
                    int iconSize = 32;
                    int iconX = slotX + (SLOT_SIZE - iconSize) / 2;
                    int iconY = slotY + (SLOT_SIZE - iconSize) / 2;
                    RenderUtils.render(iconSize, iconSize, iconX, iconY, graphics, icon);

                    float prof = data.getProficiency(skillId);
                    int profBarW = (int) ((SLOT_SIZE - 8) * prof);
                    graphics.fill(slotX + 4, slotY + SLOT_SIZE - 2, slotX + 4 + profBarW, slotY + SLOT_SIZE - 1, AcademyColors.SUCCESS);
                }
            }

            String keyLabel = i < keys.length ? keys[i].getTranslatedKeyMessage().getString() : "?";
            String display = "§7[" + keyLabel + "]";
            if (this.font.width(display) > SLOT_SIZE) {
                display = "§7[" + this.font.plainSubstrByWidth(keyLabel,
                        SLOT_SIZE - this.font.width("[…]")) + "…]";
            }
            int kw = this.font.width(display);
            graphics.drawString(this.font, display, slotX + (SLOT_SIZE - kw) / 2, slotY + SLOT_SIZE + 4, AcademyColors.TEXT_SECONDARY);
        }
    }

    private void drawDropdown(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);

        int visibleRows = dropdownVisibleRows();
        int totalHeight = visibleRows * DROPDOWN_ITEM_HEIGHT + DROPDOWN_PADDING * 2;

        graphics.fill(dropdownX - 1, dropdownY - 1, dropdownX + DROPDOWN_WIDTH + 1, dropdownY + totalHeight + 1, AcademyColors.BORDER_ACTIVE);
        graphics.fill(dropdownX, dropdownY, dropdownX + DROPDOWN_WIDTH, dropdownY + totalHeight, COLOR_DROPDOWN_BG);

        int itemY = dropdownY + DROPDOWN_PADDING;
        for (int i = dropdownScroll; i < Math.min(dropdownEntries.size(), dropdownScroll + visibleRows); i++) {
            DropdownEntry entry = dropdownEntries.get(i);
            int ix = dropdownX + DROPDOWN_PADDING;
            int iy = itemY + (i - dropdownScroll) * DROPDOWN_ITEM_HEIGHT;
            boolean isHovered = isHovered(dropdownX, iy, DROPDOWN_WIDTH, DROPDOWN_ITEM_HEIGHT, mouseX, mouseY);
            if (isHovered) hoveredDropdownItem = i;

            if (isHovered) {
                graphics.fill(dropdownX, iy, dropdownX + DROPDOWN_WIDTH, iy + DROPDOWN_ITEM_HEIGHT, AcademyColors.HOVER_BG);
            }

            if (entry.icon() != null) {
                RenderUtils.render(16, 16, ix + 2, iy + 1, graphics, entry.icon());
                String label = entry.displayName().getString();
                if (this.font.width(label) > DROPDOWN_WIDTH - 26) {
                    label = this.font.plainSubstrByWidth(label, DROPDOWN_WIDTH - 30) + "…";
                }
                graphics.drawString(this.font, label, ix + 22, iy + 5, AcademyColors.TEXT);
            } else {
                Component label = entry.displayName();
                int textWidth = this.font.width(label);
                int textX = ix + (DROPDOWN_WIDTH - DROPDOWN_PADDING * 2 - textWidth) / 2;
                int textY = iy + (DROPDOWN_ITEM_HEIGHT - 8) / 2;
                graphics.drawString(this.font, label, textX, textY, COLOR_DROPDOWN_CLEAR);
            }
        }

        if (dropdownEntries.size() > visibleRows) {
            int trackHeight = visibleRows * DROPDOWN_ITEM_HEIGHT;
            int thumbHeight = Math.max(4, trackHeight * visibleRows / dropdownEntries.size());
            int thumbY = itemY + (trackHeight - thumbHeight) * dropdownScroll / (dropdownEntries.size() - visibleRows);
            graphics.fill(dropdownX + DROPDOWN_WIDTH - 3, thumbY,
                    dropdownX + DROPDOWN_WIDTH - 1, thumbY + thumbHeight, AcademyColors.TEXT_SECONDARY);
        }

        graphics.enableScissor(dropdownX, dropdownY + DROPDOWN_PADDING,
                dropdownX + DROPDOWN_WIDTH, dropdownY + DROPDOWN_PADDING + visibleRows * DROPDOWN_ITEM_HEIGHT);
        for (var widget : dropdownButtons) widget.render(graphics, mouseX, mouseY, 0);
        graphics.disableScissor();
        graphics.pose().popPose();
    }


    private void drawHint(GuiGraphics graphics) {
        Component hint = Component.translatable("ac.skill_slot.hint");
        int tw = this.font.width(hint);
        graphics.drawString(this.font, hint, guiLeft + (GUI_WIDTH - tw) / 2, guiTop + GUI_HEIGHT - 16, AcademyColors.TEXT_SECONDARY);
    }

    private void drawSlotTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        SkillPreset preset = data.getPreset(viewPreset);

        String skillId = preset.getSlot(hoveredSlot);
        List<Component> tooltip = new ArrayList<>();

        if (skillId != null) {
            Skill skill = SkillRegistry.getSkill(data.getCurrentAbility(), skillId);
            if (skill != null) {
                tooltip.add(Component.translatable(skill.getTranslationKey()));
                tooltip.add(Component.translatable("ac.skill_slot.level_type", skill.getLevel(),
                        Component.translatable(skill.getType() == SkillType.PASSIVE ? "ac.skill_slot.passive" : "ac.skill_slot.active"))
                        .withStyle(ChatFormatting.WHITE));
                if (skill.getBaseCpCost() > 0) {
                    tooltip.add(Component.translatable("ac.skill_slot.cp", (int) skill.getBaseCpCost()).withStyle(ChatFormatting.AQUA)
                            .append(Component.literal("  "))
                            .append(Component.translatable("ac.skill_slot.overload", (int) skill.getBaseOverload()).withStyle(ChatFormatting.RED)));
                }
                float prof = data.getProficiency(skillId);
                tooltip.add(Component.translatable("ac.skill_slot.proficiency", String.format(java.util.Locale.ROOT, "%.1f", prof * 100))
                        .withStyle(ChatFormatting.YELLOW));
                tooltip.add(Component.empty());
                tooltip.add(Component.translatable("ac.skill_slot.select_clear").withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltip.add(Component.translatable("ac.skill_slot.empty").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("ac.skill_slot.select").withStyle(ChatFormatting.GRAY));
        }

        KeyMapping[] keys = KeyInputHandler.getSkillKeys();
        if (hoveredSlot < keys.length) {
            tooltip.add(Component.literal("[").append(keys[hoveredSlot].getTranslatedKeyMessage())
                    .append("]").withStyle(ChatFormatting.GRAY));
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
        graphics.pose().popPose();
    }

    private void drawDropdownTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        DropdownEntry entry = dropdownEntries.get(hoveredDropdownItem);
        if (entry.skillId() == null) return;

        PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        Skill skill = data.hasAbility()
                ? SkillRegistry.getSkill(data.getCurrentAbility(), entry.skillId())
                : null;
        if (skill == null) return;
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable(skill.getTranslationKey()));
        tooltip.add(Component.translatable("ac.skill_slot.level_type", skill.getLevel(),
                Component.translatable(skill.getType() == SkillType.PASSIVE ? "ac.skill_slot.passive" : "ac.skill_slot.active"))
                .withStyle(ChatFormatting.WHITE));
        if (skill.getBaseCpCost() > 0) {
            tooltip.add(Component.translatable("ac.skill_slot.cp", (int) skill.getBaseCpCost()).withStyle(ChatFormatting.AQUA)
                    .append(Component.literal("  "))
                    .append(Component.translatable("ac.skill_slot.overload", (int) skill.getBaseOverload()).withStyle(ChatFormatting.RED)));
        }
        float prof = data.getProficiency(entry.skillId());
        tooltip.add(Component.translatable("ac.skill_slot.proficiency", String.format(java.util.Locale.ROOT, "%.1f", prof * 100))
                .withStyle(ChatFormatting.YELLOW));

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int previousDropdown = dropdownSlot;
        if (previousDropdown < 0 && button == 1) {
            for (int i = 0; i < slotButtons.size(); i++) {
                if (slotButtons.get(i).isMouseOver(mouseX, mouseY)) {
                    PacketDistributor.sendToServer(new SetSkillSlotPacket(viewPreset, i, ""));
                    return true;
                }
            }
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (previousDropdown >= 0) {
            if (!handled) closeDropdown();
            if (dropdownSlot < 0) setFocused(slotButtons.get(previousDropdown));
            return true;
        }
        // ContainerEventHandler focuses the clicked slot after its callback; the popup owns focus now.
        if (dropdownSlot >= 0) setFocused(dropdownButtons.getFirst());
        return handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (dropdownSlot >= 0 && keyCode == 256) {
            closeDropdown();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void closeDropdown() {
        int slot = dropdownSlot;
        dropdownSlot = -1;
        clearFocus();
        for (var widget : dropdownButtons) removeWidget(widget);
        dropdownButtons.clear();
        presetButtons.forEach(widget -> widget.active = true);
        slotButtons.forEach(widget -> widget.active = true);
        if (slot >= 0) setFocused(slotButtons.get(slot));
    }

    private void updateControlMessages() {
        for (int i = 0; i < presetButtons.size(); i++) {
            Component preset = Component.translatable("ac.skill_slot.preset", i + 1);
            presetButtons.get(i).setMessage(i == viewPreset ? Component.translatable("ac.ui.preset_selected", preset) : preset);
        }
        if (minecraft.player == null) return;
        PlayerAbilityData data = minecraft.player.getData(AcademyAttachments.PLAYER_ABILITY);
        for (int i = 0; i < slotButtons.size(); i++) {
            String id = data.getPreset(viewPreset).getSlot(i);
            Skill skill = id == null ? null : SkillRegistry.getSkill(data.getCurrentAbility(), id);
            Component name = Component.translatable(skill == null ? "ac.skill_slot.empty" : skill.getTranslationKey());
            slotButtons.get(i).setMessage(Component.translatable("ac.ui.skill_slot", i + 1, name,
                    KeyInputHandler.getSkillKeys()[i].getTranslatedKeyMessage()));
        }
    }

    private void revealDropdownEntry(int index) {
        if (index < dropdownScroll) dropdownScroll = index;
        else if (index >= dropdownScroll + dropdownVisibleRows()) dropdownScroll = index - dropdownVisibleRows() + 1;
        updateDropdownPositions();
    }

    private void updateDropdownPositions() {
        for (int i = 0; i < dropdownButtons.size(); i++)
            dropdownButtons.get(i).setY(dropdownY + DROPDOWN_PADDING + (i - dropdownScroll) * DROPDOWN_ITEM_HEIGHT);
    }

    private void openDropdown(int slotIndex, Minecraft mc) {
        PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        SkillPreset preset = data.getPreset(viewPreset);
        String currentSkillId = preset.getSlot(slotIndex);

        Set<String> usedSkills = new HashSet<>();
        for (int i = 0; i < SkillPreset.SLOT_COUNT; i++) {
            if (i == slotIndex) continue;
            String sid = preset.getSlot(i);
            if (sid != null) usedSkills.add(sid);
        }

        dropdownEntries.clear();
        dropdownEntries.add(new DropdownEntry(null, Component.translatable("ac.skill_slot.clear"), null));

        List<String> learned = getLearnedActiveSkills(data);
        for (String skillId : learned) {
            if (usedSkills.contains(skillId)) continue;
            Skill skill = SkillRegistry.getSkill(data.getCurrentAbility(), skillId);
            if (skill == null) continue;
            Component name = Component.translatable(skill.getTranslationKey());
            dropdownEntries.add(new DropdownEntry(skillId, name, skill.getIconLocation()));
        }

        if (currentSkillId != null && usedSkills.contains(currentSkillId)) {
            Skill skill = SkillRegistry.getSkill(data.getCurrentAbility(), currentSkillId);
            if (skill != null) {
                Component name = Component.translatable(skill.getTranslationKey());
                dropdownEntries.add(new DropdownEntry(currentSkillId, Component.translatable("ac.skill_slot.current", name), skill.getIconLocation()));
            }
        }

        int totalWidth = SkillPreset.SLOT_COUNT * SLOT_SIZE + (SkillPreset.SLOT_COUNT - 1) * SLOT_GAP;
        int startX = guiLeft + (GUI_WIDTH - totalWidth) / 2;
        int slotY = guiTop + 50;
        dropdownX = startX + slotIndex * (SLOT_SIZE + SLOT_GAP) + SLOT_SIZE / 2 - DROPDOWN_WIDTH / 2;
        dropdownY = slotY + SLOT_SIZE + 14;

        dropdownX = Math.clamp(dropdownX, guiLeft + 2, guiLeft + GUI_WIDTH - DROPDOWN_WIDTH - 2);

        dropdownScroll = 0;
        int totalDropdownHeight = dropdownVisibleRows() * DROPDOWN_ITEM_HEIGHT + DROPDOWN_PADDING * 2;
        int centerY = slotY + SLOT_SIZE / 2;
        dropdownY = centerY - totalDropdownHeight / 2;

        if (dropdownY < 0) {
            dropdownY = 0;
        }
        if (dropdownY + totalDropdownHeight > this.height) {
            dropdownY = this.height - totalDropdownHeight;
            if (dropdownY < 0) {
                dropdownY = 0;
            }
        }

        dropdownSlot = slotIndex;
        clearFocus();
        presetButtons.forEach(widget -> widget.active = false);
        slotButtons.forEach(widget -> widget.active = false);
        for (int i = 0; i < dropdownEntries.size(); i++) {
            int index = i;
            DropdownEntry entry = dropdownEntries.get(i);
            dropdownButtons.add(addWidget(new AcademyActionButton(dropdownX, dropdownY + DROPDOWN_PADDING + i * DROPDOWN_ITEM_HEIGHT,
                    DROPDOWN_WIDTH, DROPDOWN_ITEM_HEIGHT, entry.displayName(), () -> {
                PacketDistributor.sendToServer(new SetSkillSlotPacket(viewPreset, dropdownSlot, entry.skillId() == null ? "" : entry.skillId()));
                closeDropdown();
            }, () -> revealDropdownEntry(index)) {
                @Override public boolean isMouseOver(double x, double y) {
                    return insideDropdown(y) && super.isMouseOver(x, y);
                }
                @Override public boolean mouseClicked(double x, double y, int button) {
                    return insideDropdown(y) && super.mouseClicked(x, y, button);
                }
                private boolean insideDropdown(double y) {
                    return y >= dropdownY + DROPDOWN_PADDING
                            && y < dropdownY + DROPDOWN_PADDING + dropdownVisibleRows() * DROPDOWN_ITEM_HEIGHT;
                }
            }));
        }
        setFocused(dropdownButtons.getFirst());
    }

    private int dropdownVisibleRows() {
        return Math.min(dropdownEntries.size(), Math.max(1, (height - 2 - DROPDOWN_PADDING * 2) / DROPDOWN_ITEM_HEIGHT));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int visibleRows = dropdownVisibleRows();
        int dropdownHeight = visibleRows * DROPDOWN_ITEM_HEIGHT + DROPDOWN_PADDING * 2;
        if (dropdownSlot >= 0 && mouseX >= dropdownX && mouseX < dropdownX + DROPDOWN_WIDTH
                && mouseY >= dropdownY && mouseY < dropdownY + dropdownHeight) {
            dropdownScroll = (int) Math.clamp(dropdownScroll - scrollY, 0, Math.max(0, dropdownEntries.size() - visibleRows));
            updateDropdownPositions();
            if (getFocused() instanceof AcademyActionButton focused
                    && (focused.getY() < dropdownY + DROPDOWN_PADDING
                    || focused.getY() + DROPDOWN_ITEM_HEIGHT > dropdownY + DROPDOWN_PADDING + visibleRows * DROPDOWN_ITEM_HEIGHT)) {
                clearFocus();
            }
            hoveredDropdownItem = -1;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private List<String> getLearnedActiveSkills(PlayerAbilityData data) {
        List<String> result = new ArrayList<>();
        if (!data.hasAbility()) return result;
        for (Skill skill : SkillRegistry.getSkillsByCategory(data.getCurrentAbility())) {
            if (skill.getType() != SkillType.ACTIVE) continue;
            // A zero registry cost does not make an active skill passive or
            // unassignable. Teleporter skills and overload_thinking debit
            // their resources in their own server-authoritative handlers, so
            // filtering on the registry's one-shot cost hid the whole
            // Teleporter category from this selector.
            // AbilityData.getControllableSkillList only offered enabled (canControl) skills.
            if (data.hasLearnedSkill(skill.getId()) && PlayerAbilityData.skillEnabled(skill.getId())) {
                result.add(skill.getId());
            }
        }
        return result;
    }

    private record DropdownEntry(String skillId, Component displayName, ResourceLocation icon) {
    }
}
