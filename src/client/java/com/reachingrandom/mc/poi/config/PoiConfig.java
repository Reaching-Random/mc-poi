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
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;

public class PoiConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("points-of-interest");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    private static final Path POI_DIR = CONFIG_DIR.resolve("poi");

    private static PoiConfig instance;
    private transient Path currentFilePath;

    // ── Serialized fields ──────────────────────────────────────────────────────

    public String apiBaseUrl = "https://reachingrandom.com";
    public String apiKey = "";
    public String currentWorldId = null;
    public String currentGroupId = null;
    public String currentGroupName = null;
    public List<String> trackedPoiIds = new ArrayList<>();

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

    public String getApiBaseUrl() {
        String override = System.getProperty("reaching.random.api.root");
        if (override != null && !override.isBlank()) {
            LOGGER.info("[POI] Using API URL override: {}", override);
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

    public void save() {
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
