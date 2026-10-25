package com.mohistmc.academy.regression;

import com.mohistmc.academy.client.SkillInputClientState;
import com.mohistmc.academy.client.block.entity.render.CatEngineRender;
import com.mohistmc.academy.listener.ClientListener;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.block.entity.CatEngineBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.joml.Matrix4f;

/** Real client classes, isolated state, no network sends or world mutations. */
final class ClientSecondaryAuditChecks {
    private ClientSecondaryAuditChecks() {}

    static void sessions(Minecraft mc, List<String> evidence) throws Exception {
        var saved = new LinkedHashMap<Field, Object>();
        for (String name : List.of("session", "lastSession", "connection", "revision", "sequence", "retryTicks")) {
            Field field = SkillInputClientState.class.getDeclaredField(name);
            field.setAccessible(true); saved.put(field, field.get(null));
        }
        try {
            SkillInputClientState.disconnect();
            var connection = mc.getConnection().getConnection();
            var dimension = mc.level.dimension().location();
            UUID current = UUID.randomUUID();
            SkillInputClientState.accept(connection, current, 10, dimension);
            require(SkillInputClientState.ready(), "positive session admission failed");
            long sequence = SkillInputClientState.next().sequence();
            SkillInputClientState.clear();
            SkillInputClientState.accept(connection, UUID.randomUUID(), 9, dimension);
            require(!SkillInputClientState.ready(), "dimension reset admitted stale revision");
            SkillInputClientState.accept(connection, current, 10, dimension);
            require(SkillInputClientState.ready(), "same revision retry was rejected");
            ClientListener.onLoggingOut(null);
            require(!SkillInputClientState.ready(), "logout retained ready session");
            for (var field : saved.keySet()) {
                Object value = field.get(null);
                if (List.of("connection", "lastSession", "session").contains(field.getName()))
                    require(value == null, "logout retained " + field.getName());
                if (field.getName().equals("revision")) require((long) value == 0, "logout retained revision");
                if (field.getName().equals("sequence")) require((long) value == sequence, "logout reset monotonic sequence");
            }
            evidence.add("PASS client session: dimension reset rejects stale ack; actual logout releases connection/session/revision and preserves request sequence");
        } finally {
            for (var item : saved.entrySet()) item.getKey().set(null, item.getValue());
        }
    }

    static void catRender(Minecraft mc, PoseStack pose, MultiBufferSource.BufferSource buffers,
                          List<String> evidence) throws Exception {
        interferer(mc, buffers, evidence);
        overlays(mc, buffers, evidence);
        mediaClicks(mc, evidence);
        audioConstruction(mc, evidence);
        developerEnergy(mc, evidence);
        clippedSettings(mc, buffers, evidence);
        longSkillDropdown(mc, buffers, evidence);
        readonlySkillDetails(mc, buffers, evidence);
        var engine = new CatEngineBlockEntity(BlockPos.ZERO, AcademyBlocks.CAT_ENGINE.get().defaultBlockState());
        engine.setLevel(mc.level);
        CompoundTag generation = new CompoundTag(); generation.putDouble("thisTickGeneration", 500);
        engine.loadAdditional(generation, mc.level.registryAccess());
        CatEngineBlockEntity.tickAnim(mc.level, BlockPos.ZERO, engine.getBlockState(), engine);
        require(engine.interpolatedSpinDegrees(.5f) == 125, "500 IF/t did not advance 250 degrees per game tick");
        var renderer = new CatEngineRender(null);
        var original = new Matrix4f(pose.last().pose());
        for (int frame = 0; frame < 144; frame++) {
            renderer.render(engine, .5f, pose, buffers, 0xF000F0, 0);
            require(original.equals(pose.last().pose()), "cat renderer leaked pose");
        }
        buffers.endBatch();
        require(engine.interpolatedSpinDegrees(.5f) == 125, "cat animation mutated during render");
        generation.putDouble("thisTickGeneration", 0);
        engine.loadAdditional(generation, mc.level.registryAccess());
        CatEngineBlockEntity.tickAnim(mc.level, BlockPos.ZERO, engine.getBlockState(), engine);
        require(engine.interpolatedSpinDegrees(0) == 250 && engine.interpolatedSpinDegrees(1) == 250,
                "full cat engine continued spinning with zero generation");
        generation.putDouble("thisTickGeneration", 200);
        engine.loadAdditional(generation, mc.level.registryAccess());
        CatEngineBlockEntity.tickAnim(mc.level, BlockPos.ZERO, engine.getBlockState(), engine);
        require(engine.interpolatedSpinDegrees(.5f) == 300, "partial generation did not set actual spin speed");
        evidence.add("PASS cat: 144 actual renders preserve pose/state; 500/0/200 IF/t produce 250/0/100 degrees per tick");
    }

