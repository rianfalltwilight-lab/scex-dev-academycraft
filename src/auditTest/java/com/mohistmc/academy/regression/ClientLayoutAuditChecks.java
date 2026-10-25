package com.mohistmc.academy.regression;

import com.mohistmc.academy.client.gui.*;
import com.mohistmc.academy.skill.PlayerAbilityData.TeleportLocation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.MultiBufferSource;

/** Real parser and screen paths; opt-in client probe only. */
final class ClientLayoutAuditChecks {
    static void run(Minecraft mc, MultiBufferSource.BufferSource buffers, List<String> evidence) throws Exception {
        var parse = TutorialMdParser.class.getDeclaredMethod("parseContent", String.class, String.class);
        parse.setAccessible(true);
        var parsed = (TutorialMdParser.TutorialData) parse.invoke(null, "audit",
                "![content]\nbefore\n```java\ncode\n```\nafter\n```\n```\nlast\n");
        var text = parsed.contentLines().stream().map(TutorialMdParser.TutorialLine::text).toList();
        require(text.containsAll(List.of("before", "code", "after", "last")), "code fence swallowed following tutorial content");
        evidence.add("PASS real tutorial parser preserves text before/after nonempty and empty code fences");

        var tutorial = new TutorialAppGui(true);
        tutorial.init(mc, 640, 360);
        var draw = TutorialAppGui.class.getDeclaredMethod("drawBackButton", GuiGraphics.class, int.class, int.class);
        draw.setAccessible(true);
        var graphics = new ProbeGraphics(mc, buffers);
        var oldScreen = mc.screen;
        try {
            for (float scale : new float[]{.5F, 2F}) {
                field(TutorialAppGui.class, "fx").setFloat(tutorial, 100F);
                field(TutorialAppGui.class, "fy").setFloat(tutorial, 100F);
                field(TutorialAppGui.class, "fscale").setFloat(tutorial, scale);
                int center = (int)(100 - 11 * scale);
                graphics.backColor = 0;
                draw.invoke(tutorial, graphics, center, center);
                require(graphics.backColor == 0xFF3A4A5E, "scaled back button did not highlight its actual hit box");
                require(tutorial.mouseClicked(center, center, 0) && mc.screen instanceof DataTerminalGui,
                        "scaled highlighted back button did not return to terminal");
                draw.invoke(tutorial, graphics, 105, 105);
                require(graphics.backColor == 0xFF2A3A4E, "outside scaled back button still highlighted");
            }
        } finally { mc.setScreen(oldScreen); }
        evidence.add("PASS tutorial actual back-button draw/click share scaled bounds at 0.5x and 2x; outside point rejected");

        Field latest = field(LocationTeleportGui.class, "latest");
        Object previousLocations = latest.get(null);
        var locations = new ArrayList<TeleportLocation>();
        for (int i = 0; i < 8; i++) locations.add(new TeleportLocation("audit"+i, "minecraft:overworld", i, 80, 0));
        try {
            latest.set(null, List.copyOf(locations));
            var gui = new LocationTeleportGui();
            gui.init(mc, 320, 180);
            graphics.rows.clear();
            gui.render(graphics, 0, 0, 0);
            EditBox name = (EditBox) field(LocationTeleportGui.class, "name").get(gui);
            int visible = field(LocationTeleportGui.class, "visibleRows").getInt(gui);
            require(visible == 3 && graphics.rows.size() == 3, "small location screen did not render exactly three rows");
            for (int bottom : graphics.rows) require(bottom < name.getY(), "location row overlaps footer");
            var splitMethod = LocationTeleportGui.class.getDeclaredMethod("split"); splitMethod.setAccessible(true);
            int split = (int) splitMethod.invoke(gui);
            require(name.getX()+name.getWidth()+6+18 < split-38, "add/name controls overlap page controls");
            gui.mouseClicked(split-12, name.getY()+8, 0);
            require(field(LocationTeleportGui.class,"page").getInt(gui)==1, "location next page is not reachable");
            gui.mouseScrolled(100,100,0,-100);
            gui.mouseScrolled(100,100,0,-100);
            graphics.rows.clear(); gui.render(graphics,0,0,0);
            require(field(LocationTeleportGui.class,"page").getInt(gui)==2 && graphics.rows.size()==2,
                    "last location page lost entries after resize");
            evidence.add("PASS location actual 320x180 render/click/wheel: rows avoid footer, controls separate, all eight entries reachable");
        } finally { latest.set(null, previousLocations); }
        graphics.flush();
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field field=type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static final class ProbeGraphics extends GuiGraphics {
        int backColor;
        final List<Integer> rows=new ArrayList<>();
        ProbeGraphics(Minecraft mc, MultiBufferSource.BufferSource buffers){super(mc,buffers);}
        @Override public void fill(int left,int top,int right,int bottom,int color){
            if(left==-20 && top==-20 && right==-2 && bottom==-2) backColor=color;
            if(color==0x50373F43 || color==0x4031373A || color==0x704C6670)rows.add(bottom);
            super.fill(left,top,right,bottom,color);
        }
    }
}
