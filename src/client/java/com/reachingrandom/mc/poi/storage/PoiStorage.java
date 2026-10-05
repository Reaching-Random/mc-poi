package com.reachingrandom.mc.poi.storage;

import com.reachingrandom.mc.poi.api.ApiClient;
import com.reachingrandom.mc.poi.api.ApiModels;
import com.reachingrandom.mc.poi.campsite.CampsiteIndex;
import net.minecraft.core.BlockPos;

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

    /**
     * Renames a POI and optionally replaces its description, keeping its ID.
     * A {@code null} description leaves the existing one unchanged.
     * Throws {@link ApiClient.ApiException} if the POI or world is not found.
     */
    ApiModels.WorldItem updatePoi(String worldId, String poiId,
                                   String name, String description) throws ApiClient.ApiException;

    ApiModels.WorldItem createGroup(String worldId, String name) throws ApiClient.ApiException;

    /**
     * Permanently deletes the POI with the given ID from the specified world.
     * Throws {@link ApiClient.ApiException} if the POI or world is not found.
     */
    void deletePoi(String worldId, String poiId) throws ApiClient.ApiException;

    // ── Campsites ─────────────────────────────────────────────────────────────
    // Online, these go to the site's campsite endpoints, which keep the campsite group
    // locked and let every role name and break campfires. Offline there are no roles,
    // so the defaults below use the plain item operations.

    /** Saves a named campfire as a campsite, creating the campsite group if needed. */
    default ApiModels.WorldItem createCampsite(String worldId, String name, String dimension,
                                               int x, int y, int z) throws ApiClient.ApiException {
        ApiModels.WorldItem group = CampsiteIndex.findGroup(listItems(worldId));
        if (group == null) group = createGroup(worldId, CampsiteIndex.GROUP_NAME);
        return createPoi(worldId, group.id, name, "", x, y, z, dimension);
    }

    /** Removes the campsite at a broken campfire. Does nothing if there is none. */
    default void removeCampsiteAt(String worldId, String dimension, int x, int y, int z) throws ApiClient.ApiException {
        ApiModels.WorldItem group = CampsiteIndex.findGroup(listItems(worldId));
        ApiModels.WorldItem poi = CampsiteIndex.findIn(group, dimension, new BlockPos(x, y, z));
        if (poi != null) deletePoi(worldId, poi.id);
    }

    default ApiModels.WorldItem renameCampsite(String worldId, String poiId, String name) throws ApiClient.ApiException {
        return updatePoi(worldId, poiId, name, null);
    }

    /** Deletes a campsite by blanking its name. Online, an admin can restore it. */
    default void deleteCampsite(String worldId, String poiId) throws ApiClient.ApiException {
        deletePoi(worldId, poiId);
    }
}
