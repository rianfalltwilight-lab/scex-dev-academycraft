package com.mohistmc.academy.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mohistmc.academy.AcademyCraft;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The datapack sync round trip that clients rely on. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class DataSyncGameTests {
    private DataSyncGameTests() {}

    /** Records what it was given; throws on a "fail" field to exercise the fallback. */
    private static final class Probe extends SyncedJsonData {
        Map<ResourceLocation, JsonElement> seen = Map.of();
        Probe() { super("probe", "academy_probe"); }
        @Override protected void accept(Map<ResourceLocation, JsonElement> files) {
            if (files.values().stream().anyMatch(e -> e.isJsonObject() && e.getAsJsonObject().has("fail")))
                throw new IllegalArgumentException("bad probe data");
            seen = new TreeMap<>(files);
        }
    }

    @GameTest(template = "empty")
    public static void snapshotRoundTripsAndBadDataFallsBack(GameTestHelper helper) {
        Probe server = new Probe();
        ResourceLocation a = ResourceLocation.fromNamespaceAndPath("academy", "railgun");
        ResourceLocation b = ResourceLocation.fromNamespaceAndPath("mypack", "x/y");
        server.apply(Map.of(a, JsonParser.parseString("{\"cp\":0.5}"), b, JsonParser.parseString("[1,2]")), null, null);
        Probe client = new Probe();
        client.acceptSnapshot(server.snapshot());
        if (!client.seen.equals(server.seen) || client.seen.size() != 2) {
            helper.fail("client did not receive the server's files: " + client.seen); return;
        }
        server.apply(Map.of(a, JsonParser.parseString("{\"fail\":true}")), null, null);
        if (!server.seen.isEmpty() || !"{}".equals(server.snapshot())) {
            helper.fail("invalid data did not fall back to defaults"); return;
        }
        client.reset();
        if (!client.seen.isEmpty()) { helper.fail("reset did not restore defaults"); return; }
        helper.succeed();
    }
}
