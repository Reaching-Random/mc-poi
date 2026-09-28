package com.reachingrandom.mc.poi.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain data classes matching the reaching-random API JSON shapes AND the
 * local AppState file format (poi-state-v1.json).
 * Gson deserializes directly into these; no getters/setters needed.
 */
public final class ApiModels {

    private ApiModels() {}

    // ── /api/mc/poi/worlds (GET) ───────────────────────────────────────────────
    public static class WorldsResponse {
        public List<WorldSummary> worlds;
    }

    public static class WorldSummary {
        public String id;
        public String name;
        public String seed;
        public String created;
        public String modified;
    }

    // ── /api/mc/poi/worlds (POST) ──────────────────────────────────────────────
    public static class CreateWorldRequest {
        public String name;
        public String seed;

        public CreateWorldRequest(String name, String seed) {
            this.name = name;
            this.seed = seed;
        }
    }

    public static class CreateWorldResponse {
        public WorldSummary world;
    }

    // ── /api/mc/poi/worlds/seed/:seed (GET) ───────────────────────────────────
    public static class SeedLookupResponse {
        public WorldDetail world;
    }

    // ── /api/mc/poi/worlds/:worldId/pois (GET) ────────────────────────────────
    public static class ItemsResponse {
        public List<WorldItem> items;
    }

    // ── Shared item types ──────────────────────────────────────────────────────

    /**
     * Full world detail including items. Used by the API and also as the
     * per-world entry in the local {@link AppState} file.
     */
    public static class WorldDetail {
        public String id;
        public String name;
        public String seed;
        public List<WorldItem> items;
        public String created;
        public String modified;
    }

    /**
     * A single POI or Group, as returned by the API and stored in AppState files.
     * The {@code type} field distinguishes them: {@code "poi"} or {@code "group"}.
     *
     * <p>Fields present on POIs only: {@code description}, {@code dimension},
     * {@code coords}, {@code nearestPortal}.
     * Fields present on Groups only: {@code items}, {@code isExpanded}.
     */
    public static class WorldItem {
        /** "poi" or "group" */
        public String type;

        // ── Common ────────────────────────────────────────────────────────────
        public String id;
        public String name;
        public String created;
        public String modified;

        // ── POI fields ────────────────────────────────────────────────────────
        public String description;
        public String dimension;
        public Coords coords;
        /** Nearest Nether portal coordinates. Preserved from AppState files;
         *  not captured by any mod command yet. */
        public Coords nearestPortal;

        // ── Group fields ──────────────────────────────────────────────────────
        public List<WorldItem> items;
        /** UI expand/collapse hint used by the website. Preserved as-is. */
        public Boolean isExpanded;
    }

    public static class Coords {
        public Double x;
        public Double y;
        public Double z;
    }

    // ── /api/mc/poi/worlds/:worldId/pois (POST) ───────────────────────────────
    public static class CreatePoiRequest {
        public String name;
        public String description;
        public double x;
        public double y;
        public double z;
        public String dimension;
        public String groupId;

        public CreatePoiRequest(String name, String description,
                                double x, double y, double z,
                                String dimension, String groupId) {
            this.name = name;
            this.description = description;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension;
            this.groupId = groupId;
        }
    }

    public static class CreatePoiResponse {
        public WorldItem poi;
    }

    // ── /api/mc/poi/worlds/:worldId/pois/:poiId (PATCH) ───────────────────────
    /** Null fields are omitted by Gson, so the server leaves them unchanged. */
    public static class UpdatePoiRequest {
        public String name;
        public String description;

        public UpdatePoiRequest(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }

    public static class UpdatePoiResponse {
        public WorldItem poi;
    }

    // ── /api/mc/poi/worlds/:worldId/groups (POST) ─────────────────────────────
    public static class CreateGroupRequest {
        public String name;

        public CreateGroupRequest(String name) {
            this.name = name;
        }
    }

    public static class CreateGroupResponse {
        public WorldItem group;
    }

    // ── Error envelope ────────────────────────────────────────────────────────
    public static class ErrorResponse {
        public String error;
    }

    // ── Local offline file format (poi-state-v1.json) ─────────────────────────

    /**
     * Top-level envelope for the local offline data file.
     * Matches the {@code AppState} format used by reachingrandom.com,
     * so files are interchangeable with the site's import/export feature.
     */
    public static class AppState {
        public List<WorldDetail> worlds = new ArrayList<>();
        public int version = 1;
    }
}
