package com.reachingrandom.mc.poi.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;

public class PoiConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("points-of-interest");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    private static final Path POI_DIR = CONFIG_DIR.resolve("poi");

    private static PoiConfig instance;
    private static volatile boolean loggedApiOverride;
    private transient Path currentFilePath;

    // ── Serialized fields ──────────────────────────────────────────────────────

    public String apiBaseUrl = "https://reachingrandom.com";
    public String apiKey = "";
    public String currentWorldId = null;
    public String currentGroupId = null;
    public String currentGroupName = null;
    public List<String> trackedPoiIds = new ArrayList<>();

    /**
     * Which POI world belongs to which Minecraft world, keyed by place
     * ({@code sp:<save folder>:<seed>}, {@code mp:<server address>}, ...).
     * {@link #currentWorldId} is re-resolved from this on every join.
     */
    public Map<String, String> worldBindings = new HashMap<>();

    /** Tracked POIs and group focus of the POI worlds that are not currently selected, by world ID. */
    public Map<String, WorldState> worldStates = new HashMap<>();

    /**
     * 0 = profile written before world bindings existed; its {@link #currentWorldId}
     * is not tied to any place. See {@link #takeLegacyWorldId()}.
     */
    public int bindingsVersion = 0;

    public static class WorldState {
        public List<String> trackedPoiIds = new ArrayList<>();
        public String groupId;
        public String groupName;
    }

    /** Whether placed/right-clicked campfires can be named into the "Campsites" group. */
    public boolean campfireCampsites = true;

    /**
     * Storage mode: {@code "online"} (uses the reaching-random API) or
     * {@code "offline"} (uses a local JSON file in AppState format).
     * Defaults to {@code "offline"} so the mod is usable without an API key.
     */
    public String storageMode = "offline";

    /**
     * Identifies which offline data file to use.
     * <ul>
     *   <li>{@code "offline"} → {@code {uuid}-offline.json} (pure local use)</li>
     *   <li>12-char SHA-256 prefix of an API key → {@code {uuid}-{hash}.json}
     *       (created by {@code /poi download})</li>
     * </ul>
     */
    public String offlineProfileKey = "offline";

    // ── Helpers ────────────────────────────────────────────────────────────────

    public List<String> getTrackedPoiIds() {
        if (trackedPoiIds == null) trackedPoiIds = new ArrayList<>();
        return trackedPoiIds;
    }

    /**
     * Makes {@code worldId} the selected POI world ({@code null} = none). The tracked
     * POIs and group focus of the previous world are put aside in {@link #worldStates}
     * and those of the new world are brought back. Saves the config.
     */
    public synchronized void activateWorld(String worldId) {
        if (worldStates == null) worldStates = new HashMap<>();
        if (currentWorldId != null && !currentWorldId.equals(worldId)) {
            if (getTrackedPoiIds().isEmpty() && currentGroupId == null) {
                worldStates.remove(currentWorldId);
            } else {
                WorldState state = new WorldState();
                state.trackedPoiIds = new ArrayList<>(getTrackedPoiIds());
                state.groupId   = currentGroupId;
                state.groupName = currentGroupName;
                worldStates.put(currentWorldId, state);
            }
        }
        if (worldId == null || !worldId.equals(currentWorldId)) {
            WorldState state = worldId != null ? worldStates.remove(worldId) : null;
            trackedPoiIds = state != null && state.trackedPoiIds != null
                    ? new ArrayList<>(state.trackedPoiIds)
                    : new ArrayList<>();
            currentGroupId   = state != null ? state.groupId : null;
            currentGroupName = state != null ? state.groupName : null;
        }
        currentWorldId = worldId;
        save();
    }

    /** Returns the POI world bound to the given place, or null. */
    public synchronized String getBinding(String placeKey) {
        if (placeKey == null || worldBindings == null) return null;
        return worldBindings.get(placeKey);
    }

    /** Binds the given place to a POI world ({@code null} removes the binding). Saves the config. */
    public synchronized void bindWorld(String placeKey, String worldId) {
        if (placeKey == null) return;
        if (worldBindings == null) worldBindings = new HashMap<>();
        if (worldId == null) worldBindings.remove(placeKey);
        else worldBindings.put(placeKey, worldId);
        save();
    }

    /**
     * Returns the world selected by a profile from before world bindings existed, once.
     * That selection was global, so the caller may only keep it where the seed
     * confirms it belongs to the world being joined.
     */
    public synchronized String takeLegacyWorldId() {
        if (bindingsVersion >= 1) return null;
        bindingsVersion = 1;
        return currentWorldId;
    }

    public String getApiBaseUrl() {
        String override = System.getProperty("reaching.random.api.root");
        if (override != null && !override.isBlank()) {
            if (!loggedApiOverride) {
                loggedApiOverride = true;
                LOGGER.info("[POI] Using API URL override: {}", override);
            }
            return override;
        }
        return apiBaseUrl != null ? apiBaseUrl : "https://reachingrandom.com";
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean isOnlineMode() {
        return "online".equals(storageMode);
    }

    /** Returns true when the mod is explicitly disabled via {@code /poi off}. */
    public boolean isOffMode() {
        return "off".equals(storageMode);
    }

    // ── Singleton ──────────────────────────────────────────────────────────────

    public static PoiConfig get() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return load(POI_DIR.resolve("default-profile.json"));

        String suffix = (mc.getUser() != null)
                ? mc.getUser().getProfileId().toString()
                : "default";

        if (instance == null || instance.currentFilePath == null
                || !instance.currentFilePath.getFileName().toString().contains(suffix)) {
            Path path = POI_DIR.resolve(suffix + "-profile.json");
            instance = load(path);
        }
        return instance;
    }

    // ── Persistence ────────────────────────────────────────────────────────────

    private static PoiConfig load(Path path) {
        if (!Files.exists(path)) {
            PoiConfig defaults = new PoiConfig();
            defaults.bindingsVersion = 1;
            defaults.currentFilePath = path;
            defaults.save();
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            PoiConfig cfg = GSON.fromJson(reader, PoiConfig.class);
            if (cfg == null) cfg = new PoiConfig();
            cfg.currentFilePath = path;
            return cfg;
        } catch (IOException e) {
            LOGGER.error("[POI] Failed to load config, using defaults", e);
            PoiConfig cfg = new PoiConfig();
            cfg.currentFilePath = path;
            return cfg;
        }
    }

    public synchronized void save() {
        if (currentFilePath == null) return;
        try {
            Files.createDirectories(currentFilePath.getParent());
            try (Writer writer = Files.newBufferedWriter(currentFilePath)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            LOGGER.error("[POI] Failed to save config", e);
        }
    }
}
