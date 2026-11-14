package com.mohistmc.academy.client.block.gui;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.gui.AcademyBaseUI;
import com.mohistmc.academy.client.gui.InfoArea;
import com.mohistmc.academy.world.menu.WindGenMainMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WindMainGui extends AcademyBaseUI<WindGenMainMenu> {

    private static final ResourceLocation UI_WIN_MAIN = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/ui/ui_windmain.png");

    public WindMainGui(WindGenMainMenu menu, Inventory inv, Component p_97743_) {
        super(menu, inv, p_97743_, WirelessState.DEFAULT);
        setRenderWireless(false);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // The official 1.0.7 overlay already owns every caption. Vanilla's
        // dark menu title and "Inventory" label otherwise sit on top of it.
    }

    @Override
    public void renderBackground(GuiGraphics p_300197_, int p_297538_, int p_300104_, float p_298759_) {
        renderStandardMachinePanel(p_300197_, UI_WIN_MAIN);

        if (menu.pos != null) {
            new InfoArea().seplineInfo()
                    .property(Component.translatable("ac.gui.common.prop.altitude").getString(), Integer.toString(menu.pos.getY()))
                    .property(Component.translatable("ac.gui.windgen.prop.structure").getString(), Component.translatable(menu.isStructureComplete()
                            ? "ac.gui.windgen.structure.complete" : "ac.gui.windgen.structure.incomplete").getString())
                    .property(Component.translatable("ac.gui.windgen.prop.fan").getString(), Component.translatable(menu.isFanInstalled()
                            ? "ac.gui.windgen.fan.installed" : "ac.gui.windgen.fan.missing").getString())
                    .property(Component.translatable("ac.gui.common.prop.status").getString(),
                            Component.translatable(menu.isWorking() ? "ac.gui.windgen.state.running"
                            : menu.isStructureComplete() && menu.isFanInstalled()
                            ? "ac.gui.windgen.state.blocked" : "ac.gui.windgen.state.stopped").getString())
                    .draw(p_300197_, this.leftPos, this.topPos);
        }
    }
}
