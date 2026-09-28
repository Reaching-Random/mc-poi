package com.reachingrandom.mc.poi.storage;

import com.reachingrandom.mc.poi.api.ApiClient;
import com.reachingrandom.mc.poi.api.ApiModels;

import java.util.List;

/**
 * Online storage implementation: delegates all operations to the reaching-random
 * HTTP API via {@link ApiClient}.
 */
public class ApiPoiStorage implements PoiStorage {

    private final ApiClient apiClient;

    public ApiPoiStorage(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override public boolean isOnline()    { return true; }
    @Override public String  modeLabel()   { return "ONLINE"; }

    // ── Worlds ────────────────────────────────────────────────────────────────

    @Override
    public List<ApiModels.WorldSummary> listWorlds() throws ApiClient.ApiException {
        ApiModels.WorldsResponse resp = apiClient.getWorlds();
        return resp.worlds != null ? resp.worlds : List.of();
    }

    @Override
    public ApiModels.WorldSummary createWorld(String name, String seed) throws ApiClient.ApiException {
        return apiClient.createWorld(name, seed).world;
    }

    // ── Items ─────────────────────────────────────────────────────────────────

    @Override
    public List<ApiModels.WorldItem> listItems(String worldId) throws ApiClient.ApiException {
        ApiModels.ItemsResponse resp = apiClient.getItems(worldId);
        return resp.items != null ? resp.items : List.of();
    }

    @Override
    public ApiModels.WorldItem createPoi(String worldId, String groupId,
                                          String name, String description,
                                          double x, double y, double z,
                                          String dimension) throws ApiClient.ApiException {
        return apiClient.createPoi(worldId, groupId, name, description, x, y, z, dimension).poi;
    }

    @Override
    public ApiModels.WorldItem updatePoi(String worldId, String poiId,
                                          String name, String description) throws ApiClient.ApiException {
        return apiClient.updatePoi(worldId, poiId, name, description).poi;
    }

    @Override
    public ApiModels.WorldItem createGroup(String worldId, String name) throws ApiClient.ApiException {
        return apiClient.createGroup(worldId, name).group;
    }

    @Override
    public void deletePoi(String worldId, String poiId) throws ApiClient.ApiException {
        apiClient.deletePoi(worldId, poiId);
    }
}
