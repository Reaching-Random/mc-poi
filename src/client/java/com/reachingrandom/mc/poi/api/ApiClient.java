package com.reachingrandom.mc.poi.api;

import com.google.gson.Gson;
import com.reachingrandom.mc.poi.config.PoiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

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

    // ── HTTP helpers ───────────────────────────────────────────────────────────

    private void delete(String path) throws ApiException {
        PoiConfig cfg = PoiConfig.get();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(cfg.getApiBaseUrl() + path))
                .header("Authorization", "Bearer " + effectiveApiKey())
                .header("Accept", "application/json")
                .DELETE()
                .timeout(Duration.ofSeconds(15))
                .build();
        send(req); // response body unused for DELETE
    }

    private String get(String path) throws ApiException {
        PoiConfig cfg = PoiConfig.get();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(cfg.getApiBaseUrl() + path))
                .header("Authorization", "Bearer " + effectiveApiKey())
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();
        return send(req);
    }

    private String post(String path, String jsonBody) throws ApiException {
        PoiConfig cfg = PoiConfig.get();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(cfg.getApiBaseUrl() + path))
                .header("Authorization", "Bearer " + effectiveApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(15))
                .build();
        return send(req);
    }

    private String patch(String path, String jsonBody) throws ApiException {
        PoiConfig cfg = PoiConfig.get();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(cfg.getApiBaseUrl() + path))
                .header("Authorization", "Bearer " + effectiveApiKey())
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

        if (resp.statusCode() == 401) {
            throw new ApiException("Unauthorized — check your API key with /poi setkey");
        }
        if (resp.statusCode() == 404) {
            throw new ApiException("Not found (404)");
        }
        if (resp.statusCode() == 409) {
            throw new ApiException("Data migration required — visit the website to migrate your data");
        }
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            // Try to extract error message from JSON body
            try {
                ApiModels.ErrorResponse err = GSON.fromJson(resp.body(), ApiModels.ErrorResponse.class);
                if (err != null && err.error != null) {
                    throw new ApiException("Server error: " + err.error);
                }
            } catch (Exception ignored) {}
            throw new ApiException("Server returned HTTP " + resp.statusCode());
        }

        return resp.body();
    }

    // ── Exception type ─────────────────────────────────────────────────────────

    public static class ApiException extends Exception {
        public ApiException(String message) {
            super(message);
        }
    }
}
