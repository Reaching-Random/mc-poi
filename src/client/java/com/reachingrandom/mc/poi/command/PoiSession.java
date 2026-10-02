package com.reachingrandom.mc.poi.command;

import com.reachingrandom.mc.poi.api.ApiModels;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Transient in-memory state for the current play session.
 * Holds the numbered worlds list from the last /poi worlds call
 * and the numbered items list from the last /poi groups call,
 * so that /poi select and /poi list can refer back to them.
 */
public final class PoiSession {

    private static final PoiSession INSTANCE = new PoiSession();

    private PoiSession() {}

    public static PoiSession get() {
        return INSTANCE;
    }

    // ── Worlds list (from last /poi worlds) ────────────────────────────────────
    private volatile List<ApiModels.WorldSummary> lastWorldsList = Collections.emptyList();

    public void setLastWorldsList(List<ApiModels.WorldSummary> worlds) {
        this.lastWorldsList = List.copyOf(worlds);
    }

    /**
     * Returns the world at 1-based index, or null if out of range.
     */
    public ApiModels.WorldSummary getWorldByNumber(int number) {
        if (number < 1 || number > lastWorldsList.size()) return null;
        return lastWorldsList.get(number - 1);
    }

    public int worldCount() {
        return lastWorldsList.size();
    }

    // ── Groups list (from last /poi groups) ────────────────────────────────────
    private volatile List<ApiModels.WorldItem> lastGroupsList = Collections.emptyList();

    public void setGroups(List<ApiModels.WorldItem> groups) {
        this.lastGroupsList = List.copyOf(groups);
    }

    /**
     * Returns the group at 1-based index, or null if out of range.
     */
    public ApiModels.WorldItem getGroupByNumber(int number) {
        if (number < 1 || number > lastGroupsList.size()) return null;
        return lastGroupsList.get(number - 1);
    }

    public int groupCount() {
        return lastGroupsList.size();
    }

    // ── Flat POI list (from last /poi list) ────────────────────────────────────
    private volatile List<ApiModels.WorldItem> lastPoiList = Collections.emptyList();

    public void setLastPoiList(List<ApiModels.WorldItem> pois) {
        this.lastPoiList = List.copyOf(pois);
    }

    /**
     * Returns the POI at 1-based index, or null if out of range.
     */
    public ApiModels.WorldItem getPoiByNumber(int number) {
        if (number < 1 || number > lastPoiList.size()) return null;
        return lastPoiList.get(number - 1);
    }

    public int poiCount() {
        return lastPoiList.size();
    }

    public List<ApiModels.WorldItem> getLastPoiList() {
        return lastPoiList;
    }

    /**
     * Returns the 1-based position of the given POI in the last loaded list,
     * or -1 if it isn't in the list (e.g. list hasn't been refreshed since tracking).
     */
    public int getListNumber(ApiModels.WorldItem poi) {
        for (int i = 0; i < lastPoiList.size(); i++) {
            ApiModels.WorldItem p = lastPoiList.get(i);
            if (poi.id != null && poi.id.equals(p.id)) return i + 1;
            if (poi == p) return i + 1;
        }
        return -1;
    }

    // ── Tracked POIs (multi-track) ─────────────────────────────────────────────
    // Read every frame by the renderer and changed from storage threads.
    private final List<ApiModels.WorldItem> trackedPois = new CopyOnWriteArrayList<>();

    /**
     * Adds a POI to the tracked set. If a POI with the same ID is already
     * tracked it is replaced (so coordinates stay fresh).
     */
    public void addTrackedPoi(ApiModels.WorldItem poi) {
        if (poi.id != null) {
            trackedPois.removeIf(p -> poi.id.equals(p.id));
        }
        trackedPois.add(poi);
    }

    /**
     * Removes the POI at the given 1-based list number from tracked POIs.
     * Returns the removed POI, or null if the number is invalid or not tracked.
     */
    public ApiModels.WorldItem removeTrackedPoi(int listNumber) {
        ApiModels.WorldItem poi = getPoiByNumber(listNumber);
        if (poi == null) return null;
        boolean removed = (poi.id != null)
                ? trackedPois.removeIf(p -> poi.id.equals(p.id))
                : trackedPois.remove(poi);
        return removed ? poi : null;
    }

    /**
     * Removes a tracked POI by its ID. Used after deletion so the direction
     * renderer stops referencing a POI that no longer exists.
     */
    public void removeTrackedPoiById(String poiId) {
        if (poiId != null) trackedPois.removeIf(p -> poiId.equals(p.id));
    }

    public boolean isTracked(ApiModels.WorldItem poi) {
        if (poi.id != null) {
            return trackedPois.stream().anyMatch(p -> poi.id.equals(p.id));
        }
        return trackedPois.contains(poi);
    }

    public List<ApiModels.WorldItem> getTrackedPois() {
        return Collections.unmodifiableList(trackedPois);
    }

    public void clearTrackedPois() {
        trackedPois.clear();
    }

    /**
     * Forgets everything that belongs to the selected POI world: the group and
     * POI lists and the tracked POIs. The worlds list is not world-specific and stays.
     */
    public void clearWorldState() {
        lastGroupsList = Collections.emptyList();
        lastPoiList = Collections.emptyList();
        trackedPois.clear();
    }

    /** Returns the first tracked POI, or null if none are tracked. */
    public ApiModels.WorldItem getSelectedPoi() {
        return trackedPois.isEmpty() ? null : trackedPois.get(0);
    }
}