    private static void interferer(Minecraft mc, MultiBufferSource.BufferSource buffers,
                                   List<String> evidence) throws Exception {
        var menu = new com.mohistmc.academy.world.menu.AbilityInterfererMenu(
                37, mc.player.getInventory(), null);
        var gui = new com.mohistmc.academy.client.block.gui.AbilityInterfererGui(
                menu, mc.player.getInventory(), net.minecraft.network.chat.Component.empty());
        gui.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        var labels = gui.getClass().getDeclaredMethod("renderLabels",
                net.minecraft.client.gui.GuiGraphics.class, int.class, int.class);
        labels.setAccessible(true);
        var graphics = new EnergyTextGraphics(mc, buffers);
        int[] incoming = {37, 12400, 8};
        int[] displayed = {37, 10000, 8};
        String[] expectedLabels = {"37/10000 IF", "10000/10000 IF", "8/10000 IF"};
        for (int i = 0; i < incoming.length; i++) {
            int energy = incoming[i];
            menu.setData(com.mohistmc.academy.network.MenuActionToken.Session.WORD_COUNT, energy);
            // 12400 is an over-cap negative control: the menu must clamp it to
            // the legacy 10000 IF capacity, then accept the next lower update.
            require(menu.getEnergy() == displayed[i],
                    "AIM ContainerData balance " + energy + " decoded as " + menu.getEnergy());
            graphics.energy = null;
            labels.invoke(gui, graphics, 0, 0);
            require(expectedLabels[i].equals(graphics.energy),
                    "AIM GUI balance " + energy + " expected '" + expectedLabels[i]
                            + "' but rendered '" + graphics.energy + "'");
        }
        graphics.flush();
        evidence.add("PASS AIM actual renderLabels: ContainerData 37 -> 12400 -> 8 renders 37/10000 IF -> 10000/10000 IF -> 8/10000 IF, including capacity clamp and next discharge without config responses");
    }

    private static final class EnergyTextGraphics extends net.minecraft.client.gui.GuiGraphics {
        String energy;
        EnergyTextGraphics(Minecraft mc, MultiBufferSource.BufferSource buffers) { super(mc, buffers); }
        @Override public int drawString(net.minecraft.client.gui.Font font, String text,
                                        int x, int y, int color, boolean shadow) {
            if (text.endsWith(" IF")) energy = text;
            return super.drawString(font, text, x, y, color, shadow);
        }
    }

    private static void overlays(Minecraft mc, MultiBufferSource.BufferSource buffers,
                                 List<String> evidence) throws Exception {
        var attachment = com.mohistmc.academy.skill.AcademyAttachments.PLAYER_ABILITY;
        var original = mc.player.getData(attachment);
        var config = com.mohistmc.academy.config.ACConfig.Client.KEY_HINT_Y;
        int oldY = config.get();
        boolean oldHud = com.mohistmc.academy.config.ACConfig.Client.SHOW_HUD.get();
        boolean oldHints = com.mohistmc.academy.config.ACConfig.Client.SHOW_KEY_HINTS.get();
        boolean hidden = mc.options.hideGui;
        try {
            var data = new com.mohistmc.academy.skill.PlayerAbilityData();
            data.setCurrentAbility(com.mohistmc.academy.skill.AbilityCategory.ELECTROMASTER);
            data.setAbilityActive(true);
            for (int i = 0; i < 4; i++) data.getCurrentPreset().setSlot(i, "railgun");
            mc.player.setData(attachment, data);
            config.set(1000);
            com.mohistmc.academy.config.ACConfig.Client.SHOW_HUD.set(true);
            com.mohistmc.academy.config.ACConfig.Client.SHOW_KEY_HINTS.set(true);
            mc.options.hideGui = false;
            var graphics = new OverlayGraphics(mc, buffers);
            com.mohistmc.academy.client.gui.AbilityHudOverlay.render(graphics, mc.getTimer());
            require(graphics.icons == 4 && graphics.iconBottom <= mc.getWindow().getGuiScaledHeight(),
                    "four-slot HUD exceeds bottom edge after maximum downward offset");
            for (String name : List.of("a", "r", "g", "b")) {
                Field field = com.mohistmc.academy.client.gui.ScreenMaskOverlay.class.getDeclaredField(name);
                field.setAccessible(true); field.setDouble(null, .2);
            }
            com.mohistmc.academy.client.gui.ScreenMaskOverlay.render(graphics, mc.getTimer());
            require(graphics.maskFullSource, "screen mask cropped/repeated texture instead of sampling full 512x288");
            graphics.flush();
            evidence.add("PASS actual HUD render: four icons remain inside bottom edge; screen mask maps full 512x288 source to current GUI dimensions");
        } finally {
            mc.player.setData(attachment, original); config.set(oldY);
            com.mohistmc.academy.config.ACConfig.Client.SHOW_HUD.set(oldHud);
            com.mohistmc.academy.config.ACConfig.Client.SHOW_KEY_HINTS.set(oldHints);
            mc.options.hideGui = hidden;
            com.mohistmc.academy.client.gui.ScreenMaskOverlay.resetClientSession();
        }
    }

