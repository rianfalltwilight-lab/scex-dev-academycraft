package com.mohistmc.academy.regression;

import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.block.entity.AcademyContainerBlockEntity;
import com.mohistmc.academy.world.block.entity.MatrixBlockEntity;
import com.mohistmc.academy.world.block.entity.MetalFomerBlockEntity;
import com.mohistmc.academy.world.menu.MetalFomerMenu;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.payload.AdvancedContainerSetDataPayload;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Runtime coverage of native menu packets and the distinct chunk-render snapshot. */
@GameTestHolder("academy_data_sync")
@PrefixGameTestTemplate(false)
public final class MachineInventorySyncGameTests {
    @GameTest(template = "empty", batch = "data_sync_machine", timeoutTicks = 40)
    public static void allMachineInventorySavesRemainCompleteButTrackingTagsExcludeContents(GameTestHelper h) {
        List<Block> machines = List.of(AcademyBlocks.METAL_FORMER.get(), AcademyBlocks.IMAG_FUSOR.get(),
                AcademyBlocks.PHASE_GEN.get(), AcademyBlocks.SOLAR_GEN.get(), AcademyBlocks.WINDGEN_BASE.get(),
                AcademyBlocks.WINDGEN_MAIN.get(), AcademyBlocks.MATRIX.get(), AcademyBlocks.NODE_BASIC.get(),
                AcademyBlocks.NODE_STANDARD.get(), AcademyBlocks.NODE_ADVANCED.get(),
                AcademyBlocks.DEV_NORMAL.get(), AcademyBlocks.DEV_ADVANCED.get(),
                AcademyBlocks.ABILITY_INTERFERER.get(), AcademyBlocks.RF_INPUT.get(), AcademyBlocks.RF_OUTPUT.get());
        int slots = 0;
        for (Block block : machines) {
            var state = block.defaultBlockState();
            var pos = h.absolutePos(new BlockPos(1, 2, 1));
            var machine = (AcademyContainerBlockEntity) ((EntityBlock) block).newBlockEntity(pos, state);
            h.assertTrue(machine != null, "Machine factory did not construct the registered block entity");
            for (int i = 0; i < machine.getContainerSize(); i++) {
                ItemStack stack = new ItemStack(block == AcademyBlocks.ABILITY_INTERFERER.get()
                        ? AcademyItems.ENERGY_UNIT.get() : Items.IRON_INGOT, i + 1);
                stack.set(DataComponents.CUSTOM_NAME, Component.literal("saved-slot-" + i));
                CompoundTag custom = new CompoundTag(); custom.putString("owner_note", "private-inventory-data");
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
                machine.getItems().set(i, stack);
                slots++;
            }
            CompoundTag disk = machine.saveWithFullMetadata(h.getLevel().registryAccess());
            var restored = (AcademyContainerBlockEntity) BlockEntity.loadStatic(pos, state, disk, h.getLevel().registryAccess());
            h.assertTrue(restored != null && restored.getItems().size() == machine.getContainerSize(),
                    "Native block entity reload lost inventory size for " + block);
            for (int i = 0; i < machine.getContainerSize(); i++)
                h.assertTrue(ItemStack.matches(restored.getItems().get(i), machine.getItems().get(i)),
                        "Native save/reload lost count or components for " + block + " slot " + i);
            h.assertTrue(disk.contains("contentItems") && !machine.getUpdateTag(h.getLevel().registryAccess()).contains("contentItems"),
                    "World snapshot and complete persistent inventory were not separated for " + block);
        }
        h.assertTrue(slots > 20, "Inventory fixture did not exercise the real machine slot families");
        LogUtils.getLogger().info("[academy_data_sync:machine] persistence machines={} slots={} tracking_inventory_tags=0", machines.size(), slots);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "data_sync_machine", timeoutTicks = 60)
    public static void twoRealViewersReceiveOnlyChangedSlotsAndClosedMenusStopReceiving(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(3, 2, 2));
        h.getLevel().setBlock(pos, AcademyBlocks.METAL_FORMER.get().defaultBlockState(), 3);
        var machine = (MetalFomerBlockEntity) h.getLevel().getBlockEntity(pos);
        try (Peer first = new Peer(h); Peer second = new Peer(h); Peer observer = new Peer(h)) {
            MetalFomerMenu a = first.open(pos), b = second.open(pos);
            h.assertTrue(countFull(first.drain()) == 1 && countFull(second.drain()) == 1,
                    "Opening did not produce exactly one native full container snapshot per viewer");
            observer.drain();
            a.container.setItem(0, new ItemStack(Items.IRON_INGOT, 12));
            a.broadcastChanges(); b.broadcastChanges();
            var p1 = first.drain(); var p2 = second.drain();
            var slot1 = onlySlot(h, p1, a.containerId, 36, 12);
            var slot2 = onlySlot(h, p2, b.containerId, 36, 12);
            h.assertTrue(countFull(p1) == 0 && countFull(p2) == 0 && countSlots(observer.drain()) == 0,
                    "Inventory mutation resent a full snapshot or reached a non-viewer");
            a.broadcastChanges(); b.broadcastChanges();
            h.assertTrue(countSlots(first.drain()) == 0 && countSlots(second.drain()) == 0,
                    "Idle menus repeated unchanged slot packets");
            machine.injectEnergy(1250);
            a.broadcastChanges(); b.broadcastChanges();
            var data1 = first.drain(); var data2 = second.drain();
            h.assertTrue(hasData(data1, a.containerId, 1250) && hasData(data2, b.containerId, 1250)
                            && countFull(data1) == 0 && countFull(data2) == 0 && countSlots(data1) == 0 && countSlots(data2) == 0,
                    "Energy change did not use native per-viewer data slots or resent inventory");
            a.broadcastChanges(); b.broadcastChanges();
            h.assertTrue(!hasData(first.drain(), a.containerId, 1250) && !hasData(second.drain(), b.containerId, 1250)
                            && !hasData(observer.drain(), a.containerId, 1250),
                    "Unchanged energy data repeated or was sent to a non-viewer");

            a.clicked(36, 0, ClickType.PICKUP, first.player);
            a.broadcastChanges(); b.broadcastChanges();
            h.assertTrue(machine.getItems().get(0).isEmpty() && a.getCarried().getCount() == 12,
                    "Native pickup did not preserve the inventory/cursor total");
            onlySlot(h, second.drain(), b.containerId, 36, 0);
            first.drain();
            first.player.closeContainer(); first.drain();
            b.container.setItem(0, new ItemStack(Items.IRON_INGOT, 7));
            b.broadcastChanges();
            onlySlot(h, second.drain(), b.containerId, 36, 7);
            h.assertTrue(countSlots(first.drain()) == 0 && countSlots(observer.drain()) == 0,
                    "A closed or absent viewer still received machine slots");

            var disk = machine.saveWithFullMetadata(h.getLevel().registryAccess());
            h.getLevel().removeBlockEntity(pos);
            var replacement = BlockEntity.loadStatic(pos, machine.getBlockState(), disk, h.getLevel().registryAccess());
            h.assertTrue(replacement instanceof MetalFomerBlockEntity, "Native reload did not restore machine type");
            h.getLevel().setBlockEntity(replacement);
            h.assertTrue(!b.stillValid(second.player)
                            && ((MetalFomerBlockEntity) replacement).getItems().get(0).getCount() == 7,
                    "Block entity replacement reused stale menu authority or lost the final saved inventory");
            LogUtils.getLogger().info("[academy_data_sync:machine] two_viewers changed_slot_packets=2 encoded_slot_body_bytes={} idle_slot_packets=0 observer_slot_packets=0 closed_viewer_slot_packets=0",
                    slotBytes(h, slot1) + slotBytes(h, slot2));
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "data_sync_machine", timeoutTicks = 40)
    public static void matrixAppearancePacketContainsOnlyMaterialSummaryAndIsSmallerThanInventorySnapshot(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        var matrix = new MatrixBlockEntity(pos, AcademyBlocks.MATRIX.get().defaultBlockState());
        matrix.setLevel(h.getLevel());
        matrix.getItems().set(3, new ItemStack(AcademyItems.MAT_CORE_2.get()));
        for (int i = 0; i < 3; i++) matrix.getItems().set(i, new ItemStack(AcademyItems.CONSTRAINT_PLATE.get()));
        CompoundTag privateData = new CompoundTag(); privateData.putString("private_note", "inventory-only-".repeat(128));
        matrix.getItems().get(3).set(DataComponents.CUSTOM_DATA, CustomData.of(privateData));
        CompoundTag tag = matrix.getUpdateTag(h.getLevel().registryAccess());
        h.assertTrue(tag.getBoolean("render_has_materials") && tag.getInt("render_core_level") == 2
                        && !tag.contains("contentItems") && !tag.toString().contains("private_note"),
                "Matrix compact visual state is incomplete or exposes inventory components");
        h.assertTrue(!matrix.saveWithFullMetadata(h.getLevel().registryAccess()).contains("render_has_materials"),
                "Ephemeral visual summary leaked into the persistent schema");
        var actual = ClientboundBlockEntityDataPacket.create(matrix);
        var before = ClientboundBlockEntityDataPacket.create(matrix, (be, registries) -> {
            CompoundTag old = matrix.getUpdateTag(registries);
            old.remove("render_core_level"); old.remove("render_has_materials");
            matrix.serializeContentItems(old, registries);
            return old;
        });
        int currentBytes = blockBytes(h, actual), oldBytes = blockBytes(h, before);
        h.assertTrue(currentBytes < oldBytes, "Visual packet did not shrink after removing inventory NBT");
        matrix.getItems().set(0, ItemStack.EMPTY);
        h.assertTrue(!matrix.getUpdateTag(h.getLevel().registryAccess()).getBoolean("render_has_materials"),
                "Removing a required plate did not turn off the world appearance");
        LogUtils.getLogger().info("[academy_data_sync:machine] matrix_encoded_body_bytes={} baseline_0_0_30_inventory_body_bytes={} saved_component_bytes_preserved=true",
                currentBytes, oldBytes);
        h.succeed();
    }

