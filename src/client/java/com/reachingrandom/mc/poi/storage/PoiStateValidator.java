package com.reachingrandom.mc.poi.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates POI state JSON files against the bundled {@code poi-state-v1.json} schema.
 *
 * <p>The schema is loaded once from the mod JAR on first use. If the schema cannot
 * be loaded (e.g. the resource is missing), validation is skipped with a warning
 * rather than hard-failing the build or game startup.
 */
public final class PoiStateValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger("points-of-interest");
    private static final String SCHEMA_RESOURCE = "/schemas/poi-state-v1.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonSchema SCHEMA;

    static {
        JsonSchema loaded = null;
        try (InputStream is = PoiStateValidator.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (is == null) {
                LOGGER.warn("[POI] Schema resource not found at {}; validation will be skipped.", SCHEMA_RESOURCE);
            } else {
                JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
                loaded = factory.getSchema(is);
                LOGGER.info("[POI] Loaded POI state schema (poi-state-v1.json).");
            }
        } catch (Exception e) {
            LOGGER.error("[POI] Failed to load JSON schema; validation will be skipped.", e);
        }
        SCHEMA = loaded;
    }

    private PoiStateValidator() {}

    /**
     * Validates {@code json} against the schema.
     *
     * @return a list of human-readable error messages, empty if the document is valid
     *         or if the schema could not be loaded (fail-open).
     */
    public static List<String> validate(String json) {
        if (SCHEMA == null) return List.of();

        try {
            JsonNode node = MAPPER.readTree(json);
            Set<ValidationMessage> errors = SCHEMA.validate(node);
            return errors.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of("Could not parse JSON: " + e.getMessage());
        }
    }
}
