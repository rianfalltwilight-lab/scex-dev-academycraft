package com.mohistmc.academy.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Native input and narration for controls whose artwork is drawn by the Academy screen. */
public class AcademyActionButton extends AbstractButton {
    private final Runnable action;
    private final Runnable reveal;

    public AcademyActionButton(int x, int y, int width, int height, Component message, Runnable action) {
        this(x, y, width, height, message, action, () -> {});
    }

    public AcademyActionButton(int x, int y, int width, int height, Component message,
                               Runnable action, Runnable reveal) {
        super(x, y, width, height, message);
        this.action = action;
        this.reveal = reveal;
    }

    @Override public void onPress() { action.run(); }

    @Override public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused) reveal.run();
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Scroll viewports override hit testing; clipped rows must not become narration hover targets.
        this.isHovered = isMouseOver(mouseX, mouseY);
        if (isFocused()) graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFFFFFFFF);
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
