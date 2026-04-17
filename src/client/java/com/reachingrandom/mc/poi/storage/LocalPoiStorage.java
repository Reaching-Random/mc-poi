package com.reachingrandom.mc.poi.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.reachingrandom.mc.poi.api.ApiClient;
import com.reachingrandom.mc.poi.api.ApiModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Offline storage implementation: reads and writes a local JSON file in the
 * {@link ApiModels.AppState} format (poi-state-v1.json).
 *
 * <p>The file is compatible with the reachingrandom.com import/export feature,
 * so data can be transferred between the mod and the website without any conversion.
 *
 * <p>All operations load the file, mutate in memory, then write it back immediately.
 * This is fast (local I/O) and keeps the file always consistent.
 */
public class LocalPoiStorage implements PoiStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger("points-of-interest");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path dataFile;

    public LocalPoiStorage(Path dataFile) {
        this.dataFile = dataFile;
    }

    @Override public boolean isOnline()  { return false; }
    @Override public String  modeLabel() { return "OFFLINE"; }

    /** Returns the path of the data file (used for displaying clickable links in chat). */
    public Path getDataFile() { return dataFile; }

    // ── Worlds ────────────────────────────────────────────────────────────────

    @Override
    public List<ApiModels.WorldSummary> listWorlds() throws ApiClient.ApiException {
        ApiModels.AppState state = loadAppState();
        if (state.worlds == null) return List.of();
        return state.worlds.stream().map(this::toSummary).toList();
    }

    @Override
    public ApiModels.WorldSummary createWorld(String name, String seed) throws ApiClient.ApiException {
        ApiModels.AppState state = loadAppState();

        ApiModels.WorldDetail world = new ApiModels.WorldDetail();
        world.id       = generateId();
        world.name     = name;
        world.seed     = seed != null ? seed : "";
        world.items    = new ArrayList<>();
        world.created  = now();
        world.modified = world.created;

        if (state.worlds == null) state.worlds = new ArrayList<>();
        state.worlds.add(world);
        saveAppState(state);

        return toSummary(world);
    }

    // ── Items ─────────────────────────────────────────────────────────────────

    @Override
    public List<ApiModels.WorldItem> listItems(String worldId) throws ApiClient.ApiException {
        ApiModels.AppState state = loadAppState();
        ApiModels.WorldDetail world = requireWorld(state, worldId);
        return world.items != null ? world.items : List.of();
    }

    @Override
    public ApiModels.WorldItem createPoi(String worldId, String groupId,
                                          String name, String description,
                                          double x, double y, double z,
                                          String dimension) throws ApiClient.ApiException {
        ApiModels.AppState state = loadAppState();
        ApiModels.WorldDetail world = requireWorld(state, worldId);

        ApiModels.WorldItem poi = new ApiModels.WorldItem();
        poi.type        = "poi";
        poi.id          = generateId();
        poi.name        = name;
        poi.description = description != null ? description : "";
        poi.dimension   = dimension != null ? dimension : "overworld";
        poi.coords      = new ApiModels.Coords();
        poi.coords.x    = x;
        poi.coords.y    = y;
        poi.coords.z    = z;
        poi.created     = now();
        poi.modified    = poi.created;

        if (groupId != null && !groupId.isBlank()) {
            boolean added = addToGroup(world, groupId, poi);
            if (!added) {
                // Group not found — fall back to root level
                LOGGER.warn("[POI] Group {} not found; adding POI to root level.", groupId);
                ensureItems(world).add(poi);
            }
        } else {
            ensureItems(world).add(poi);
        }

        world.modified = now();
        saveAppState(state);
        return poi;
    }

    @Override
    public ApiModels.WorldItem createGroup(String worldId, String name) throws ApiClient.ApiException {
        ApiModels.AppState state = loadAppState();
        ApiModels.WorldDetail world = requireWorld(state, worldId);

        ApiModels.WorldItem group = new ApiModels.WorldItem();
        group.type       = "group";
        group.id         = generateId();
        group.name       = name;
        group.items      = new ArrayList<>();
        group.isExpanded = true;
        group.created    = now();
        group.modified   = group.created;

        ensureItems(world).add(group);
        world.modified = now();
        saveAppState(state);
        return group;
    }

    @Override
    public void deletePoi(String worldId, String poiId) throws ApiClient.ApiException {
        ApiModels.AppState state = loadAppState();
        ApiModels.WorldDetail world = requireWorld(state, worldId);

        // Remove from root-level items
        boolean removed = false;
        if (world.items != null) {
            removed = world.items.removeIf(i -> "poi".equals(i.type) && poiId.equals(i.id));

            // Remove from inside any group
            if (!removed) {
                for (ApiModels.WorldItem group : world.items) {
                    if ("group".equals(group.type) && group.items != null) {
                        removed = group.items.removeIf(i -> poiId.equals(i.id));
                        if (removed) break;
                    }
                }
            }
        }

        if (!removed) {
            throw new ApiClient.ApiException("POI not found: " + poiId);
        }

        world.modified = now();
        saveAppState(state);
    }

    /**
     * Replaces the entire data file with the given {@link ApiModels.AppState}.
     * Used by {@code /poi download} to persist a snapshot fetched from the API.
     */
    public void overwrite(ApiModels.AppState state) throws ApiClient.ApiException {
        saveAppState(state);
    }

    // ── AppState I/O ──────────────────────────────────────────────────────────

    private ApiModels.AppState loadAppState() throws ApiClient.ApiException {
        if (!Files.exists(dataFile)) {
            return new ApiModels.AppState();
        }

        String json;
        try {
            json = Files.readString(dataFile);
        } catch (IOException e) {
            throw new ApiClient.ApiException("Failed to read offline data file: " + e.getMessage());
        }

        List<String> errors = PoiStateValidator.validate(json);
        if (!errors.isEmpty()) {
            LOGGER.error("[POI] Offline data file {} has schema errors:", dataFile.getFileName());
            for (String err : errors) LOGGER.error("[POI]   {}", err);
            throw new ApiClient.ApiException(
                "Offline data file is invalid (schema errors). Check latest.log for details, " +
                "or re-download with /poi download / import from reachingrandom.com."
            );
        }

        ApiModels.AppState state = GSON.fromJson(json, ApiModels.AppState.class);
        if (state == null || state.worlds == null) return new ApiModels.AppState();
        return state;
    }

    private void saveAppState(ApiModels.AppState state) throws ApiClient.ApiException {
        try {
            Files.createDirectories(dataFile.getParent());
            Files.writeString(dataFile, GSON.toJson(state));
        } catch (IOException e) {
            throw new ApiClient.ApiException("Failed to save offline data file: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ApiModels.WorldDetail requireWorld(ApiModels.AppState state, String worldId)
            throws ApiClient.ApiException {
        if (state.worlds != null) {
            for (ApiModels.WorldDetail w : state.worlds) {
                if (worldId.equals(w.id)) return w;
            }
        }
        throw new ApiClient.ApiException(
            "World not found: " + worldId + ". Run /world list to refresh.");
    }

    /** Tries to add {@code poi} into the group with the given ID. Returns true on success. */
    private boolean addToGroup(ApiModels.WorldDetail world, String groupId, ApiModels.WorldItem poi) {
        if (world.items == null) return false;
        for (ApiModels.WorldItem item : world.items) {
            if ("group".equals(item.type) && groupId.equals(item.id)) {
                if (item.items == null) item.items = new ArrayList<>();
                item.items.add(poi);
                return true;
            }
        }
        return false;
    }

    private List<ApiModels.WorldItem> ensureItems(ApiModels.WorldDetail world) {
        if (world.items == null) world.items = new ArrayList<>();
        return world.items;
    }

    private ApiModels.WorldSummary toSummary(ApiModels.WorldDetail world) {
        ApiModels.WorldSummary s = new ApiModels.WorldSummary();
        s.id       = world.id;
        s.name     = world.name;
        s.seed     = world.seed;
        s.created  = world.created;
        s.modified = world.modified;
        return s;
    }

    private static String generateId() {
        // 21-char alphanumeric ID (similar to nanoid used by the website)
        return UUID.randomUUID().toString().replace("-", "").substring(0, 21);
    }

    private static String now() {
        return Instant.now().toString();
    }
}