    private static final class OverlayGraphics extends net.minecraft.client.gui.GuiGraphics {
        int icons, iconBottom;
        boolean maskFullSource;
        private final Minecraft mc;
        OverlayGraphics(Minecraft mc, MultiBufferSource.BufferSource buffers) { super(mc, buffers); this.mc = mc; }
        @Override public void renderOutline(int x, int y, int width, int height, int color) {
            if (width == 18 && height == 18) { icons++; iconBottom = Math.max(iconBottom, y + height); }
            super.renderOutline(x, y, width, height, color);
        }
        @Override public void blit(net.minecraft.resources.ResourceLocation texture, int x, int y,
                                   int width, int height, float u, float v, int sourceWidth, int sourceHeight,
                                   int textureWidth, int textureHeight) {
            if (texture.getPath().equals("textures/effects/screen_mask.png")) {
                maskFullSource = x == 0 && y == 0 && width == mc.getWindow().getGuiScaledWidth()
                        && height == mc.getWindow().getGuiScaledHeight() && u == 0 && v == 0
                        && sourceWidth == 512 && sourceHeight == 288 && textureWidth == 512 && textureHeight == 288;
            }
            super.blit(texture, x, y, width, height, u, v, sourceWidth, sourceHeight, textureWidth, textureHeight);
        }
    }

    @SuppressWarnings("unchecked")
    private static void mediaClicks(Minecraft mc, List<String> evidence) throws Exception {
        Class<?> manager = com.mohistmc.academy.client.media.ExternalMediaManager.class;
        Field initialized = manager.getDeclaredField("initialized"), tracksField = manager.getDeclaredField("TRACKS");
        initialized.setAccessible(true); tracksField.setAccessible(true);
        boolean oldInitialized = initialized.getBoolean(null);
        var tracks = (java.util.Map<String, com.mohistmc.academy.terminal.MediaTrack>) tracksField.get(null);
        var savedTracks = new LinkedHashMap<>(tracks);
        double oldVolume = com.mohistmc.academy.config.ACConfig.Client.MEDIA_PLAYER_VOLUME.get();
        var attachment = com.mohistmc.academy.skill.AcademyAttachments.PLAYER_ABILITY;
        var oldData = mc.player.getData(attachment);
        try {
            initialized.setBoolean(null, true);
            var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy_audit", "external_click");
            var external = new com.mohistmc.academy.terminal.MediaTrack("external/audit", "", "", "EXT", id, id,
                    1, mc.gameDirectory.toPath().resolve("unused-audit-silence.ogg"), "Audit", "Audit");
            tracks.put(external.trackId(), external);
            mc.player.setData(attachment, new com.mohistmc.academy.skill.PlayerAbilityData());
            // Zero volume prevents opening any audio stream; the production click/play admission still runs.
            com.mohistmc.academy.config.ACConfig.Client.MEDIA_PLAYER_VOLUME.set(0d);
            var gui = new com.mohistmc.academy.client.gui.MediaPlayerAppGui();
            gui.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            clickTrack(gui, external);
            require(external.trackId().equals(com.mohistmc.academy.client.MediaPlayerManager.getCurrentTrack()),
                    "external row click was incorrectly gated by server disc unlocks");
            com.mohistmc.academy.client.MediaPlayerManager.stop();
            var builtin = com.mohistmc.academy.terminal.MediaTrackRegistry.getAllTracks().iterator().next();
            clickTrack(gui, builtin);
            require(com.mohistmc.academy.client.MediaPlayerManager.getCurrentTrack() == null,
                    "unlocked-only builtin media restriction was bypassed");
            evidence.add("PASS actual media row clicks: external file accepted without disc unlock; locked builtin row remains rejected; zero-volume stream guard used");
        } finally {
            com.mohistmc.academy.client.MediaPlayerManager.stop();
            mc.player.setData(attachment, oldData);
            tracks.clear(); tracks.putAll(savedTracks); initialized.setBoolean(null, oldInitialized);
            com.mohistmc.academy.config.ACConfig.Client.MEDIA_PLAYER_VOLUME.set(oldVolume);
        }
    }

