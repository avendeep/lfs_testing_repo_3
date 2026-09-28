package com.automation.api.steps;

import com.automation.api.utils.ApiRequestBuilder;
import com.automation.api.utils.GlobalPayloadResolver;
import com.automation.api.utils.RandomTokenResolver;
import com.automation.api.utils.SuiteAuthContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.BeforeAll;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.OutputStream;
import java.util.Map;
import java.util.Properties;

/**
 * Runs once per suite (JVM-level, before any scenario) -- self-registers one throwaway real
 * account so ordinary Login/authenticated scenarios have a genuinely working
 * {{auth.username}}/{{auth.password}} account without needing any secret on disk or in git.
 * Populates SuiteAuthContext, which AuthCredentialsResolver checks before falling back to
 * auth_credentials.properties -- see that class.
 *
 * Reuses the register endpoint's OWN api-payload.json base entry (the exact same base a
 * generated Register feature's TC-ID mechanism would resolve) rather than building a separate
 * payload here, so the account this hook creates is never out of sync with what a Register
 * scenario itself would send -- and since that base entry's identifier/email fields already
 * carry "{{random.field}}" tokens (see workspace_init_service.py), a fresh account is created
 * on every suite run, never colliding with a prior run's "already registered" data.
 *
 * A complete no-op (nothing populated) when the workspace has no detected register endpoint
 * (src/test/resources/config/suite-auth.properties absent -- see workspace_init_service.py's
 * _write_suite_auth_config). When the register call itself fails instead (network issue, schema
 * mismatch, endpoint requires something this hook doesn't send), the failure is recorded via
 * writeDiagnostic() into allure-results/environment.properties -- picked up by `mvn
 * allure:report` and rendered in the report's Environment tab, so it's visible in
 * history-reports/ even though the process's own stdout only ever reached the ephemeral exec
 * directory. Either way, Login/other scenarios then simply fall back to
 * auth_credentials.properties exactly as they did before this hook existed -- self-registering
 * a convenience account is optional, never a suite-wide hard requirement.
 */
public class RegisterOnceHook {
    private static final Path CONFIG_PATH = Paths.get("src/test/resources/config/suite-auth.properties");
    // allure-cucumber7-jvm writes raw results here (see codegen_service.py); a
    // pre-existing environment.properties in this dir is picked up by `mvn allure:report`
    // and rendered into the report's Environment tab, which survives into history-reports/
    // -- unlike stdout/printStackTrace, which only ever reached the ephemeral exec dir's console.
    private static final Path ALLURE_ENV_PATH = Paths.get("allure-results/environment.properties");
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String[] IDENTIFIER_FIELD_KEYWORDS = {"email", "username", "userid", "login", "identifier"};
    private static final String[] PASSWORD_FIELD_KEYWORDS = {"password", "passwd", "pwd"};

    @BeforeAll
    public static void registerOnce() {
        String endpointKey = readRegisterEndpointKey();
        if (endpointKey == null) {
            return;
        }
        try {
            String[] parts = endpointKey.split(" ", 2);
            String method = parts[0];
            String path = parts[1];

            Map<String, Object> payload = GlobalPayloadResolver.resolve(method, path);
            // Resolve the WHOLE serialized payload in ONE RandomTokenResolver.resolve() call --
            // same approach as ApiContext.resolvePlaceholders for a real scenario's request
            // body, not the old field-by-field loop. Required so a "confirm password"-style
            // field can see and match whatever the plain "password" field resolved to in the
            // SAME call; see RandomTokenResolver's own doc for why that coordination only works
            // within one call.
            @SuppressWarnings("unchecked")
            Map<String, Object> resolvedPayload = JSON.readValue(
                RandomTokenResolver.resolve(JSON.writeValueAsString(payload)), Map.class);
            payload = resolvedPayload;

            RequestSpecification spec = ApiRequestBuilder.buildRequestSpec();
            spec.contentType("application/json");
            spec.body(JSON.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8));
            Response response = spec.post(path);
            if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
                writeDiagnostic("FAILED", "HTTP " + response.getStatusCode(), response.getBody().asString());
                return;
            }

            String username = findByKeyword(payload, IDENTIFIER_FIELD_KEYWORDS);
            String password = findByKeyword(payload, PASSWORD_FIELD_KEYWORDS);
            if (username != null && password != null) {
                SuiteAuthContext.set(username, password);
            } else {
                writeDiagnostic("FAILED", "no identifier/password field in response payload",
                        JSON.writeValueAsString(payload));
            }
        } catch (Exception e) {
            writeDiagnostic("EXCEPTION", e.toString(), null);
        }
    }

    /**
     * Persists a register-attempt failure so it's still visible after the ephemeral exec
     * directory (and its console output) is gone -- see ALLURE_ENV_PATH. Best-effort: a
     * failure while writing this diagnostic must never fail the suite.
     */
    private static void writeDiagnostic(String status, String detail, String responseBody) {
        try {
            Properties env = new Properties();
            if (Files.exists(ALLURE_ENV_PATH)) {
                try (InputStream input = Files.newInputStream(ALLURE_ENV_PATH)) {
                    env.load(input);
                }
            }
            env.setProperty("RegisterOnceHook.status", status);
            env.setProperty("RegisterOnceHook.detail", truncate(detail));
            if (responseBody != null) {
                env.setProperty("RegisterOnceHook.responseBody", truncate(responseBody));
            }
            Files.createDirectories(ALLURE_ENV_PATH.getParent());
            try (OutputStream output = Files.newOutputStream(ALLURE_ENV_PATH)) {
                env.store(output, "Written by RegisterOnceHook on register-attempt failure");
            }
        } catch (Exception ignored) {
            // Diagnostics are best-effort -- never let a logging failure fail the suite.
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        String flattened = value.replaceAll("\\s+", " ").trim();
        return flattened.length() > 500 ? flattened.substring(0, 500) + "...(truncated)" : flattened;
    }

    private static String readRegisterEndpointKey() {
        if (!Files.exists(CONFIG_PATH)) {
            return null;
        }
        try (InputStream input = Files.newInputStream(CONFIG_PATH)) {
            Properties props = new Properties();
            props.load(input);
            String value = props.getProperty("register.endpoint");
            return (value == null || value.isBlank()) ? null : value.trim();
        } catch (IOException e) {
            return null;
        }
    }

    private static String findByKeyword(Map<String, Object> payload, String[] keywords) {
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String keyLower = entry.getKey().toLowerCase();
            for (String keyword : keywords) {
                if (keyLower.contains(keyword)) {
                    return String.valueOf(entry.getValue());
                }
            }
        }
        return null;
    }
}
