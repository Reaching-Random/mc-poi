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

    // ── fields (serialized to JSON) ────────────────────────────────────────────
    private static final String HELP_PATH = "/mc/poi/help";
    public String apiBaseUrl = "https://reachingrandom.com";
    public String apiKey = "";
    public String currentWorldId = null;
    public String currentGroupId = null;
    public String currentGroupName = null;
    public List<String> trackedPoiIds = new ArrayList<>();

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

    // ── singleton ──────────────────────────────────────────────────────────────
    public static PoiConfig get() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return load(POI_DIR.resolve("default-profile.json"));
        
        String suffix = (mc.getUser() != null)
                ? mc.getUser().getProfileId().toString()
                : "default";

        if (instance == null || instance.currentFilePath == null || !instance.currentFilePath.getFileName().toString().contains(suffix)) {
            Path path = POI_DIR.resolve(suffix + "-profile.json");
            instance = load(path);
        }
        return instance;
    }

    // ── persistence ────────────────────────────────────────────────────────────
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

    // ── helpers ────────────────────────────────────────────────────────────────
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
