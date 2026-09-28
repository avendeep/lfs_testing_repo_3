package com.automation.api.steps;

import com.automation.api.utils.ApiRequestBuilder;
import com.automation.api.utils.GlobalPayloadResolver;
import com.automation.api.utils.PayloadResolver;
import com.automation.api.utils.SchemaValidator;
import com.automation.api.utils.StatusCodeValidator;
import com.automation.api.utils.TcTestDataResolver;
import com.automation.api.utils.TestDataGenerator;
import com.automation.api.utils.TestDataResolver;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.config.EncoderConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import org.testng.Assert;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ApiSteps {
    private final ApiContext context;
    private static final ObjectMapper JSON = new ObjectMapper();
    // Tracked per-scenario (not per-request) because the spec is long-lived across steps --
    // once a scenario explicitly declares its own Content-Type, every write on that spec needs
    // the charset-append workaround below, not just the first one.
    private boolean explicitContentTypeSet = false;
    // Captured once per scenario via @Before -- feeds TestDataResolver/TcTestDataResolver so
    // neither needs a manually-typed (and typo-able) file name in the feature file itself.
    private String featureSlug;
    // The scenario's own "@TC-..." tag (testcase-to-feature/SKILL.md) -- null for a scenario
    // that carries no such tag, e.g. one still written against the older "I use test data"/
    // "I apply value" mechanism, or one with no per-Test-Case data at all.
    private String activeTcId;

    public ApiSteps(ApiContext context) {
        this.context = context;
    }

    @Before
    public void captureFeatureSlug(Scenario scenario) {
        String uri = scenario.getUri().toString();
        String fileName = uri.substring(uri.lastIndexOf('/') + 1);
        featureSlug = fileName.replaceAll("\\.feature$", "");
        activeTcId = scenario.getSourceTagNames().stream()
            .map(tag -> tag.startsWith("@") ? tag.substring(1) : tag)
            .filter(tag -> tag.toUpperCase().startsWith("TC-"))
            .findFirst()
            .orElse(null);
    }

    @Given("the API base URL is set")
    public void theApiBaseUrlIsSet() {
        RequestSpecification spec = ApiRequestBuilder.buildRequestSpec();
        context.save("requestSpec", spec);
    }

    @Given("I have a valid authentication token")
    public void iHaveAValidAuthenticationToken() {
        // Deliberate no-op -- this module has no live auth/token component today. An endpoint
        // that truly requires auth surfaces a real 401/403 downstream instead of every scenario
        // dying on a generic "no token" assertion here.
    }

    @Given("I have the following headers:")
    public void iHaveTheFollowingHeaders(DataTable table) {
        RequestSpecification spec = (RequestSpecification) context.get("requestSpec");
        for (List<String> row : table.asLists()) {
            String name = row.get(0);
            String value = context.resolvePlaceholders(row.get(1));
            // replaceHeader, not header() -- header()/headers() ADD rather than replace, so
            // setting the same header twice in one scenario (e.g. Authorization reused across
            // two calls in a journey) sends it duplicated, which some servers 400 on.
            ((FilterableRequestSpecification) spec).replaceHeader(name, value);
            if ("Content-Type".equalsIgnoreCase(name)) {
                explicitContentTypeSet = true;
            }
        }
    }

    @Given("I have the following payload:")
    public void iHaveTheFollowingPayload(String payload) {
        context.save("requestBody", context.resolvePlaceholders(payload));
    }

    @Given("I have a fresh payload for the following fields:")
    public void iHaveAFreshPayloadForTheFollowingFields(DataTable table) throws Exception {
        Map<String, String> fields = table.asMap(String.class, String.class);
        Map<String, Object> payload = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            Object value = TestDataGenerator.generateValueForField(entry.getKey(), entry.getValue(), "positive");
            payload.put(entry.getKey(), value);
            // Saved into ApiContext too, not just the outgoing JSON -- so a later assertion can
            // reference the exact value this step generated via "{{fieldName}}" instead of a
            // second, unrelated "{{random.fieldName}}" generation.
            context.save(entry.getKey(), value);
        }
        context.save("requestBody", JSON.writeValueAsString(payload));
    }

    @Given("I have a fresh payload using the stored payload template for {string} {string}")
    public void iHaveAFreshPayloadUsingTheStoredPayloadTemplateFor(String method, String path) throws Exception {
        Map<String, Object> payload = PayloadResolver.resolve(method, path);
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            // Same ApiContext save-per-field convention as the DataTable-driven fresh-payload
            // step above -- lets a later step reference any one field as "{{fieldName}}".
            context.save(entry.getKey(), entry.getValue());
        }
        context.save("requestBody", JSON.writeValueAsString(payload));
    }

    @Given("I use test data {string} for this feature")
    public void iUseTestDataForThisFeature(String model) {
        Map<String, Object> payload = TestDataResolver.loadBase(featureSlug, model);
        context.save("activeTestDataModel", model);
        context.save("payload", payload);
        writeRequestBody(payload);
    }

    @Given("I apply value {string}")
    public void iApplyValue(String key) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) context.get("payload");
        String model = context.getString("activeTestDataModel");
        TestDataResolver.applyValue(payload, featureSlug, model, key);
        writeRequestBody(payload);
    }

    // Re-serializes after every base-load/apply-value so "requestBody" always reflects the
    // payload map's current state, and resolves "{{name}}" cross-step references (e.g.
    // "email:duplicate": "{{existing.user.email}}") at this point -- unlike the DataTable-driven
    // and PayloadResolver-driven steps above, the request body is built once, well before send
    // time, here, so resolution can't be deferred to iSendARequestTo.
    private void writeRequestBody(Map<String, Object> payload) {
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            context.save(entry.getKey(), entry.getValue());
        }
        try {
            context.save("requestBody", context.resolvePlaceholders(JSON.writeValueAsString(payload)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize test-data payload to JSON", e);
        }
    }

    // testcase-to-feature/SKILL.md's TC-ID-driven flow writes no explicit payload-building
    // Given at all -- the body is built here, automatically, from this scenario's own @TC-...
    // entry in testdata/<slug>.json, dispatched on that entry's request_body.type (framework/
    // SKILL.md's TC-ID Test Data section has the full table). Only kicks in when nothing
    // upstream already set "requestBody" (an explicit "I have the following payload:"/"I use
    // test data ..."/fresh-payload step always wins) and the scenario actually carries a
    // @TC-... tag -- a body-bearing request with neither is left exactly as before (no body
    // sent), same as prior behavior.
    private void maybeAutoResolvePayload(String method, String path) {
        if (context.has("requestBody") || activeTcId == null) {
            return;
        }
        if (!("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method))) {
            return;
        }
        String type = TcTestDataResolver.requestBodyType(featureSlug, activeTcId, method, path);
        switch (type.toUpperCase()) {
            case "NORMAL" -> {
                Map<String, Object> payload = GlobalPayloadResolver.resolve(method, path);
                TcTestDataResolver.applyTestCase(payload, featureSlug, activeTcId, method, path);
                writeResolvedRequestBody(payload);
            }
            case "EMPTY_OBJECT" -> context.save("requestBody", "{}");
            case "EMPTY_ARRAY" -> context.save("requestBody", "[]");
            case "NULL" -> context.save("requestBody", "null");
            // Deliberately does NOT save "requestBody" -- iSendARequestTo then sends no body at
            // all, same as a POST/PUT/PATCH scenario that never builds one today.
            case "MISSING_BODY" -> { }
            case "MALFORMED_JSON", "RAW_JSON" -> context.save("requestBody",
                context.resolvePlaceholders(TcTestDataResolver.requestBodyRawValue(featureSlug, activeTcId, method, path)));
            default -> throw new IllegalStateException(
                "Unsupported request_body.type \"" + type + "\" for TC \"" + activeTcId + "\" in " + featureSlug
                    + " -- ApiSteps.java only supports NORMAL/MALFORMED_JSON/RAW_JSON/EMPTY_OBJECT/EMPTY_ARRAY/NULL/MISSING_BODY.");
        }
    }

    // Resolves ApiContext/"{{name}}" references and "{{random.field}}" tokens on the WHOLE
    // serialized payload first, then re-parses the resolved JSON to save each field back into
    // ApiContext under its own name -- saving the raw, pre-resolution map (as the older
    // writeRequestBody above does, safe there since that mechanism never carries
    // "{{random.field}}" tokens) would let a later "{{fieldName}}" reference pick up an
    // unresolved literal token string instead of the actual generated/referenced value.
    private void writeResolvedRequestBody(Map<String, Object> payload) {
        try {
            String rawJson = JSON.writeValueAsString(payload);
            String resolvedJson = context.resolvePlaceholders(rawJson);
            context.save("requestBody", resolvedJson);
            @SuppressWarnings("unchecked")
            Map<String, Object> resolvedPayload = JSON.readValue(resolvedJson, Map.class);
            for (Map.Entry<String, Object> entry : resolvedPayload.entrySet()) {
                context.save(entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to build request body for TC \"" + activeTcId + "\" in " + featureSlug, e);
        }
    }

    @When("I send a {string} request to {string}")
    public void iSendARequestTo(String method, String path) {
        maybeAutoResolvePayload(method, path);
        RequestSpecification spec = (RequestSpecification) context.get("requestSpec");
        String resolvedPath = context.resolvePlaceholders(path);
        String requestBody = context.has("requestBody") ? context.getString("requestBody") : null;

        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
            if (!explicitContentTypeSet) {
                spec.contentType("application/json");
            } else {
                // RestAssured appends its default charset (ISO-8859-1) to ANY Content-Type it
                // doesn't specifically recognize, even one set explicitly via replaceHeader --
                // e.g. "application/json; v=1.0" silently becomes
                // "application/json; v=1.0; charset=ISO-8859-1" on the wire, which
                // fakerestapi's (.NET/ASP.NET Core) strict media-type parser rejects with 415.
                // Confirmed live against fakerestapi.azurewebsites.net. Disable that auto-append
                // whenever the scenario declared its own Content-Type -- send exactly what the
                // feature file specified, nothing added.
                spec.config(RestAssuredConfig.newConfig().encoderConfig(
                    EncoderConfig.encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)));
            }
            // Always byte[], never String -- a String body routes through a Groovy
            // charset-encoding path that throws ClassCastException on this JDK/Groovy pairing.
            if (requestBody != null) {
                spec.body(requestBody.getBytes(StandardCharsets.UTF_8));
            }
        }

        // Dispatch via the spec's own single-purpose method -- never RestAssured.given(spec)
        // (silently drops the body on merge) and never the generic spec.request(method, path).
        Response response = switch (method.toUpperCase()) {
            case "GET" -> spec.get(resolvedPath);
            case "POST" -> spec.post(resolvedPath);
            case "PUT" -> spec.put(resolvedPath);
            case "DELETE" -> spec.delete(resolvedPath);
            case "PATCH" -> spec.patch(resolvedPath);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        };

        context.save("response", response);
        context.save("lastMethod", method);
        context.save("lastPath", resolvedPath);
        context.save("requestEvidence", method + " " + resolvedPath + (requestBody != null ? "\nBody: " + requestBody : ""));
        context.save("responseEvidence", "Status: " + response.getStatusCode() + "\nBody: " + safeBody(response));
        // Journey scenarios send several requests through this same ApiContext instance --
        // drop the body now so the NEXT "When I send" rebuilds its own (explicit payload step
        // or maybeAutoResolvePayload) instead of silently resending this one's.
        context.clear("requestBody");
    }

    @Then("the response status code should be {int}")
    public void theResponseStatusCodeShouldBe(int expectedStatusCode) {
        Response response = (Response) context.get("response");
        StatusCodeValidator.validate(response, expectedStatusCode);
    }

    @Then("the response should match the response schema")
    public void theResponseShouldMatchTheResponseSchema() {
        Response response = (Response) context.get("response");
        String slug = slugify(context.getString("lastMethod"), context.getString("lastPath"));
        SchemaValidator.validate(response, slug + "_response_schema.json");
    }

    @Then("I save {string} from the response as {string}")
    public void iSaveFromTheResponseAs(String gPath, String name) {
        Response response = (Response) context.get("response");
        // Defensive strip of a leading "$." -- GPath (not JSONPath) is correct here; a leading
        // "$." silently resolves to null instead of erroring, surfacing as a confusing failure
        // two steps later. New step text should be written in GPath from the start.
        String cleanedPath = gPath.startsWith("$.") ? gPath.substring(2) : gPath;
        Object value = JsonPath.from(response.getBody().asString()).get(cleanedPath);
        if (value == null) {
            throw new IllegalStateException(
                "GPath \"" + gPath + "\" resolved to null on the response -- check the response shape or the path.");
        }
        context.save(name, value);
    }

    @Then("the response field {string} should be {string}")
    public void theResponseFieldShouldBe(String gPath, String expected) {
        Response response = (Response) context.get("response");
        String resolvedExpected = context.resolvePlaceholders(expected);
        Object actual = JsonPath.from(response.getBody().asString()).get(gPath);
        Assert.assertEquals(String.valueOf(actual), resolvedExpected, "Field \"" + gPath + "\" mismatch!");
    }

    // testcase-to-feature/SKILL.md's saveRequestValue() -- reads from the already-built (but
    // not-yet-sent) "requestBody", not the response. Typical use: capture the fresh
    // "{{random.email}}" value TC-USER-001's positive payload just generated so a later,
    // separate TC (e.g. a duplicate-email negative case) can reference it via "{{name}}"
    // instead of guessing/duplicating that value.
    @Given("I save {string} from the request as {string}")
    public void iSaveFromTheRequestAs(String gPath, String name) {
        if (!context.has("requestBody")) {
            throw new IllegalStateException(
                "No request body available to save \"" + gPath + "\" from -- run a payload-building step first.");
        }
        String cleanedPath = gPath.startsWith("$.") ? gPath.substring(2) : gPath;
        Object value = JsonPath.from(context.getString("requestBody")).get(cleanedPath);
        if (value == null) {
            throw new IllegalStateException(
                "GPath \"" + gPath + "\" resolved to null on the request body -- check the payload or the path.");
        }
        context.save(name, value);
    }

    // testcase-to-feature/SKILL.md's validateResponseFieldNotNull().
    @Then("the response field {string} should not be null")
    public void theResponseFieldShouldNotBeNull(String gPath) {
        Response response = (Response) context.get("response");
        String cleanedPath = gPath.startsWith("$.") ? gPath.substring(2) : gPath;
        Object value = JsonPath.from(response.getBody().asString()).get(cleanedPath);
        Assert.assertNotNull(value, "Field \"" + gPath + "\" was null in the response, expected non-null value.");
    }

    @Then("the response time should be below {int} ms")
    public void theResponseTimeShouldBeBelowMs(int maxMillis) {
        Response response = (Response) context.get("response");
        Assert.assertTrue(response.time() <= maxMillis,
            "Response time " + response.time() + "ms exceeded max " + maxMillis + "ms");
    }

    @After
    public void attachEvidence(Scenario scenario) {
        if (context.has("requestEvidence")) {
            scenario.attach(context.getString("requestEvidence"), "text/plain", "Request");
        }
        if (context.has("responseEvidence")) {
            scenario.attach(context.getString("responseEvidence"), "text/plain", "Response");
        }
    }

    private static String safeBody(Response response) {
        try {
            return response.getBody().asPrettyString();
        } catch (Exception e) {
            return response.getBody().asString();
        }
    }

    // Same slug formula the generated feature-file/schema names use:
    // "<method> <path>" lowercased, every non-alphanumeric char replaced with "_", leading
    // "_" stripped -- e.g. "POST /api/v1/Books" -> "post__api_v1_books".
    private static String slugify(String method, String path) {
        String slug = (method + " " + path).toLowerCase().replaceAll("[^a-z0-9]", "_");
        while (slug.startsWith("_")) {
            slug = slug.substring(1);
        }
        return slug;
    }
}
