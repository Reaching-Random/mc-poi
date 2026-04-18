package com.reachingrandom.mc.poi.storage;

import com.reachingrandom.mc.poi.api.ApiClient;
import com.reachingrandom.mc.poi.api.ApiModels;

import java.util.List;

/**
 * Abstraction over POI data storage. Two implementations exist:
 * <ul>
 *   <li>{@link ApiPoiStorage} — delegates to the reaching-random HTTP API (online mode)</li>
 *   <li>{@link LocalPoiStorage} — reads and writes a local JSON file in AppState format (offline mode)</li>
 * </ul>
 *
 * <p>All methods throw {@link ApiClient.ApiException} on failure so that
 * {@code PoiCommand}'s existing {@code async()} error-handler works unchanged for both impls.
 */
public interface PoiStorage {

    /** Whether this instance is backed by the live HTTP API. */
    boolean isOnline();

    /** Short human-readable label for chat display: {@code "ONLINE"} or {@code "OFFLINE"}. */
    String modeLabel();

    // ── Worlds ────────────────────────────────────────────────────────────────

    List<ApiModels.WorldSummary> listWorlds() throws ApiClient.ApiException;

    ApiModels.WorldSummary createWorld(String name, String seed) throws ApiClient.ApiException;

    // ── Items ─────────────────────────────────────────────────────────────────

    List<ApiModels.WorldItem> listItems(String worldId) throws ApiClient.ApiException;

    ApiModels.WorldItem createPoi(String worldId, String groupId,
                                   String name, String description,
                                   double x, double y, double z,
                                   String dimension) throws ApiClient.ApiException;

    ApiModels.WorldItem createGroup(String worldId, String name) throws ApiClient.ApiException;

    /**
     * Permanently deletes the POI with the given ID from the specified world.
     * Throws {@link ApiClient.ApiException} if the POI or world is not found.
     */
    void deletePoi(String worldId, String poiId) throws ApiClient.ApiException;
}
