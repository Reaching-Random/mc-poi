package com.reachingrandom.mc.poi.command;

import com.reachingrandom.mc.poi.api.ApiModels;

import java.util.Collections;
import java.util.List;

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
    private List<ApiModels.WorldSummary> lastWorldsList = Collections.emptyList();

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
    private List<ApiModels.WorldItem> lastGroupsList = Collections.emptyList();

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
    private List<ApiModels.WorldItem> lastPoiList = Collections.emptyList();

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

    // ── Selected (tracked) POI ─────────────────────────────────────────────────
    private ApiModels.WorldItem selectedPoi = null;

    public void setSelectedPoi(ApiModels.WorldItem poi) {
        this.selectedPoi = poi;
    }

    public ApiModels.WorldItem getSelectedPoi() {
        return selectedPoi;
    }
}
