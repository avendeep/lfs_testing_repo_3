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
import java.util.regex.Pattern;

/**
 * Reads api-automation-root/src/payload/api-payload.json -- the flat, "METHOD /path"-keyed
 * global payload file described in testcase-to-feature/SKILL.md. Distinct from the older,
 * [requiredFlag, ""]-templated api-payloads.json {@link PayloadResolver} reads -- that
 * file/class are untouched, still live for any feature still written the old way.
 *
 * Every entry here is already a full base payload: each value is a literal, an ApiContext
 * "{{name}}" journey reference, or a "{{random.field}}" token. Neither kind of token is
 * resolved here -- {@link ApiContext#resolvePlaceholders} does that once the caller has
 * finished layering a Test Case's overrides/remove/add on top of the copy this returns.
 */
public class GlobalPayloadResolver {
    private static final Path PAYLOAD_PATH = Paths.get("src/payload/api-payload.json");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static JsonNode root;

    static {
        if (Files.exists(PAYLOAD_PATH)) {
            try (InputStream input = Files.newInputStream(PAYLOAD_PATH)) {
                root = JSON.readTree(input);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private GlobalPayloadResolver() {
    }

    /**
     * Fresh, mutable copy of the base payload for "METHOD path" -- callers apply a Test Case's
     * remove/overrides/add on top of THIS copy, never the cached JsonNode itself.
     */
    public static Map<String, Object> resolve(String method, String path) {
        JsonNode entry = findEntry(method, path);
        Map<String, Object> payload = new LinkedHashMap<>();
        if (entry.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = entry.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                payload.put(field.getKey(), JSON.convertValue(field.getValue(), Object.class));
            }
        }
        return payload;
    }

    // "{{userId}}" (ApiContext double-brace style, as a feature file's own "When I send"
    // step writes a path) and "{userId}" (OpenAPI single-brace style, as Swagger/
    // api-payload.json key it) name the same path parameter -- normalize the feature file's
    // form to the spec's form before the exact-string key lookup below, so a journey step's
    // resolved-at-runtime path still matches its own payload template.
    private static final Pattern DOUBLE_BRACE = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    private static JsonNode findEntry(String method, String path) {
        if (root == null) {
            throw new IllegalStateException(
                "api-payload.json not found at " + PAYLOAD_PATH.toAbsolutePath()
                    + " -- it is generated once per workspace during initialization; regenerate it before running this scenario.");
        }
        String upperMethod = method.toUpperCase();
        JsonNode entry = root.get(upperMethod + " " + path);
        if (entry == null) {
            String normalizedPath = DOUBLE_BRACE.matcher(path).replaceAll("{$1}");
            entry = root.get(upperMethod + " " + normalizedPath);
        }
        if (entry == null) {
            throw new IllegalStateException(
                "No entry found in api-payload.json for \"" + upperMethod + " " + path
                    + "\" -- confirm this endpoint exists in the workspace's Swagger/API definitions.");
        }
        return entry;
    }
}