    private static int blockBytes(GameTestHelper h, ClientboundBlockEntityDataPacket packet) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try { ClientboundBlockEntityDataPacket.STREAM_CODEC.encode(buffer, packet); return buffer.readableBytes(); }
        finally { buffer.release(); }
    }
    private static int slotBytes(GameTestHelper h, ClientboundContainerSetSlotPacket packet) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try { ClientboundContainerSetSlotPacket.STREAM_CODEC.encode(buffer, packet); return buffer.readableBytes(); }
        finally { buffer.release(); }
    }
    private static long countFull(List<Object> packets) { return packets.stream().filter(ClientboundContainerSetContentPacket.class::isInstance).count(); }
    private static long countSlots(List<Object> packets) { return packets.stream().filter(ClientboundContainerSetSlotPacket.class::isInstance).count(); }
    private static boolean hasData(List<Object> packets, int container, int value) {
        return packets.stream().anyMatch(packet -> packet instanceof ClientboundContainerSetDataPacket data
                && data.getContainerId() == container && data.getValue() == value
                || packet instanceof ClientboundCustomPayloadPacket custom
                && custom.payload() instanceof AdvancedContainerSetDataPayload advanced
                && Byte.toUnsignedInt(advanced.containerId()) == container && advanced.value() == value);
    }
    private static ClientboundContainerSetSlotPacket onlySlot(GameTestHelper h, List<Object> packets, int id, int slot, int count) {
        var matching = packets.stream().filter(ClientboundContainerSetSlotPacket.class::isInstance)
                .map(ClientboundContainerSetSlotPacket.class::cast)
                .filter(p -> p.getContainerId() == id && p.getSlot() == slot).toList();
        h.assertTrue(matching.size() == 1 && matching.getFirst().getItem().getCount() == count,
                "Expected one native machine slot change to count " + count + ", received " + matching.size());
        return matching.getFirst();
    }

    private static final class Peer implements AutoCloseable {
        final ServerPlayer player;
        final Connection connection;
        final EmbeddedChannel channel;
        Peer(GameTestHelper h) {
            var profile = new GameProfile(UUID.randomUUID(), "Machine" + UUID.randomUUID().toString().substring(0, 7));
            var cookie = new CommonListenerCookie(profile, 0, ClientInformation.createDefault(), false, ConnectionType.NEOFORGE);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, cookie.clientInformation());
            connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
            var pos = h.absolutePos(new BlockPos(2, 2, 2)); player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            drain();
        }
        MetalFomerMenu open(BlockPos pos) {
            player.openMenu(new SimpleMenuProvider((id, inv, viewer) -> {
                var data = new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(pos);
                try { return new MetalFomerMenu(id, inv, data); } finally { data.release(); }
            }, Component.literal("Machine sync check")), buffer -> buffer.writeBlockPos(pos));
            return (MetalFomerMenu) player.containerMenu;
        }
        List<Object> drain() {
            channel.runPendingTasks();
            var result = new ArrayList<Object>(); Object packet;
            while ((packet = channel.readOutbound()) != null) {
                result.add(packet); io.netty.util.ReferenceCountUtil.release(packet);
            }
            return result;
        }
        @Override public void close() {
            player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
    }
}
