package com.mohistmc.academy.regression;

import com.mohistmc.academy.crafting.MetalFormerRecipes;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.block.Matrix;
import com.mohistmc.academy.world.block.entity.MatrixBlockEntity;
import com.mohistmc.academy.world.block.entity.MetalFomerBlockEntity;
import com.mohistmc.academy.world.menu.MatrixMenu;
import com.mohistmc.academy.world.menu.MetalFomerMenu;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/** Mutations run on the integrated server; assertions read only real client packet results. */
final class MachineInventoryClientChecks {
    private static int phase, waitTicks;
    private static boolean complete;
    private static CompletableFuture<Void> operation;
    private static Throwable failure;
    private static BlockPos metalPos, matrixPos;

    private MachineInventoryClientChecks() {}

    static boolean tick(Minecraft mc, List<String> evidence) {
        if (complete) return true;
        if (failure != null) {
            if (!operation.isDone() && ++waitTicks <= 200) return false;
            if (operation.isDone()) {
                try { operation.getNow(null); }
                catch (Throwable cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            }
            throw new IllegalStateException("Machine inventory client sync failed", failure);
        }
        try {
            if (operation == null) {
                int requested = phase;
                operation = server(mc, player -> change(player, requested));
                waitTicks = 0;
            }
            if (++waitTicks > 200) throw new IllegalStateException("Machine sync timeout phase=" + phase + " " + state(mc));
            if (!operation.isDone()) return false;
            operation.getNow(null);
            if (phase == 6) { complete = true; return true; }
            if (!matches(mc)) return false;
            evidence.add("PASS machine native menu/world packet phase=" + phase + ": " + state(mc));
            phase++; operation = null;
            return false;
        } catch (Throwable problem) {
            failure = problem;
            operation = server(mc, MachineInventoryClientChecks::cleanup);
            waitTicks = 0;
            return false;
        }
    }

    private static void change(ServerPlayer player, int requested) {
        var level = player.serverLevel();
        switch (requested) {
            case 0 -> {
                metalPos = player.blockPosition().offset(3, 1, 0);
                require(level.isEmptyBlock(metalPos), "Metal former fixture target must be empty");
                var state = AcademyBlocks.METAL_FORMER.get().defaultBlockState();
                level.setBlock(metalPos, state, Block.UPDATE_ALL);
                require(level.getBlockEntity(metalPos) instanceof MetalFomerBlockEntity, "Machine fixture placement failed");
                player.openMenu(state.getMenuProvider(level, metalPos), buffer -> buffer.writeBlockPos(metalPos));
            }
            case 1 -> {
                require(player.containerMenu instanceof MetalFomerMenu, "Metal former menu closed before slot mutation");
                ((MetalFomerMenu) player.containerMenu).container.setItem(0, new ItemStack(Items.IRON_INGOT, 32));
                // No battery or machine energy: the input cannot be consumed while packets are observed.
            }
            case 2 -> {
                var machine = (MetalFomerBlockEntity) level.getBlockEntity(metalPos);
                require(machine != null && machine.getEnergy() == 0, "Unpowered fixture unexpectedly acquired energy");
                machine.cycleMode(1);
                require(machine.getMode() == MetalFormerRecipes.Mode.INCISE, "Fixture mode transition failed");
                // Exercise an actual native world update after the menu has received its inventory.
                level.sendBlockUpdated(metalPos, machine.getBlockState(), machine.getBlockState(), Block.UPDATE_CLIENTS);
            }
            case 3 -> {
                player.closeContainer();
                matrixPos = player.blockPosition().offset(-3, 1, 0);
                var state = AcademyBlocks.MATRIX.get().defaultBlockState();
                require(level.isEmptyBlock(matrixPos) && Matrix.structurePositions(matrixPos, state).stream().allMatch(level::isEmptyBlock),
                        "Matrix fixture footprint must be empty");
                level.setBlock(matrixPos, state, Block.UPDATE_ALL);
                ((Matrix) state.getBlock()).createStructure(level, matrixPos, state);
                var matrix = (MatrixBlockEntity) level.getBlockEntity(matrixPos);
                require(matrix != null, "Matrix fixture placement failed");
                matrix.setOwnerUUID(player.getUUID());
                player.openMenu(state.getMenuProvider(level, matrixPos),
                        buffer -> MatrixMenu.writeOpeningData(buffer, matrixPos, matrix, player));
            }
            case 4 -> {
                require(player.containerMenu instanceof MatrixMenu, "Matrix menu closed before material insertion");
                var menu = (MatrixMenu) player.containerMenu;
                for (int i = 0; i < 3; i++) menu.container.setItem(i, new ItemStack(AcademyItems.CONSTRAINT_PLATE.get()));
                menu.container.setItem(3, new ItemStack(AcademyItems.MAT_CORE_2.get()));
                require(((MatrixBlockEntity) level.getBlockEntity(matrixPos)).hasInitializationMaterials(),
                        "Server matrix did not recognize the installed materials");
            }
            case 5 -> {
                require(player.containerMenu instanceof MatrixMenu, "Matrix menu closed before material removal");
                ((MatrixMenu) player.containerMenu).container.removeItem(0, 1);
            }
            default -> cleanup(player);
        }
    }

    private static boolean matches(Minecraft mc) {
        if (phase <= 2) {
            if (!(mc.player.containerMenu instanceof MetalFomerMenu menu)
                    || !(mc.level.getBlockEntity(metalPos) instanceof MetalFomerBlockEntity machine)) return false;
            if (phase == 0) return menu.getSlot(36).getItem().isEmpty();
            if (!menu.getSlot(36).getItem().is(Items.IRON_INGOT) || menu.getSlot(36).getItem().getCount() != 32) return false;
            require(machine.getItems().stream().allMatch(ItemStack::isEmpty), "Client BE incorrectly received private machine inventory");
            return phase == 1 || (machine.getMode() == MetalFormerRecipes.Mode.INCISE && menu.getMode() == MetalFormerRecipes.Mode.INCISE);
        }
        if (!(mc.player.containerMenu instanceof MatrixMenu menu)
                || !(mc.level.getBlockEntity(matrixPos) instanceof MatrixBlockEntity matrix)) return false;
        if (phase == 3) return menu.getSlot(39).getItem().isEmpty() && !matrix.hasInitializationMaterials();
        require(matrix.getItems().stream().allMatch(ItemStack::isEmpty), "Matrix rendering snapshot contains real inventory stacks");
        if (!menu.getSlot(39).getItem().is(AcademyItems.MAT_CORE_2.get())
                || !menu.getSlot(37).getItem().is(AcademyItems.CONSTRAINT_PLATE.get())
                || !menu.getSlot(38).getItem().is(AcademyItems.CONSTRAINT_PLATE.get())) return false;
        return phase == 4 ? menu.getSlot(36).getItem().is(AcademyItems.CONSTRAINT_PLATE.get())
                        && menu.hasInitializationMaterials() && matrix.hasInitializationMaterials() && matrix.initializationCoreLevel() == 2
                : menu.getSlot(36).getItem().isEmpty() && !menu.hasInitializationMaterials()
                        && !matrix.hasInitializationMaterials() && matrix.initializationCoreLevel() == 2;
    }

    private static String state(Minecraft mc) {
        if (phase <= 2 && mc.player.containerMenu instanceof MetalFomerMenu menu) {
            var machine = mc.level.getBlockEntity(metalPos);
            return "metal slot=" + menu.getSlot(36).getItem().getCount() + ", menuMode=" + menu.getMode()
                    + ", worldMode=" + (machine instanceof MetalFomerBlockEntity be ? be.getMode() : "missing");
        }
        if (mc.player.containerMenu instanceof MatrixMenu menu) {
            var matrix = mc.level.getBlockEntity(matrixPos);
            return "matrix slots=" + menu.getSlot(36).getItem().getCount() + "," + menu.getSlot(37).getItem().getCount()
                    + "," + menu.getSlot(38).getItem().getCount() + "," + menu.getSlot(39).getItem().getCount()
                    + ", menuMaterials=" + menu.hasInitializationMaterials()
                    + ", worldMaterials=" + (matrix instanceof MatrixBlockEntity be && be.hasInitializationMaterials());
        }
        return "menu=" + mc.player.containerMenu.getClass().getSimpleName();
    }

    private static void cleanup(ServerPlayer player) {
        player.closeContainer();
        var level = player.serverLevel();
        if (metalPos != null && level.getBlockEntity(metalPos) instanceof MetalFomerBlockEntity metal) {
            metal.getItems().clear(); level.removeBlock(metalPos, false);
        }
        if (matrixPos != null && level.getBlockEntity(matrixPos) instanceof MatrixBlockEntity matrix) {
            matrix.getItems().clear(); level.removeBlock(matrixPos, false);
        }
    }
    private static CompletableFuture<Void> server(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        require(server != null, "Machine check needs the existing isolated integrated server");
        var id = mc.player.getUUID();
        return server.submit(() -> {
            var player = server.getPlayerList().getPlayer(id);
            require(player != null, "Integrated server player disappeared");
            action.accept(player);
        });
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
