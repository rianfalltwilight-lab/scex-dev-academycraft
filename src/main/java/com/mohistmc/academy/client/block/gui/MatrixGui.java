package com.mohistmc.academy.client.block.gui;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.gui.AcademyBaseUI;
import com.mohistmc.academy.client.gui.InfoArea;
import com.mohistmc.academy.network.InitMatrixPacket;
import com.mohistmc.academy.network.MatrixConfigPacket;
import com.mohistmc.academy.network.NetworkInputLimits;
import com.mohistmc.academy.world.menu.MatrixMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

/** Wireless matrix inventory plus the 1.0.7-style right information area. */
@OnlyIn(Dist.CLIENT)
public class MatrixGui extends AcademyBaseUI<MatrixMenu> {
    private static final ResourceLocation UI_MATRIX = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MODID, "textures/guis/ui/ui_matrix.png");
    private static final int PANEL_X = 183;
    private static final int PANEL_Y = 5;
    private static final int PANEL_W = 100;
    private static final int PANEL_H = 140;
    private static final int BUTTON_X = 6;
    private static final int BUTTON_W = PANEL_W - 12;
    private static final int BUTTON_H = 16;
    private static final int RED_TEXT = 0xFF000000 | ChatFormatting.RED.getColor();

    private boolean editingSsid;
    private boolean editingPassword;
    private final StringBuilder ssidInput = new StringBuilder();
    private final StringBuilder passwordInput = new StringBuilder();
    private boolean inputStateInitialized;
    private boolean ssidEdited, passwordEdited;
    private com.mohistmc.academy.network.MenuActionToken pendingToken;
    private String pendingSsid, pendingPassword;
    private String saveStatus = "";
    private String observedSsid;
    private int pendingTicks;

    public MatrixGui(MatrixMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, WirelessState.DEFAULT);
        // The inventory and the legacy 100px InfoArea are one 283px logical
        // screen. Reserving that width makes AbstractContainerScreen centre the
        // whole composition, so INIT/link controls remain reachable at common
        // GUI-scale widths instead of being clipped beyond the right edge.
        this.imageWidth = PANEL_X + PANEL_W;
        // DEFAULT has no wireless sub-page. Leaving the common sidebar enabled
        // opened a blank page and disabled every slot.
        setRenderWireless(false);
    }

    @Override
    protected void init() {
        super.init();
        // Include the always-visible inventory selector when centring the
        // 295px matrix composition. At 320 logical pixels this yields
        // sidebar x=2 and panel right=317, with neither edge clipped.
        this.leftPos = com.mohistmc.academy.client.gui.RegularMachineLayout
                .contentLeftWithSidebar(this.width, this.imageWidth);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {}

    @Override
    public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderStandardMachinePanel(graphics, UI_MATRIX);
        initializeInputState();

        int px = this.leftPos + PANEL_X;
        int py = this.topPos + PANEL_Y;
        InfoArea.drawPanel(graphics, px, py, PANEL_W, PANEL_H);
        graphics.drawString(font, menu.isOperational()
                        ? Component.translatable("ac.gui.matrix.status.operational").withStyle(ChatFormatting.GREEN)
                        : Component.translatable(menu.isInitialized()
                                ? "ac.gui.matrix.status.paused" : "ac.gui.matrix.status.uninitialized")
                                .withStyle(ChatFormatting.YELLOW),
                px + 6, py + 7, 0xFFFFFFFF, false);

        String ssid = editingSsid ? ssidInput + "▌"
                : menu.isInitialized() ? com.mohistmc.academy.client.gui.WirelessDisplayName.display(ssidInput.toString())
                : ssidInput.isEmpty() ? tr("ac.gui.matrix.click_to_set") : ssidInput.toString();
        graphics.drawString(font, trim("SSID: " + ssid), px + 6, py + 23, 0xFFCCCCCC, false);
        String password = passwordEdited ? "*".repeat(passwordInput.length()) + (editingPassword ? "▌" : "")
                : tr(menu.hasPasswordConfigured() ? "ac.gui.matrix.click_to_change" : "ac.gui.matrix.password_none");
        graphics.drawString(font, trim(tr("ac.gui.matrix.password", password)), px + 6, py + 36, 0xFFCCCCCC, false);

        if (menu.isInitialized()) {
            graphics.drawString(font, Component.translatable("ac.gui.matrix.capacity", menu.getCapacity()), px + 6, py + 53, 0xFFAAAAAA, false);
            graphics.drawString(font, Component.translatable("ac.gui.matrix.bandwidth", menu.getBandwidth()), px + 6, py + 66, 0xFFAAAAAA, false);
            graphics.drawString(font, Component.translatable("ac.gui.matrix.range", menu.getRange()), px + 6, py + 79, 0xFFAAAAAA, false);
            graphics.drawString(font, trim(tr("ac.gui.matrix.owner", menu.getOwnerLabel())),
                    px + 6, py + 92, 0xFF888888, false);
            if (!menu.isOperational()) {
                graphics.drawString(font, trim(tr("ac.gui.matrix.restore_hint")), px + 6, py + 106,
                        RED_TEXT, false);
            }
            graphics.drawString(font, trim(!saveStatus.isEmpty() ? saveStatus : ssidEdited || passwordEdited
                            ? tr("ac.gui.matrix.hint.unsaved") : menu.canEdit() ? tr("ac.gui.matrix.hint.edit")
                            : tr("ac.gui.matrix.hint.read_only")),
                    px + 6, py + 121, 0xFF8FBBD0, false);
        } else {
            graphics.drawString(font, menu.hasInitializationMaterials()
                            ? Component.translatable("ac.gui.matrix.materials_ready").withStyle(ChatFormatting.GREEN)
                            : Component.translatable("ac.gui.matrix.materials_missing").withStyle(ChatFormatting.RED),
                    px + 6, py + 58, 0xFFFFFFFF, false);
            graphics.drawString(font, trim(saveStatus), px + 6, py + 106, 0xFF8FBBD0, false);
            drawButton(graphics, px + BUTTON_X, py + 78, Component.literal("INIT"),
                    mouseX, mouseY, pendingToken == null && menu.hasInitializationMaterials() && menu.actionSessionReady() && menu.canEdit());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        initializeInputState();
        int px = this.leftPos + PANEL_X;
        int py = this.topPos + PANEL_Y;
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        if (!menu.isInitialized()) {
            if (inside(mouseX, mouseY, px + BUTTON_X, py + 78, BUTTON_W, BUTTON_H)
                    && pendingToken == null && menu.hasInitializationMaterials() && menu.actionSessionReady() && menu.pos != null && menu.canEdit()) {
                pendingToken = menu.nextActionToken();
                pendingTicks = 0;
                pendingSsid = ssidInput.isEmpty() ? "Unnamed" : ssidInput.toString();
                pendingPassword = passwordInput.toString();
                saveStatus = tr("ac.gui.common.saving");
                PacketDistributor.sendToServer(new InitMatrixPacket(pendingToken, menu.pos, pendingSsid, pendingPassword));
                editingSsid = editingPassword = false;
                return true;
            }
        }
        if (pendingToken == null && menu.canEdit() && inside(mouseX, mouseY, px + 4, py + 18, PANEL_W - 8, 14)) {
            editingSsid = true;
            editingPassword = false;
            return true;
        }
        if (pendingToken == null && menu.canEdit() && inside(mouseX, mouseY, px + 4, py + 32, PANEL_W - 8, 14)) {
            editingPassword = true;
            editingSsid = false;
            if (!passwordEdited) passwordInput.setLength(0);
            passwordEdited = true;
            saveStatus = "";
            return true;
        }

        editingSsid = false;
        editingPassword = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && menu.isInitialized()
                && (editingSsid || editingPassword || ssidEdited || passwordEdited)) {
            submitConfig();
            return true;
        }
        if (!editingSsid && !editingPassword) return super.keyPressed(keyCode, scanCode, modifiers);
        StringBuilder target = editingSsid ? ssidInput : passwordInput;
        if (keyCode == 259) {
            if (!target.isEmpty()) { target.deleteCharAt(target.length() - 1); markEdited(); }
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            editingSsid = false;
            editingPassword = false;
            return true;
        }
        if (keyCode == 256) {
            editingSsid = false;
            editingPassword = false;
            // Escape cancels drafts; do not leave an unsaved SSID looking confirmed.
            ssidInput.setLength(0);
            ssidInput.append(menu.getCurrentSsid());
            ssidEdited = passwordEdited = false;
            saveStatus = "";
            passwordInput.setLength(0);
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!editingSsid && !editingPassword) return super.charTyped(codePoint, modifiers);
        StringBuilder target = editingSsid ? ssidInput : passwordInput;
        int limit = editingSsid ? NetworkInputLimits.SSID : NetworkInputLimits.PASSWORD;
        if (!Character.isISOControl(codePoint) && target.length() < limit) { target.append(codePoint); markEdited(); }
        return true;
    }

    private void initializeInputState() {
        String current = menu.getCurrentSsid();
        if (inputStateInitialized) {
            if (!java.util.Objects.equals(current, observedSsid)) {
                observedSsid = current;
                if (!ssidEdited && pendingToken == null) {
                    ssidInput.setLength(0);
                    ssidInput.append(current);
                }
            }
            return;
        }
        observedSsid = current;
        ssidInput.append(menu.getInitialSsid());
        inputStateInitialized = true;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (pendingToken != null && ++pendingTicks > 200) {
            pendingToken = null;
            pendingSsid = pendingPassword = null;
            saveStatus = tr("ac.gui.matrix.unconfirmed");
        }
    }

    private void markEdited() {
        if (editingSsid) ssidEdited = true;
        if (editingPassword) passwordEdited = true;
        saveStatus = "";
    }

    private void submitConfig() {
        if (pendingToken != null) return;
        if (!menu.actionSessionReady()) { saveStatus = tr("ac.gui.common.syncing"); return; }
        if (!menu.canEdit() || menu.pos == null) { saveStatus = tr("ac.gui.matrix.no_permission"); return; }
        if (!ssidEdited && !passwordEdited) return;
        if (ssidEdited && !NetworkInputLimits.validRequired(ssidInput.toString(), NetworkInputLimits.SSID)) {
            saveStatus = tr("ac.gui.matrix.ssid_empty");
            return;
        }
        pendingToken = menu.nextActionToken();
        pendingTicks = 0;
        pendingSsid = ssidEdited ? ssidInput.toString() : null;
        pendingPassword = passwordEdited ? passwordInput.toString() : null;
        saveStatus = tr("ac.gui.common.saving");
        // Submit every edited field, never an untouched opening snapshot.
        PacketDistributor.sendToServer(new MatrixConfigPacket(pendingToken, menu.pos,
                java.util.Optional.ofNullable(pendingSsid), java.util.Optional.ofNullable(pendingPassword)));
        editingSsid = editingPassword = false;
    }

    public void acceptMatrixConfigResult(com.mohistmc.academy.network.MatrixConfigResultPacket result) {
        if (!result.pos().equals(menu.pos) || !result.actionToken().equals(pendingToken)) return;
        saveStatus = tr(result.accepted() ? "ac.gui.matrix.saved" : "ac.gui.matrix.save_failed");
        if (result.accepted()) {
            ssidInput.setLength(0);
            ssidInput.append(result.ssid());
            ssidEdited = passwordEdited = false;
            passwordInput.setLength(0);
        }
        pendingToken = null;
        pendingSsid = pendingPassword = null;
    }

    /** Exercise the real INIT hit box in the isolated client integration gate. */
    public boolean clickInitForVisualGate() {
        if (!Boolean.getBoolean("academy.machineVisualGate")) return false;
        initializeInputState();
        int px = this.leftPos + PANEL_X;
        int py = this.topPos + PANEL_Y;
        return mouseClicked(px + BUTTON_X + BUTTON_W / 2.0, py + 86.0, 0);
    }

    private void drawButton(GuiGraphics graphics, int x, int y, Component label,
                            double mouseX, double mouseY, boolean enabled) {
        int color = !enabled ? 0xFF555566
                : inside(mouseX, mouseY, x, y, BUTTON_W, BUTTON_H) ? 0xFF3498DB : 0xFF2775AE;
        graphics.fill(x, y, x + BUTTON_W, y + BUTTON_H, color);
        graphics.drawCenteredString(font, label, x + BUTTON_W / 2, y + 4,
                enabled ? 0xFFFFFFFF : 0xFF999999);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private String trim(String value) {
        return font.plainSubstrByWidth(value, PANEL_W - 12);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
