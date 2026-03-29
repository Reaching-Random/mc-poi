package com.reachingrandom.mc.poi.api;

import java.util.List;

/**
 * Plain data classes matching the reaching-random API JSON shapes.
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
    public static class WorldDetail {
        public String id;
        public String name;
        public String seed;
        public List<WorldItem> items;
        public String created;
        public String modified;
    }

    public static class WorldItem {
        /** "poi" or "group" */
        public String type;

        // --- POI fields ---
        public String id;
        public String name;
        public String description;
        public String dimension;
        public Coords coords;
        public String created;
        public String modified;

        // --- Group fields ---
        public List<WorldItem> items;
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
}
