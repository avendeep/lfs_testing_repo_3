package com.automation.api.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-feature Test Case data, TC-ID keyed -- testcase-to-feature/SKILL.md's
 * overrides/remove/add shape. Distinct from the older per-model "base"/"values" shape
 * {@link TestDataResolver} reads -- that class/file are untouched, still live for any feature
 * still generated the old way.
 *
 * One JSON per feature file, same slug formula as before: post__api_v1_books.feature ->
 * src/test/resources/testdata/post__api_v1_books.json. {@code overrides}/{@code remove}/
 * {@code add} apply on top of {@link GlobalPayloadResolver}'s base payload, in the skill's
 * documented precedence: base -> remove -> overrides -> add -- this is the {@code NORMAL}
 * {@code request_body.type} (framework/SKILL.md's TC-ID Test Data section), also the default
 * when a TC entry carries no {@code request_body} at all. ApiContext/reference and
 * random-token resolution happen afterward, in the caller (ApiSteps), via
 * {@link ApiContext#resolvePlaceholders}.
 *
 * A TC entry needing a different body shape (empty/null/missing/malformed/raw) wraps
 * {@code overrides}/{@code remove}/{@code add} under an explicit {@code request_body} object
 * with a {@code type} -- see {@link #requestBodyType} / {@link #requestBodyRawValue} and
 * framework/SKILL.md's `request_body.type` table for the full contract.
 *
 * A journey scenario carries ONE {@code @TC-...} tag but sends several requests through
 * {@code iSendARequestTo} -- a TC entry needing DIFFERENT overrides per call wraps them under a
 * {@code "requests"} object keyed by the same literal {@code "<METHOD> <path>"} string
 * {@code api-payload.json} uses, e.g. {@code {"requests": {"POST /cart/items": {"overrides":
 * {...}}}}}. When {@code "requests"} is present, every method+path in it applies only its OWN
 * fields to that ONE call; any call in the journey with no matching entry gets the untouched
 * global base payload -- never another call's overrides. Omitting {@code "requests"} entirely
 * keeps the original one-scenario-one-request behavior unchanged: the TC entry's own top-level
 * {@code overrides}/{@code remove}/{@code add} (or {@code request_body}) apply to whichever
 * single request that TC builds.
 */
public class TcTestDataResolver {
    private static final Path TESTDATA_DIR = Paths.get("src/test/resources/testdata");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, JsonNode> CACHE = new ConcurrentHashMap<>();

    private TcTestDataResolver() {
    }

    /**
     * Mutates {@code payload} in place. A missing testdata file, a missing TC entry, or an
     * explicit {@code {}} entry are all treated the same, deliberate way: "base payload
     * unchanged" -- per testcase-to-feature/SKILL.md, {@code "TC-USER-001": {}} is a normal,
     * valid entry, not a generation defect (unlike the older TestDataResolver, where a missing
     * model/file IS always a hard failure). Only meaningful for the {@code NORMAL}
     * {@code request_body.type} -- callers check {@link #requestBodyType} first.
     */
    public static void applyTestCase(Map<String, Object> payload, String featureSlug, String tcId, String method, String path) {
        JsonNode tcNode = tcNode(featureSlug, tcId);
        if (tcNode == null) {
            return;
        }
        JsonNode callNode = callNode(tcNode, method, path);
        if (callNode == null) {
            return;
        }
        JsonNode fields = fieldsNode(callNode);

        JsonNode remove = fields.get("remove");
        if (remove != null && remove.isArray()) {
            for (JsonNode field : remove) {
                payload.remove(field.asText());
            }
        }

        JsonNode overrides = fields.get("overrides");
        if (overrides != null && overrides.isObject()) {
            putAll(payload, overrides);
        }

        JsonNode add = fields.get("add");
        if (add != null && add.isObject()) {
            putAll(payload, add);
        }
    }

    /**
     * {@code request_body.type} for this TC, defaulting to {@code "NORMAL"} when the TC entry,
     * its {@code request_body} object, or the {@code type} field itself is absent -- an entry
     * written the old way (bare {@code overrides}/{@code remove}/{@code add}, no
     * {@code request_body} wrapper at all) is exactly the {@code NORMAL} case, unchanged.
     */
    public static String requestBodyType(String featureSlug, String tcId, String method, String path) {
        JsonNode requestBody = requestBodyNode(featureSlug, tcId, method, path);
        JsonNode type = requestBody != null ? requestBody.get("type") : null;
        return type != null && !type.isNull() ? type.asText("NORMAL") : "NORMAL";
    }

    /**
     * The literal body text for a {@code MALFORMED_JSON}/{@code RAW_JSON} TC -- sent by the
     * caller exactly as returned, never parsed/re-serialized first (that's the whole point: a
     * {@code MALFORMED_JSON} value is genuinely invalid JSON, and must stay that way on the
     * wire). Fails fast when {@code value} is missing or not a string -- both types require it.
     */
    public static String requestBodyRawValue(String featureSlug, String tcId, String method, String path) {
        JsonNode requestBody = requestBodyNode(featureSlug, tcId, method, path);
        JsonNode value = requestBody != null ? requestBody.get("value") : null;
        if (value == null || !value.isTextual()) {
            throw new IllegalStateException(
                "request_body.value must be a JSON string for TC \"" + tcId + "\" in " + featureSlug
                    + " -- MALFORMED_JSON/RAW_JSON both require the literal body text under \"value\".");
        }
        return value.asText();
    }

    private static JsonNode requestBodyNode(String featureSlug, String tcId, String method, String path) {
        JsonNode tcNode = tcNode(featureSlug, tcId);
        if (tcNode == null) {
            return null;
        }
        JsonNode callNode = callNode(tcNode, method, path);
        if (callNode == null) {
            return null;
        }
        JsonNode requestBody = callNode.get("request_body");
        return requestBody != null && requestBody.isObject() ? requestBody : null;
    }

    // A TC entry carrying "requests" (journey, multiple calls under one TC tag) scopes
    // overrides/remove/add/request_body to ONE method+path -- returns that call's own object,
    // or null when this call has no entry under "requests" (base payload, untouched, is
    // correct for it). A TC entry with no "requests" key at all is the original
    // one-scenario-one-request shape -- the whole tcNode IS the call's fields, same as always.
    private static JsonNode callNode(JsonNode tcNode, String method, String path) {
        JsonNode requests = tcNode.get("requests");
        if (requests == null || !requests.isObject()) {
            return tcNode;
        }
        JsonNode match = requests.get(method.toUpperCase() + " " + path);
        return match != null && match.isObject() ? match : null;
    }

    // overrides/remove/add live inside "request_body" for a TC that also carries a "type", but
    // stay at the call node's own top level for the older, pre-request_body shape -- either way,
    // this is the one node applyTestCase reads all three from.
    private static JsonNode fieldsNode(JsonNode callNode) {
        JsonNode requestBody = callNode.get("request_body");
        return requestBody != null && requestBody.isObject() ? requestBody : callNode;
    }

    private static JsonNode tcNode(String featureSlug, String tcId) {
        if (tcId == null) {
            return null;
        }
        JsonNode root = load(featureSlug);
        if (root == null) {
            return null;
        }
        JsonNode node = root.get(tcId);
        return node != null && node.isObject() ? node : null;
    }

    private static void putAll(Map<String, Object> payload, JsonNode node) {
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            // convertValue(..., Object.class) turns an explicit JSON null into a real Java
            // null (payload.put(key, null)), not a skipped entry -- SKILL.md explicitly
            // allows {"overrides": {"email": null}}.
            payload.put(field.getKey(), JSON.convertValue(field.getValue(), Object.class));
        }
    }

    private static JsonNode load(String featureSlug) {
        return CACHE.computeIfAbsent(featureSlug, slug -> {
            Path path = testDataPath(slug);
            if (!Files.exists(path)) {
                return null;
            }
            try (InputStream input = Files.newInputStream(path)) {
                return JSON.readTree(input);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to read " + path.toAbsolutePath(), e);
            }
        });
    }

    private static Path testDataPath(String featureSlug) {
        return TESTDATA_DIR.resolve(featureSlug + ".json");
    }
}
