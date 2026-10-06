package com.reachingrandom.mc.poi.api;

import com.google.gson.Gson;
import com.reachingrandom.mc.poi.Pointsofinterest;
import com.reachingrandom.mc.poi.config.PoiConfig;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Thin wrapper around Java's HttpClient for the reaching-random POI API.
 * All calls are synchronous (intended to be run off the main thread via
 * CompletableFuture in PoiCommand).
 *
 * <p>Construct with {@link #ApiClient()} to use the API key from {@link PoiConfig},
 * or with {@link #ApiClient(String)} to supply a key directly (e.g. for one-shot
 * operations like {@code /poi download} that don't persist the key).
 */
public class ApiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("points-of-interest");
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1) // Force HTTP 1.1 for dev server compatibility
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Sent on every request as {@code points-of-interest/<version>}. The site uses it to tell
     * builds that understand sharing from older ones, and to ask outdated builds to update.
     */
    private static final String CLIENT_HEADER = "points-of-interest/" + FabricLoader.getInstance()
            .getModContainer(Pointsofinterest.MOD_ID)
            .map(m -> m.getMetadata().getVersion().getFriendlyString())
            .orElse("0.0.0");

    /** If non-null, used instead of the key stored in {@link PoiConfig}. */
    private final String apiKeyOverride;

    /** Uses the API key from {@link PoiConfig} (normal usage). */
    public ApiClient() {
        this.apiKeyOverride = null;
    }

    /**
     * Uses the given key instead of the one in {@link PoiConfig}.
     * The key is never persisted to config — suitable for one-shot operations.
     */
    public ApiClient(String apiKeyOverride) {
        this.apiKeyOverride = apiKeyOverride;
    }

    private String effectiveApiKey() {
        return apiKeyOverride != null ? apiKeyOverride : PoiConfig.get().apiKey;
    }

    // ── Worlds ─────────────────────────────────────────────────────────────────

    public ApiModels.WorldsResponse getWorlds() throws ApiException {
        String json = get("/api/mc/poi/worlds");
        return GSON.fromJson(json, ApiModels.WorldsResponse.class);
    }

    public ApiModels.CreateWorldResponse createWorld(String name, String seed) throws ApiException {
        String body = GSON.toJson(new ApiModels.CreateWorldRequest(name, seed));
        String json = post("/api/mc/poi/worlds", body);
        return GSON.fromJson(json, ApiModels.CreateWorldResponse.class);
    }

    public ApiModels.SeedLookupResponse getWorldBySeed(String seed) throws ApiException {
        String json = get("/api/mc/poi/worlds/seed/" + seed);
        return GSON.fromJson(json, ApiModels.SeedLookupResponse.class);
    }

    // ── POIs / items ───────────────────────────────────────────────────────────

    public ApiModels.ItemsResponse getItems(String worldId) throws ApiException {
        String json = get("/api/mc/poi/worlds/" + worldId + "/pois");
        return GSON.fromJson(json, ApiModels.ItemsResponse.class);
    }

    public ApiModels.CreatePoiResponse createPoi(String worldId, String groupId,
                                                  String name, String description,
                                                  double x, double y, double z,
                                                  String dimension) throws ApiException {
        String body = GSON.toJson(
                new ApiModels.CreatePoiRequest(name, description, x, y, z, dimension, groupId));
        String json = post("/api/mc/poi/worlds/" + worldId + "/pois", body);
        return GSON.fromJson(json, ApiModels.CreatePoiResponse.class);
    }

    public ApiModels.CreateGroupResponse createGroup(String worldId, String name) throws ApiException {
        String body = GSON.toJson(new ApiModels.CreateGroupRequest(name));
        String json = post("/api/mc/poi/worlds/" + worldId + "/groups", body);
        return GSON.fromJson(json, ApiModels.CreateGroupResponse.class);
    }

    public ApiModels.UpdatePoiResponse updatePoi(String worldId, String poiId,
                                                  String name, String description) throws ApiException {
        String body = GSON.toJson(new ApiModels.UpdatePoiRequest(name, description));
        String json = patch("/api/mc/poi/worlds/" + worldId + "/pois/" + poiId, body);
        return GSON.fromJson(json, ApiModels.UpdatePoiResponse.class);
    }

    public void deletePoi(String worldId, String poiId) throws ApiException {
        delete("/api/mc/poi/worlds/" + worldId + "/pois/" + poiId);
    }

    // ── Campsites ──────────────────────────────────────────────────────────────

    public ApiModels.WorldItem createCampsite(String worldId, String name, String dimension,
                                              int x, int y, int z) throws ApiException {
        String body = GSON.toJson(new ApiModels.CreateCampsiteRequest(name, dimension, x, y, z));
        return GSON.fromJson(post("/api/mc/poi/worlds/" + worldId + "/campsites", body), ApiModels.PoiResponse.class).poi;
    }

    /** Removes the campsite at a broken campfire. Succeeds if it's already gone. */
    public void removeCampsiteAt(String worldId, String dimension, int x, int y, int z) throws ApiException {
        delete("/api/mc/poi/worlds/" + worldId + "/campsites?dim=" + enc(dimension) + "&x=" + x + "&y=" + y + "&z=" + z);
    }

    public ApiModels.WorldItem renameCampsite(String worldId, String poiId, String name) throws ApiException {
        String body = GSON.toJson(new ApiModels.RenameRequest(name));
        return GSON.fromJson(patch("/api/mc/poi/worlds/" + worldId + "/campsites/" + poiId, body), ApiModels.PoiResponse.class).poi;
    }

    public void deleteCampsite(String worldId, String poiId) throws ApiException {
        delete("/api/mc/poi/worlds/" + worldId + "/campsites/" + poiId);
    }

    // ── Sharing ────────────────────────────────────────────────────────────────

    /** The background check: the selected world's revision (if any), waiting invites, and when to ask again. */
    public ApiModels.PollResponse poll(String worldId) throws ApiException {
        String path = "/api/mc/poi/poll" + (worldId != null ? "?worldId=" + enc(worldId) : "");
        return GSON.fromJson(get(path), ApiModels.PollResponse.class);
    }

    public void setServerAddress(String worldId, String serverAddress) throws ApiException {
        patch("/api/mc/poi/worlds/" + worldId, GSON.toJson(new ApiModels.ServerAddressRequest(serverAddress)));
    }

    public List<ApiModels.Member> getMembers(String worldId) throws ApiException {
        return orEmpty(GSON.fromJson(get("/api/mc/poi/worlds/" + worldId + "/members"), ApiModels.MembersResponse.class).members);
    }

    public void leaveWorld(String worldId) throws ApiException {
        post("/api/mc/poi/worlds/" + worldId + "/leave", "{}");
    }

    public List<ApiModels.InviteResult> invitePlayers(String worldId, String role, List<ApiModels.Player> players) throws ApiException {
        String body = GSON.toJson(new ApiModels.InvitePlayersRequest(role, players));
        return orEmpty(GSON.fromJson(post("/api/mc/poi/worlds/" + worldId + "/invites", body), ApiModels.InvitePlayersResponse.class).results);
    }

    public List<ApiModels.Invite> getInvites(String worldId) throws ApiException {
        return orEmpty(GSON.fromJson(get("/api/mc/poi/worlds/" + worldId + "/invites"), ApiModels.InvitesResponse.class).invites);
    }

    public void revokeInvite(String worldId, String inviteId) throws ApiException {
        delete("/api/mc/poi/worlds/" + worldId + "/invites/" + inviteId);
    }

    public List<ApiModels.PendingInvite> getPendingInvites() throws ApiException {
        return orEmpty(GSON.fromJson(get("/api/mc/poi/invites"), ApiModels.PendingInvitesResponse.class).invites);
    }

    /** Accepts or declines an invite sent to you; returns the world's id. */
    public String respondToInvite(String inviteId, boolean accept) throws ApiException {
        String json = post("/api/mc/poi/invites/" + inviteId + (accept ? "/accept" : "/decline"), "{}");
        return accept ? GSON.fromJson(json, ApiModels.WorldIdResponse.class).worldId : null;
    }

    // ── Account linking ────────────────────────────────────────────────────────

    public String createLinkNonce() throws ApiException {
        return GSON.fromJson(post("/api/mc/poi/link/nonce", "{}"), ApiModels.NonceResponse.class).nonce;
    }

    public ApiModels.LinkedAccount link(String username, String nonce) throws ApiException {
        String body = GSON.toJson(new ApiModels.LinkRequest(username, nonce));
        return GSON.fromJson(post("/api/mc/poi/link", body), ApiModels.LinkResponse.class).account;
    }

    public List<ApiModels.LinkedAccount> getLinkedAccounts() throws ApiException {
        return orEmpty(GSON.fromJson(get("/api/mc/poi/link"), ApiModels.LinkedAccountsResponse.class).accounts);
    }

    public void unlink(String uuid) throws ApiException {
        delete("/api/mc/poi/link/" + uuid);
    }

    /** A missing list in a response reads as empty, so a newer site can't break older builds. */
    private static <T> List<T> orEmpty(List<T> list) {
        return list != null ? list : List.of();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // ── HTTP helpers ───────────────────────────────────────────────────────────

    /**
     * Resolves an API path against the configured base URL. Refuses plain HTTP
     * except to a local development host, so the API key is never sent in clear text.
     */
    private static URI uri(String path) throws ApiException {
        URI uri = URI.create(PoiConfig.get().getApiBaseUrl() + path);
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !isLocalHost(uri.getHost())) {
            throw new ApiException("Refusing to send the API key over insecure " + uri.getScheme()
                    + " to " + uri.getHost() + " — the API URL must use https");
        }
        return uri;
    }

    private static boolean isLocalHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("localhost") || h.endsWith(".localhost")
                || h.equals("127.0.0.1") || h.equals("[::1]") || h.equals("::1");
    }

    private void delete(String path) throws ApiException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(uri(path))
                .header("Authorization", "Bearer " + effectiveApiKey())
                .header("X-POI-Client", CLIENT_HEADER)
                .header("Accept", "application/json")
                .DELETE()
                .timeout(Duration.ofSeconds(15))
                .build();
        send(req); // response body unused for DELETE
    }

    private String get(String path) throws ApiException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(uri(path))
                .header("Authorization", "Bearer " + effectiveApiKey())
                .header("X-POI-Client", CLIENT_HEADER)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();
        return send(req);
    }

    private String post(String path, String jsonBody) throws ApiException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(uri(path))
                .header("Authorization", "Bearer " + effectiveApiKey())
                .header("X-POI-Client", CLIENT_HEADER)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(15))
                .build();
        return send(req);
    }

    private String patch(String path, String jsonBody) throws ApiException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(uri(path))
                .header("Authorization", "Bearer " + effectiveApiKey())
                .header("X-POI-Client", CLIENT_HEADER)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(15))
                .build();
        return send(req);
    }

    private String send(HttpRequest req) throws ApiException {
        LOGGER.info("[POI] Request: {} {}", req.method(), req.uri());
        HttpResponse<String> resp;
        try {
            resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            LOGGER.error("[POI] HTTP request failed", e);
            throw new ApiException("Network error: " + e.getMessage());
        }

        int status = resp.statusCode();
        if (status >= 200 && status < 300) return resp.body();
        int retryAfter = resp.headers().firstValue("Retry-After").map(ApiClient::parseSeconds).orElse(0);
        if (status == 401) {
            throw new ApiException("Unauthorized — check your API key with /poi setkey", status, retryAfter);
        }
        // The site explains most refusals (a role that can't do this, an expired invite, ...)
        String message = serverMessage(resp.body());
        if (message != null) throw new ApiException(message, status, retryAfter);
        throw new ApiException(status == 404 ? "Not found (404)" : "Server returned HTTP " + status, status, retryAfter);
    }

    /** Retry-After in seconds; the HTTP-date form is rare enough to treat as "not given". */
    private static int parseSeconds(String value) {
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String serverMessage(String body) {
        try {
            ApiModels.ErrorResponse err = GSON.fromJson(body, ApiModels.ErrorResponse.class);
            return err != null && err.error != null && !err.error.isBlank() ? err.error : null;
        } catch (Exception e) {
            return null; // not JSON
        }
    }

    // ── Exception type ─────────────────────────────────────────────────────────

    public static class ApiException extends Exception {
        /** The HTTP status, or 0 when the request never got an answer. */
        public final int status;
        /** Seconds the server asked us to wait (Retry-After), or 0. */
        public final int retryAfterSeconds;

        public ApiException(String message) {
            this(message, 0, 0);
        }

        public ApiException(String message, int status) {
            this(message, status, 0);
        }

        public ApiException(String message, int status, int retryAfterSeconds) {
            super(message);
            this.status = status;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        /** Too many requests, or the site is struggling: slow down. */
        public boolean isBusy() { return status == 429 || status == 503; }

        public boolean isForbidden() { return status == 403; }
        public boolean isNotFound()  { return status == 404; }
    }
}
