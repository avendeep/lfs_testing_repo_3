package com.automation.api.utils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Resolves a {{auth.username}}/{{auth.password}}/{{auth.otp}} token from one of two sources,
 * checked in this order:
 *   1. SuiteAuthContext -- the throwaway real account RegisterOnceHook self-registered once
 *      for this suite run, if the workspace has a detected register endpoint (see
 *      RegisterOnceHook). Never username/password AND otp at once -- a self-registered
 *      account is never OTP-verified, so "otp" always falls through to source 2.
 *   2. src/test/resources/testdata/auth_credentials.properties -- a real, pre-existing test
 *      account's credentials, for the cases self-registration can't produce (a specific role,
 *      an OTP-verified account, a "duplicate email" negative test). That file is materialized
 *      locally from AWS Secrets Manager at sync time (secrets_sync_service.py) and is never
 *      committed with a real value.
 *
 * Either way, a {{auth.username}}/{{auth.password}}/{{auth.otp}} token in a feature file's
 * api-payload.json/testdata/<slug>.json entry is safe to commit -- it is only a reference,
 * resolved here at run time, never the real value itself.
 *
 * Same "auth.username"/"auth.password"/"auth.otp.value" keys ApiConfigManager's own
 * documentation already describes -- see auth_credentials.properties itself for the current
 * shape (it also carries legacy "testuser.*" duplicates of the same values for backward
 * compatibility with any feature file still written against those key names directly).
 */
public class AuthCredentialsResolver {
    private static final Path PROPERTIES_PATH = Paths.get("src/test/resources/testdata/auth_credentials.properties");
    private static Properties properties;

    static {
        if (Files.exists(PROPERTIES_PATH)) {
            properties = new Properties();
            try (InputStream input = Files.newInputStream(PROPERTIES_PATH)) {
                properties.load(input);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to read " + PROPERTIES_PATH.toAbsolutePath(), e);
            }
        }
    }

    private AuthCredentialsResolver() {
    }

    /**
     * {@code field} is the part of the "{{auth.<field>}}" token after "auth." -- "username",
     * "password", or "otp". Checks SuiteAuthContext first for "username"/"password" (never
     * "otp" -- see class docs); falls through to auth_credentials.properties otherwise, and
     * fails fast there (never silently substitutes an empty string) when the properties file
     * itself is missing, or when it exists but this key was never synced down (e.g. no
     * APP_USERNAME/APP_PASSWORD/APP_OTP configured yet for this workspace/user).
     */
    public static String get(String field) {
        if (SuiteAuthContext.hasCredentials()) {
            if ("username".equals(field)) {
                return SuiteAuthContext.getUsername();
            }
            if ("password".equals(field)) {
                return SuiteAuthContext.getPassword();
            }
        }
        if (properties == null) {
            throw new IllegalStateException(
                "auth_credentials.properties not found at " + PROPERTIES_PATH.toAbsolutePath()
                    + " -- it is materialized from this workspace's configured test-account secrets; "
                    + "configure APP_USERNAME/APP_PASSWORD/APP_OTP for this workspace/user first.");
        }
        String key = "otp".equals(field) ? "auth.otp.value" : "auth." + field;
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "auth_credentials.properties has no value for \"" + key + "\" -- configure it for this workspace/user first.");
        }
        return value;
    }
}
