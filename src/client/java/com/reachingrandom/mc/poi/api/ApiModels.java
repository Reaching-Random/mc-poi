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
        /** Your role in this world: read-only, contribute, admin or owner. Null offline (your own world). */
        public String role;
        /** The multiplayer server this world is played on, if an admin bound it. */
        public String serverAddress;
        public String created;
        public String modified;

        /** Whether someone else owns this world, so it was shared with you. */
        public boolean isShared() {
            return role != null && !"owner".equals(role);
        }

        public boolean canAdd() {
            return role == null || !"read-only".equals(role);
        }

        public boolean isAdmin() {
            return role == null || "owner".equals(role) || "admin".equals(role);
        }
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

        /** Site user who added it. Online only. */
        public String createdBy;

        // ── Group fields ──────────────────────────────────────────────────────
        public List<WorldItem> items;
        /** UI expand/collapse hint used by the website. Preserved as-is. */
        public Boolean isExpanded;
        /** {@code "campsites"} on the locked group that campfires manage. */
        public String system;
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

    // ── Campsites (/worlds/:worldId/campsites) ────────────────────────────────
    public static class CreateCampsiteRequest {
        public String name;
        public String dimension;
        public int x;
        public int y;
        public int z;

        public CreateCampsiteRequest(String name, String dimension, int x, int y, int z) {
            this.name = name;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static class RenameRequest {
        public String name;

        public RenameRequest(String name) {
            this.name = name;
        }
    }

    public static class PoiResponse {
        public WorldItem poi;
    }

    // ── Sharing ───────────────────────────────────────────────────────────────
    /** GET /api/mc/poi/poll. Fields may be missing; see SharingCommands for the defaults. */
    public static class PollResponse {
        public Long rev;
        public Boolean shared;
        public Integer pendingInvites;
        public Integer pollSeconds;
    }

    public static class ServerAddressRequest {
        public String serverAddress;

        public ServerAddressRequest(String serverAddress) {
            this.serverAddress = serverAddress;
        }
    }

    public static class Member {
        public String name;
        public String role;
        public boolean isYou;
    }

    public static class MembersResponse {
        public List<Member> members;
    }

    public static class Player {
        public String uuid;
        public String name;

        public Player(String uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    public static class InvitePlayersRequest {
        public String role;
        public List<Player> players;

        public InvitePlayersRequest(String role, List<Player> players) {
            this.role = role;
            this.players = players;
        }
    }

    public static class InviteResult {
        public String name;
        /** addressed (sent to their game), link (pass {@link #url} on) or member (already in). */
        public String kind;
        public String url;
    }

    public static class InvitePlayersResponse {
        public List<InviteResult> results;
    }

    public static class Invite {
        public String id;
        public String role;
        public String label;
        public String status;
        public boolean addressed;
        public String expiresAt;
    }

    public static class InvitesResponse {
        public List<Invite> invites;
    }

    /** An invite sent to one of your linked Minecraft accounts. */
    public static class PendingInvite {
        public String id;
        public String worldId;
        public String worldName;
        public String role;
        public String invitedBy;
    }

    public static class PendingInvitesResponse {
        public List<PendingInvite> invites;
    }

    public static class WorldIdResponse {
        public String worldId;
    }

    // ── Account linking (/link) ───────────────────────────────────────────────
    public static class NonceResponse {
        public String nonce;
    }

    public static class LinkRequest {
        public String username;
        public String nonce;

        public LinkRequest(String username, String nonce) {
            this.username = username;
            this.nonce = nonce;
        }
    }

    public static class LinkedAccount {
        public String uuid;
        public String name;
        public boolean primary;
    }

    public static class LinkResponse {
        public LinkedAccount account;
    }

    public static class LinkedAccountsResponse {
        public List<LinkedAccount> accounts;
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
