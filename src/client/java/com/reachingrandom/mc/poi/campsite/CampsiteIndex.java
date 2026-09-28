package com.reachingrandom.mc.poi.campsite;

import com.reachingrandom.mc.poi.api.ApiModels;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory lookup of campsite POIs for the selected world, keyed by
 * dimension + block position.
 *
 * <p>A campfire is linked to a POI purely by location: any POI inside the root-level
 * {@value #GROUP_NAME} group whose coordinates equal the campfire's block position
 * (in the same dimension) is that campfire's POI. No extra fields are stored, so the
 * data stays compatible with the website and the AppState file format.
 *
 * <p>Rebuilt from every POI-list refresh (which may run on a background thread) and
 * read on the render thread, so the whole snapshot is swapped atomically.
 */
public final class CampsiteIndex {

    public static final String GROUP_NAME = "Campsites";

    public record Key(String dimension, BlockPos pos) {}

    private record Snapshot(String worldId, Map<Key, ApiModels.WorldItem> pois) {}

    private static volatile Snapshot snapshot = new Snapshot(null, Map.of());

    private CampsiteIndex() {}

    /** Replaces the index with the campsites found in {@code items} (a world's root item list). */
    public static void rebuild(String worldId, List<ApiModels.WorldItem> items) {
        Map<Key, ApiModels.WorldItem> pois = new HashMap<>();
        ApiModels.WorldItem group = findGroup(items);
        if (group != null && group.items != null) {
            for (ApiModels.WorldItem poi : group.items) {
                Key key = keyOf(poi);
                if (key != null) pois.putIfAbsent(key, poi);
            }
        }
        snapshot = new Snapshot(worldId, Map.copyOf(pois));
    }

    /** Whether the index was last built for the given world. */
    public static boolean isBuiltFor(String worldId) {
        return worldId != null && worldId.equals(snapshot.worldId());
    }

    public static String worldId() {
        return snapshot.worldId();
    }

    /** Returns the campsite POI at the given location, or null. */
    public static ApiModels.WorldItem find(String dimension, BlockPos pos) {
        return snapshot.pois().get(new Key(dimension, pos));
    }

    /** All indexed campsites, as an immutable view. */
    public static Map<Key, ApiModels.WorldItem> all() {
        return snapshot.pois();
    }

    /** Returns the root-level {@value #GROUP_NAME} group (case-insensitive), or null. */
    public static ApiModels.WorldItem findGroup(List<ApiModels.WorldItem> items) {
        if (items == null) return null;
        for (ApiModels.WorldItem item : items) {
            if ("group".equals(item.type) && GROUP_NAME.equalsIgnoreCase(item.name)) return item;
        }
        return null;
    }

    /** Returns the POI inside {@code group} at the given location, or null. */
    public static ApiModels.WorldItem findIn(ApiModels.WorldItem group, String dimension, BlockPos pos) {
        if (group == null || group.items == null) return null;
        Key target = new Key(dimension, pos);
        for (ApiModels.WorldItem poi : group.items) {
            if (target.equals(keyOf(poi))) return poi;
        }
        return null;
    }

    private static Key keyOf(ApiModels.WorldItem poi) {
        if (!"poi".equals(poi.type) || poi.coords == null) return null;
        ApiModels.Coords c = poi.coords;
        if (c.x == null || c.y == null || c.z == null) return null;
        String dimension = poi.dimension != null ? poi.dimension : "overworld";
        BlockPos pos = new BlockPos((int) Math.floor(c.x), (int) Math.floor(c.y), (int) Math.floor(c.z));
        return new Key(dimension, pos);
    }
}
