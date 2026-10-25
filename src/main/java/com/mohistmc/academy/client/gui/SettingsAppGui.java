package com.mohistmc.academy.client.gui;

import com.mohistmc.academy.client.KeyInputHandler;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.network.SettingsConfigPacket;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public class SettingsAppGui extends AcademyScreen {

    private static final int GUI_WIDTH = 280;
    private static final int GUI_HEIGHT = 200;
    private static final int TOP_BAR = 28;
    private static final int ROW_HEIGHT = 20;

    private int scrollOffset = 0;
    private int hoveredRow = -1;
    private boolean hoveredBack = false;
    private long lastSettingsRefresh = Long.MIN_VALUE;

    private final List<SettingRow> rows = new ArrayList<>();

    public SettingsAppGui() {
        super(Component.translatable("item.academy.app_settings"));
    }

    @Override
    protected void init() {
        super.init();
        centerGui(GUI_WIDTH, GUI_HEIGHT);
        buildRows();
    }

    private void buildRows() {
        rows.clear();
        Minecraft mc = Minecraft.getInstance();
        rows.add(new SettingRow(Component.translatable("ac.settings.cat.keys"), Component.empty(), RowType.HEADER));
        rows.add(new SettingRow(Component.translatable("ac.settings.key.open_slots"),
                KeyInputHandler.OPEN_SKILL_SLOT.getTranslatedKeyMessage(), RowType.INFO));
        rows.add(new SettingRow(Component.translatable("ac.settings.key.toggle_ability"),
                KeyInputHandler.TOGGLE_ABILITY.getTranslatedKeyMessage(), RowType.INFO));
        rows.add(new SettingRow(Component.translatable("ac.settings.key.switch_preset"),
                KeyInputHandler.SWITCH_PRESET.getTranslatedKeyMessage(), RowType.INFO));

        for (int i = 0; i < KeyInputHandler.getSkillKeys().length; i++) {
            rows.add(new SettingRow(Component.translatable("ac.settings.key.slot", i + 1),
                    KeyInputHandler.getSkillKeys()[i].getTranslatedKeyMessage(), RowType.INFO));
        }

        rows.add(new SettingRow(Component.empty(), Component.empty(), RowType.SEPARATOR));
        rows.add(new SettingRow(Component.translatable("ac.settings.cat.generic"), Component.empty(), RowType.HEADER));
        if (mc.hasSingleplayerServer()) {
            rows.add(toggleRow("ac.settings.prop.attackPlayer", ACConfig.Server.pvpEnabled(), SettingKey.PVP));
            boolean destroyBlocks;
            try { destroyBlocks = ACConfig.Server.DESTROY_BLOCKS.get(); }
            catch (IllegalStateException unloaded) { destroyBlocks = true; }
            rows.add(toggleRow("ac.settings.prop.destroyBlocks", destroyBlocks, SettingKey.DESTROY_BLOCKS));
        }
        rows.add(toggleRow("ac.settings.prop.headsOrTails", ACConfig.Client.headsOrTails(), SettingKey.HEADS_OR_TAILS));

        rows.add(new SettingRow(Component.empty(), Component.empty(), RowType.SEPARATOR));
        rows.add(new SettingRow(Component.translatable("ac.settings.cat.interface"), Component.empty(), RowType.HEADER));
        rows.add(toggleRow("ac.settings.prop.showHud", ACConfig.Client.showHud(), SettingKey.SHOW_HUD));
        rows.add(toggleRow("ac.settings.prop.showCpBar", ACConfig.Client.showCpBar(), SettingKey.SHOW_CP_BAR));
        rows.add(toggleRow("ac.settings.prop.showChargingHud", ACConfig.Client.showChargingHud(), SettingKey.SHOW_CHARGING_HUD));
        rows.add(toggleRow("ac.settings.prop.showKeyHints", ACConfig.Client.showKeyHints(), SettingKey.SHOW_KEY_HINTS));
        rows.add(toggleRow("ac.settings.prop.autoAvoidJade", ACConfig.Client.autoAvoidJade(), SettingKey.AUTO_AVOID_JADE));
        rows.add(new SettingRow(Component.translatable("ac.settings.prop.editHud"), Component.literal(">"),
                RowType.ACTION, SettingKey.EDIT_HUD, false));

        rows.add(new SettingRow(Component.empty(), Component.empty(), RowType.SEPARATOR));
        rows.add(new SettingRow(Component.translatable("ac.settings.cat.audio"), Component.empty(), RowType.HEADER));
        rows.add(toggleRow("ac.settings.prop.skillSounds", ACConfig.Client.enableSkillSounds(), SettingKey.SKILL_SOUNDS));

        rows.add(new SettingRow(Component.empty(), Component.empty(), RowType.SEPARATOR));
        rows.add(new SettingRow(Component.translatable("ac.settings.cat.ability"), Component.empty(), RowType.HEADER));

        if (mc.player != null) {
            PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
            if (data.hasAbility()) {
                Component abilityName = Component.translatable("item.academy.factor_" + data.getCurrentAbility().id());
                rows.add(new SettingRow(Component.translatable("ac.settings.ability.current"), abilityName, RowType.INFO));
                rows.add(new SettingRow(Component.translatable("ac.settings.ability.level"),
                        Component.translatable("ac.settings.ability.level_value", data.getPlayerLevel()), RowType.INFO));
                rows.add(new SettingRow(Component.translatable("ac.settings.ability.cp"),
                        Component.literal(String.format("%.0f / %.0f", data.getCurrentCp(), data.getMaxCp())), RowType.INFO));
                rows.add(new SettingRow(Component.translatable("ac.settings.ability.overload"),
                        Component.literal(String.format("%.0f / %.0f", data.getCurrentOverload(), data.getMaxOverload())), RowType.INFO));
                rows.add(new SettingRow(Component.translatable("ac.settings.ability.skills"),
                        Component.translatable("ac.settings.ability.skill_count", data.getLearnedSkills().size()), RowType.INFO));
                boolean active = data.isAbilityActive();
                rows.add(new SettingRow(Component.translatable("ac.settings.ability.status"),
                        Component.translatable(active ? "ac.settings.ability.active" : "ac.settings.ability.inactive"),
                        RowType.STATUS, null, active));
            } else {
                rows.add(new SettingRow(Component.translatable("ac.settings.ability.current"),
                        Component.translatable("ac.settings.ability.none"), RowType.INFO));
            }
        }
    }

    private static SettingRow toggleRow(String translationKey, boolean enabled, SettingKey key) {
        return new SettingRow(Component.translatable(translationKey),
                Component.translatable(enabled ? "ac.settings.enabled" : "ac.settings.disabled"),
                RowType.TOGGLE, key, enabled);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getGameTime() / 5 != lastSettingsRefresh) {
            lastSettingsRefresh = minecraft.level.getGameTime() / 5;
            buildRows();
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        pushZ(graphics);

        drawBackground(graphics, AcademyColors.BG);
        graphics.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + TOP_BAR, AcademyColors.BG_PANEL);
        graphics.fill(guiLeft, guiTop + TOP_BAR, guiLeft + GUI_WIDTH, guiTop + TOP_BAR + 1, AcademyColors.ACCENT);

        int backX = guiLeft + 6;
        int backY = guiTop + 5;
        hoveredBack = drawBackButton(graphics, backX, backY, mouseX, mouseY);

        Component title = Component.translatable("item.academy.app_settings");
        int titleX = backX + 18 + 6;
        graphics.drawString(this.font, title, titleX, guiTop + 9, AcademyColors.TEXT_ACCENT);

        graphics.enableScissor(guiLeft, guiTop + TOP_BAR + 1, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT);
        graphics.pose().pushPose();
        graphics.pose().translate(0, -scrollOffset, 0);

        int adjustedMouseY = mouseY + scrollOffset;
        hoveredRow = -1;

        int y = guiTop + TOP_BAR + 6;
        for (int i = 0; i < rows.size(); i++) {
            SettingRow row = rows.get(i);
            int rowY = y + i * ROW_HEIGHT;

            if (row.type == RowType.SEPARATOR) {
                graphics.fill(guiLeft + 8, rowY + ROW_HEIGHT / 2, guiLeft + GUI_WIDTH - 8, rowY + ROW_HEIGHT / 2 + 1, AcademyColors.SEPARATOR);
                continue;
            }

            if (row.type != RowType.HEADER) {
                int bgColor = (i % 2 == 0) ? AcademyColors.BG_CARD : AcademyColors.BG_CARD_ALT;
                graphics.fill(guiLeft + 2, rowY, guiLeft + GUI_WIDTH - 2, rowY + ROW_HEIGHT, bgColor);
            }

            boolean isHovered = insideRows(mouseX, mouseY)
                    && isHovered(guiLeft + 2, rowY, GUI_WIDTH - 4, ROW_HEIGHT, mouseX, adjustedMouseY);
            if (isHovered && row.type != RowType.HEADER) hoveredRow = i;

            if (isHovered && row.type != RowType.HEADER) {
                graphics.fill(guiLeft + 2, rowY, guiLeft + GUI_WIDTH - 2, rowY + ROW_HEIGHT, AcademyColors.HOVER);
            }

            if (row.type == RowType.HEADER) {
                graphics.drawString(this.font, row.label, guiLeft + 8, rowY + 6, AcademyColors.TEXT_ACCENT);
            } else {
                graphics.drawString(this.font, row.label, guiLeft + 10, rowY + 6, AcademyColors.TEXT);
                int valW = this.font.width(row.value);
                int valColor = row.type == RowType.STATUS || row.type == RowType.TOGGLE
                        ? (row.enabled ? AcademyColors.SUCCESS : AcademyColors.ERROR)
                        : row.type == RowType.ACTION ? AcademyColors.TEXT_ACCENT
                        : AcademyColors.TEXT_SECONDARY;
                graphics.drawString(this.font, row.value, guiLeft + GUI_WIDTH - valW - 10, rowY + 6, valColor);
            }
        }

        graphics.pose().popPose();
        graphics.disableScissor();
        popZ(graphics);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hoveredBack && button == 0) {
            Minecraft.getInstance().setScreen(new DataTerminalGui());
            return true;
        }
        if (button == 0 && insideRows(mouseX, mouseY) && hoveredRow >= 0 && hoveredRow < rows.size()) {
            SettingRow row = rows.get(hoveredRow);
            if (row.key != null) {
                switch (row.key) {
                    case HEADS_OR_TAILS -> ACConfig.Client.HEADS_OR_TAILS.set(!ACConfig.Client.headsOrTails());
                    case SHOW_HUD -> ACConfig.Client.SHOW_HUD.set(!ACConfig.Client.showHud());
                    case SHOW_CP_BAR -> ACConfig.Client.SHOW_CP_BAR.set(!ACConfig.Client.showCpBar());
                    case SHOW_CHARGING_HUD -> ACConfig.Client.SHOW_CHARGING_HUD.set(!ACConfig.Client.showChargingHud());
                    case SHOW_KEY_HINTS -> ACConfig.Client.SHOW_KEY_HINTS.set(!ACConfig.Client.showKeyHints());
                    case AUTO_AVOID_JADE -> ACConfig.Client.AUTO_AVOID_JADE.set(!ACConfig.Client.autoAvoidJade());
                    case SKILL_SOUNDS -> ACConfig.Client.ENABLE_SKILL_SOUNDS.set(!ACConfig.Client.enableSkillSounds());
                    case EDIT_HUD -> {
                        Minecraft.getInstance().setScreen(new HudCustomizeGui());
                        return true;
                    }
                    case PVP -> PacketDistributor.sendToServer(new SettingsConfigPacket(
                            SettingsConfigPacket.PVP, !ACConfig.Server.pvpEnabled()));
                    case DESTROY_BLOCKS -> {
                        boolean current;
                        try { current = ACConfig.Server.DESTROY_BLOCKS.get(); }
                        catch (IllegalStateException unloaded) { current = true; }
                        PacketDistributor.sendToServer(new SettingsConfigPacket(
                                SettingsConfigPacket.DESTROY_BLOCKS, !current));
                    }
                }
                buildRows();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int contentHeight = rows.size() * ROW_HEIGHT + 8;
        int visibleHeight = GUI_HEIGHT - TOP_BAR - 1;
        int maxScroll = Math.max(0, contentHeight - visibleHeight);
        scrollOffset = (int) Math.clamp(scrollOffset - scrollY * 10, 0, maxScroll);
        return true;
    }

    private boolean insideRows(double mouseX, double mouseY) {
        return mouseX >= guiLeft && mouseX < guiLeft + GUI_WIDTH
                && mouseY >= guiTop + TOP_BAR + 1 && mouseY < guiTop + GUI_HEIGHT;
    }

    private enum RowType { HEADER, INFO, STATUS, TOGGLE, ACTION, SEPARATOR }

    private enum SettingKey {
        PVP, DESTROY_BLOCKS, HEADS_OR_TAILS,
        SHOW_HUD, SHOW_CP_BAR, SHOW_CHARGING_HUD, SHOW_KEY_HINTS,
        AUTO_AVOID_JADE, EDIT_HUD, SKILL_SOUNDS
    }

    private record SettingRow(Component label, Component value, RowType type, SettingKey key, boolean enabled) {
        private SettingRow(Component label, Component value, RowType type) {
            this(label, value, type, null, false);
        }
    }
}
