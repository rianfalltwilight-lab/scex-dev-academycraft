package com.mohistmc.academy.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A datapack directory of JSON files that is reloaded with {@code /reload} and mirrored to clients.
 *
 * <p>Subclasses parse the files in {@link #accept}; it runs on the server after a reload and on the
 * client when the sync payload arrives, and with an empty map to restore the built-in defaults (on
 * client disconnect, or when a parse fails).  Values must therefore be read through the subclass's
 * own accessors, never cached elsewhere.
 */
public abstract class SyncedJsonData extends SimpleJsonResourceReloadListener {
    protected static final Logger LOGGER = LoggerFactory.getLogger("AcademyCraft/Data");
    private static final Gson GSON = new Gson();
    private final String id;
    private Map<ResourceLocation, JsonElement> current = Map.of();

    protected SyncedJsonData(String id, String directory) {
        super(GSON, directory);
        this.id = id;
    }

    /** Stable id used in the sync payload. */
    public final String id() { return id; }

    /** Parse and install the given files (sorted by id); an empty map means built-in defaults. */
    protected abstract void accept(Map<ResourceLocation, JsonElement> files);

    @Override
    protected final void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        install(new TreeMap<>(files));
    }

    private void install(Map<ResourceLocation, JsonElement> files) {
        try {
            accept(files);
            current = Map.copyOf(files);
            if (!files.isEmpty()) LOGGER.info("Loaded {} {} file(s)", files.size(), id);
        } catch (RuntimeException e) {
            LOGGER.error("Invalid {} data, falling back to built-in defaults", id, e);
            accept(Map.of());
            current = Map.of();
        }
    }

    /** The files currently installed (tests restore them after injecting their own). */
    protected final Map<ResourceLocation, JsonElement> installed() { return current; }

    /** The installed files as one JSON object, for the sync payload. */
    final String snapshot() {
        JsonObject out = new JsonObject();
        current.forEach((key, value) -> out.add(key.toString(), value));
        return GSON.toJson(out);
    }

    /** Client side: install what the server sent. */
    final void acceptSnapshot(String json) {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
        JsonParser.parseString(json).getAsJsonObject().entrySet().forEach(entry -> {
            ResourceLocation key = ResourceLocation.tryParse(entry.getKey());
            if (key != null) files.put(key, entry.getValue());
        });
        install(new TreeMap<>(files));
    }

    /** Back to built-in defaults (client disconnect). */
    final void reset() {
        accept(Map.of());
        current = Map.of();
    }
}
