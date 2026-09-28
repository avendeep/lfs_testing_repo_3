package com.automation.api.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads api-automation-root/src/payload/api-payloads.json (generated once per workspace, from
 * Swagger, by orbit_qa_platform's workspace_init_service._write_api_payloads_file) and turns an
 * endpoint's payload TEMPLATE ({@code {field: [requiredFlag, ""]}}, plus an optional real
 * "examples" value per field) into a real, ready-to-send payload -- the single source of truth
 * for a feature file's request-body FIELD NAMES, so a generated step never has to guess/invent
 * one (see ApiSteps.iHaveAFreshPayloadUsingTheStoredPayloadTemplateFor).
 *
 * Same relative-file-path pattern as EnvironmentResolver/AuthCredentialsResolver: resolved
 * against the working directory (api-automation-root/, true for both an IDE run and "mvn test"),
 * never a classpath resource.
 */
public class PayloadResolver {
    private static final Path PAYLOADS_PATH = Paths.get("src/payload/api-payloads.json");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static JsonNode root;

    static {
        if (Files.exists(PAYLOADS_PATH)) {
            try (InputStream input = Files.newInputStream(PAYLOADS_PATH)) {
                root = JSON.readTree(input);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private PayloadResolver() {
    }

    /**
     * Builds a fresh, ready-to-serialize payload for the endpoint matching {@code method} +
     * {@code path} -- every field name comes from the stored template, every value is either the
     * spec's own real example value (when one was captured) or a freshly generated one from
     * TestDataGenerator (using {@code strategy}, e.g. "positive"). Throws IllegalStateException
     * (a real signal, never swallowed -- same convention as ApiContext.get) when api-payloads.json
     * is missing or has no entry for this endpoint, since a feature file that reaches this step
     * genuinely needs the template to exist.
     */
    public static Map<String, Object> resolve(String method, String path, String strategy) {
        JsonNode entry = findEntry(method, path);
        JsonNode template = entry.get("payload");
        JsonNode examples = entry.get("examples");
        Map<String, Object> payload = new LinkedHashMap<>();
        if (template != null && template.isObject()) {
            buildFromTemplate(template, examples, strategy, payload);
        }
        return payload;
    }

    public static Map<String, Object> resolve(String method, String path) {
        return resolve(method, path, "positive");
    }

    private static void buildFromTemplate(JsonNode template, JsonNode examples, String strategy, Map<String, Object> out) {
        Iterator<Map.Entry<String, JsonNode>> fields = template.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            JsonNode value = field.getValue();
            JsonNode exampleValue = examples != null ? examples.get(key) : null;

            if (value.isObject()) {
                // Nested object template (see workspace_init_service._build_payload_template) --
                // recurse into its own fields/examples instead of treating it as a leaf.
                Map<String, Object> nested = new LinkedHashMap<>();
                buildFromTemplate(value, exampleValue, strategy, nested);
                out.put(key, nested);
                continue;
            }

            if (exampleValue != null && !exampleValue.isNull()) {
                out.put(key, JSON.convertValue(exampleValue, Object.class));
                continue;
            }

            // Leaf is always [requiredFlag, ""] -- the flag itself isn't needed to generate a
            // POSITIVE value (a fresh payload always fills every field, required or not); the
            // "" default gives TestDataGenerator's key-name heuristic (email/password/user/
            // date/...) the same string-typed hint it already gets from a DataTable typeHint.
            out.put(key, TestDataGenerator.generateValueForField(key, "", strategy));
        }
    }

    private static JsonNode findEntry(String method, String path) {
        if (root == null) {
            throw new IllegalStateException(
                "api-payloads.json not found at " + PAYLOADS_PATH.toAbsolutePath()
                    + " -- it is generated once per workspace during initialization; regenerate it before running this scenario.");
        }
        JsonNode entry = root.get(path);
        if (entry != null && entry.has("method")
            && !entry.get("method").asText("").equalsIgnoreCase(method)) {
            entry = null;
        }
        if (entry == null) {
            throw new IllegalStateException(
                "No payload template found in api-payloads.json for " + method + " " + path
                    + " -- confirm this endpoint exists in the workspace's Swagger/API definitions.");
        }
        return entry;
    }
}