    private static void clickTrack(com.mohistmc.academy.client.gui.MediaPlayerAppGui gui,
                                   com.mohistmc.academy.terminal.MediaTrack track) throws Exception {
        for (var entry : java.util.Map.of("visibleTracks", List.of(track), "hoveredTrack", 0,
                "hoveredTrackId", track.trackId()).entrySet()) {
            Field field = gui.getClass().getDeclaredField(entry.getKey()); field.setAccessible(true);
            field.set(gui, entry.getValue());
        }
        require(gui.mouseClicked(0, 0, 0), "media row click was not consumed");
    }

    private static void audioConstruction(Minecraft mc, List<String> evidence) throws Exception {
        var factory = com.mohistmc.academy.client.MediaPlayerManager.class
                .getDeclaredMethod("openExternalStream", java.io.InputStream.class);
        factory.setAccessible(true);
        var broken = new TrackedInput(new java.io.ByteArrayInputStream(new byte[] {1, 2, 3, 4}));
        try {
            factory.invoke(null, broken);
            throw new IllegalStateException("invalid Ogg unexpectedly decoded");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            require(expected.getCause() instanceof java.io.IOException
                    || expected.getCause() instanceof RuntimeException, "unexpected decoder failure");
            require(broken.closed, "failed JOrbis construction retained the input stream");
        }
        var resource = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                "academy", "sounds/vecmanip/vec_accel.ogg");
        var valid = new TrackedInput(mc.getResourceManager().open(resource));
        try (var stream = (net.minecraft.client.sounds.AudioStream) factory.invoke(null, valid)) {
            require(!valid.closed && stream.getFormat().getSampleRate() > 0,
                    "successful decoder lost ownership before playback");
        }
        require(valid.closed, "successful decoder did not close its owned input");
        evidence.add("PASS actual JOrbis construction: corrupt Ogg closes input; valid bundled Ogg stays open until AudioStream.close");
    }

    private static final class TrackedInput extends java.io.FilterInputStream {
        boolean closed;
        TrackedInput(java.io.InputStream input) { super(input); }
        @Override public void close() throws java.io.IOException { closed = true; super.close(); }
    }

    private static void clippedSettings(Minecraft mc, MultiBufferSource.BufferSource buffers,
                                        List<String> evidence) throws Exception {
        var gui = new com.mohistmc.academy.client.gui.SettingsAppGui();
        gui.init(mc, 320, 240);
        Field rows = gui.getClass().getDeclaredField("rows"), scroll = gui.getClass().getDeclaredField("scrollOffset");
        rows.setAccessible(true); scroll.setAccessible(true);
        var list = (List<?>) rows.get(gui);
        int hudRow = -1;
        for (int i = 0; i < list.size(); i++) {
            var key = list.get(i).getClass().getDeclaredMethod("key"); key.setAccessible(true);
            if (String.valueOf(key.invoke(list.get(i))).equals("SHOW_HUD")) { hudRow = i; break; }
        }
        require(hudRow >= 0, "HUD setting row absent");
        var config = com.mohistmc.academy.config.ACConfig.Client.SHOW_HUD;
        boolean previous = config.get();
        var graphics = new net.minecraft.client.gui.GuiGraphics(mc, buffers);
        try {
            // Row center 44 lies behind the header; the row viewport begins at 49.
            scroll.setInt(gui, 54 + hudRow * 20 + 10 - 44);
            gui.render(graphics, 150, 44, 0);
            gui.mouseClicked(150, 44, 0);
            require(config.get() == previous, "clipped settings row reacted through its header");
            // The same actual row is visible at y=100 and must still toggle.
            scroll.setInt(gui, 54 + hudRow * 20 + 10 - 100);
            gui.render(graphics, 150, 100, 0);
            gui.mouseClicked(150, 100, 0);
            require(config.get() != previous, "visible settings row failed to toggle");
            graphics.flush();
            evidence.add("PASS actual settings render/click: clipped HUD row cannot toggle; same row in viewport toggles");
        } finally { config.set(previous); }
    }

    private static void longSkillDropdown(Minecraft mc, MultiBufferSource.BufferSource buffers,
                                          List<String> evidence) throws Exception {
        var attachment = com.mohistmc.academy.skill.AcademyAttachments.PLAYER_ABILITY;
        var original = mc.player.getData(attachment);
        var data = new com.mohistmc.academy.skill.PlayerAbilityData();
        var category = com.mohistmc.academy.skill.AbilityCategory.all().stream()
                .max(java.util.Comparator.comparingLong(c -> com.mohistmc.academy.skill.SkillRegistry.getSkillsByCategory(c)
                        .stream().filter(s -> s.getType() == com.mohistmc.academy.skill.SkillType.ACTIVE).count())).orElseThrow();
        data.setCurrentAbility(category);
        for (var skill : com.mohistmc.academy.skill.SkillRegistry.getSkillsByCategory(category)) data.learnSkill(skill.getId());
        try {
            mc.player.setData(attachment, data);
            var gui = new com.mohistmc.academy.client.gui.SkillSlotGui();
            gui.init(mc, 320, 180);
            var open = gui.getClass().getDeclaredMethod("openDropdown", int.class, Minecraft.class);
            open.setAccessible(true); open.invoke(gui, 0, mc);
            var draw = gui.getClass().getDeclaredMethod("drawDropdown", net.minecraft.client.gui.GuiGraphics.class, int.class, int.class);
            draw.setAccessible(true);
            var visible = gui.getClass().getDeclaredMethod("dropdownVisibleRows"); visible.setAccessible(true);
            var fields = new java.util.HashMap<String, Field>();
            for (String name : List.of("dropdownEntries", "dropdownX", "dropdownY", "hoveredDropdownItem", "dropdownScroll")) {
                Field field = gui.getClass().getDeclaredField(name); field.setAccessible(true); fields.put(name, field);
            }
            int count = ((List<?>) fields.get("dropdownEntries").get(gui)).size();
            int rows = (int) visible.invoke(gui), x = fields.get("dropdownX").getInt(gui), y = fields.get("dropdownY").getInt(gui);
            require(count > rows && rows * 18 + 4 + y <= 180, "long selector fixture is not bounded to the small screen");
            var graphics = new net.minecraft.client.gui.GuiGraphics(mc, buffers);
            draw.invoke(gui, graphics, x + 8, y + 10);
            require(fields.get("hoveredDropdownItem").getInt(gui) == 0, "first selector row is unreachable");
            require(gui.mouseScrolled(x + 8, y + 10, 0, -100), "actual dropdown wheel event was rejected");
            draw.invoke(gui, graphics, x + 8, y + 2 + (rows - 1) * 18 + 8);
            require(fields.get("hoveredDropdownItem").getInt(gui) == count - 1, "last learned skill is unreachable after scrolling");
            gui.mouseScrolled(x + 8, y + 10, 0, 100);
            require(fields.get("dropdownScroll").getInt(gui) == 0, "selector cannot return to clear/first row");
            graphics.flush();
            evidence.add("PASS actual skill selector at 320x180: first/last of " + count + " entries reachable through wheel hit boxes; viewport stays inside window");
        } finally { mc.player.setData(attachment, original); }
    }

    private static void developerEnergy(Minecraft mc, List<String> evidence) throws Exception {
        var previousScreen = mc.screen;
        BlockPos position = mc.player.blockPosition().offset(1, 3, 1);
        var blockEntities = mc.level.getChunkAt(position).getBlockEntities();
        var previousEntity = blockEntities.get(position);
        var stale = new com.mohistmc.academy.world.block.entity.DevNormalBlockEntity(position,
                AcademyBlocks.DEV_NORMAL.get().defaultBlockState());
        CompoundTag saved = new CompoundTag(); saved.putInt("energy", 3);
        stale.loadAdditional(saved, mc.level.registryAccess());
        UUID nonce = UUID.randomUUID();
        var gui = new com.mohistmc.academy.client.gui.SkillTreeGui(false, false,
                com.mohistmc.academy.world.block.DevMachineType.NORMAL, 10, 50000, position, nonce);
        Field energy = gui.getClass().getDeclaredField("energy"), spent = gui.getClass().getDeclaredField("sessionSpent");
        energy.setAccessible(true); spent.setAccessible(true);
        try {
            blockEntities.put(position, stale);
            gui.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            // Assign only the field: Screen.removed would send a close packet for an artificial nonce.
            mc.screen = gui;
            var buffer = io.netty.buffer.Unpooled.buffer();
            try {
                var packet = new com.mohistmc.academy.network.DevEnergyPacket(nonce, 12000, 50000);
                com.mohistmc.academy.network.DevEnergyPacket.STREAM_CODEC.encode(buffer, packet);
                require(buffer.readableBytes() == 24, "idle developer payload is not 24 bytes");
                com.mohistmc.academy.client.gui.SkillTreeGui.acceptServerEnergy(
                        com.mohistmc.academy.network.DevEnergyPacket.STREAM_CODEC.decode(buffer));
            } finally { buffer.release(); }
            gui.tick();
            require(energy.getInt(gui) == 12000, "GUI tick replaced server energy with stale local BE balance");
            com.mohistmc.academy.client.gui.SkillTreeGui.acceptServerEnergy(
                    new com.mohistmc.academy.network.DevEnergyPacket(UUID.randomUUID(), 1, 50000));
            require(energy.getInt(gui) == 12000, "old developer nonce changed current GUI");
            spent.setBoolean(gui, true);
            com.mohistmc.academy.client.gui.SkillTreeGui.acceptServerEnergy(
                    new com.mohistmc.academy.network.DevEnergyPacket(nonce, 2, 50000));
            require(energy.getInt(gui) == 12000, "committed developer session accepted idle update");
            evidence.add("PASS developer client: 24-byte energy codec, current nonce accepted, stale nonce/spent session rejected, tick preserves authority over stale BE");
        } finally {
            mc.screen = previousScreen;
            if (previousEntity == null) blockEntities.remove(position); else blockEntities.put(position, previousEntity);
        }
    }

    private static void readonlySkillDetails(Minecraft mc, MultiBufferSource.BufferSource buffers,
                                            List<String> evidence) throws Exception {
        var attachment = com.mohistmc.academy.skill.AcademyAttachments.PLAYER_ABILITY;
        var original = mc.player.getData(attachment);
        try {
            for (boolean learned : new boolean[] {false, true}) {
                var data = new com.mohistmc.academy.skill.PlayerAbilityData();
                data.setCurrentAbility(com.mohistmc.academy.skill.AbilityCategory.ELECTROMASTER);
                if (learned) for (var skill : com.mohistmc.academy.skill.SkillRegistry.getSkillsByCategory(data.getCurrentAbility()))
                    data.learnSkill(skill.getId());
                mc.player.setData(attachment, data);
                var gui = new com.mohistmc.academy.client.gui.SkillTreeGui(true, true);
                gui.init(mc, 320, 240);
                Field nodes = gui.getClass().getDeclaredField("skillNodes"), hover = gui.getClass().getDeclaredField("hoveredNode");
                nodes.setAccessible(true); hover.setAccessible(true);
                var node = ((List<?>) nodes.get(gui)).getFirst();
                hover.set(gui, node);
                var x = node.getClass().getDeclaredMethod("x"); x.setAccessible(true);
                var y = node.getClass().getDeclaredMethod("y"); y.setAccessible(true);
                require(gui.mouseClicked((int) x.invoke(node) + 2, (int) y.invoke(node) + 2, 0), "read-only skill click rejected");
                var graphics = new DetailGraphics(mc, buffers);
                gui.render(graphics, 0, 0, 0);
                require(graphics.detailCover, "read-only " + (learned ? "learned" : "unlearned") + " detail was never rendered");
                Field actionWidth = gui.getClass().getDeclaredField("actionWidth"); actionWidth.setAccessible(true);
                require(actionWidth.getInt(gui) == 0, "read-only terminal exposed a learning action");
                graphics.flush();
            }
            evidence.add("PASS terminal actual skill click/render: learned and unlearned details display without developer type; no learning button");
        } finally { mc.player.setData(attachment, original); }
    }

    private static final class DetailGraphics extends net.minecraft.client.gui.GuiGraphics {
        boolean detailCover;
        DetailGraphics(Minecraft mc, MultiBufferSource.BufferSource buffers) { super(mc, buffers); }
        @Override public void fill(int left, int top, int right, int bottom, int color) {
            if (left == 0 && top == 0 && right == 320 && bottom == 240 && color == 0xB8000000) detailCover = true;
            super.fill(left, top, right, bottom, color);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
