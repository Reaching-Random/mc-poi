package com.reachingrandom.mc.poi.storage;

import com.reachingrandom.mc.poi.api.ApiClient;
import com.reachingrandom.mc.poi.config.PoiConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Factory that returns the active {@link PoiStorage} implementation based on
 * the player's current {@link PoiConfig#storageMode}.
 *
 * <ul>
 *   <li>{@code "online"} + API key set → {@link ApiPoiStorage} backed by the reaching-random API</li>
 *   <li>Any other case → {@link LocalPoiStorage} backed by a local JSON file</li>
 * </ul>
 *
 * <p>Data files live at:
 * <pre>{config-dir}/poi/data/{playerUUID}-{profileKey}.json</pre>
 * where {@code profileKey} is {@code "offline"} for pure local use, or a 12-character
 * SHA-256 prefix of an API key for files created by {@code /poi download}.
 */
public final class PoiStorageProvider {

    private static final Path DATA_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("poi")
            .resolve("data");

    private PoiStorageProvider() {}

    /**
     * Returns the currently active {@link PoiStorage} implementation.
     * Safe to call from any thread; does not perform I/O.
     */
    public static PoiStorage get() {
        PoiConfig cfg = PoiConfig.get();
        if (cfg.isOnlineMode() && cfg.hasApiKey()) {
            return new ApiPoiStorage(new ApiClient());
        }
        return new LocalPoiStorage(resolveDataFile(cfg));
    }

    /**
     * Resolves the absolute path to the offline data file for the given config.
     * Uses {@link PoiConfig#offlineProfileKey} and the current player's UUID.
     *
     * <p>This method is storage-mode-agnostic — it always returns the offline
     * file path regardless of {@code storageMode}, which is useful for displaying
     * the path to the user (e.g. in {@code /poi setkey} and {@code /poi status}).
     */
    public static Path resolveDataFile(PoiConfig cfg) {
        String playerKey = playerUuid();
        String profileKey = cfg.offlineProfileKey != null && !cfg.offlineProfileKey.isBlank()
                ? cfg.offlineProfileKey
                : "offline";
        return DATA_DIR.resolve(playerKey + "-" + profileKey + ".json");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String playerUuid() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getUser() != null) {
            UUID id = mc.getUser().getProfileId();
            if (id != null) return id.toString();
        }
        return "default";
    }
}
