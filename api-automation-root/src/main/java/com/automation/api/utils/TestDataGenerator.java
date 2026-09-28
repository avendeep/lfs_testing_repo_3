package com.automation.api.utils;

import net.datafaker.Faker;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class TestDataGenerator {
    private static final Faker faker = new Faker();
    private static final DateTimeFormatter ISO_MILLIS = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    public static String randomEmail() {
        return faker.internet().emailAddress();
    }

    public static Object generateValueForField(String key, Object defaultValue, String strategy) {
        String keyLower = key.toLowerCase();
        boolean isBoolean = defaultValue instanceof Boolean
                || (defaultValue instanceof String && "boolean".equalsIgnoreCase((String) defaultValue));
        boolean isNumeric = false;
        if (defaultValue instanceof Number) {
            isNumeric = true;
        } else if (defaultValue instanceof String) {
            if ("number".equalsIgnoreCase((String) defaultValue)) {
                isNumeric = true;
            } else {
                try {
                    Double.parseDouble((String) defaultValue);
                    isNumeric = true;
                } catch (NumberFormatException e) {
                }
            }
        }
        // Key-name fallback: a template sourced from api-payloads.json (see PayloadResolver)
        // never carries a real type -- every leaf's defaultValue is a blank "" string -- so a
        // genuinely numeric field (id, pageCount, amount, ...) would otherwise always fall
        // through to the lorem-word/empty-string branches below. Same heuristic style as the
        // email/password/user/date checks already here, only reached when defaultValue itself
        // gave no type signal.
        if (!isNumeric && !isBoolean
                && (keyLower.equals("id") || keyLower.endsWith("id") || keyLower.endsWith("count")
                        || keyLower.contains("quantity") || keyLower.contains("amount")
                        || keyLower.contains("number") || keyLower.contains("age"))) {
            isNumeric = true;
        }
        // Same key-name fallback as above, for boolean fields: a template sourced from
        // api-payloads.json never carries a real type (every leaf's defaultValue is a blank
        // "" string), so a genuinely boolean field (is_active, enabled, ...) would otherwise
        // fall through to the lorem-word branch below and fail server-side bool_parsing
        // validation instead of exercising the case the scenario actually intends to test.
        if (!isNumeric && !isBoolean
                && (keyLower.startsWith("is_") || keyLower.startsWith("has_")
                        || keyLower.endsWith("_active") || keyLower.equals("active")
                        || keyLower.contains("enabled") || keyLower.contains("disabled")
                        || keyLower.contains("flag"))) {
            isBoolean = true;
        }

        if ("positive".equalsIgnoreCase(strategy)) {
            if (keyLower.contains("email")) {
                return faker.internet().emailAddress();
            } else if (keyLower.contains("password")) {
                return "Pass@" + faker.internet().password(8, 12, true, true, true);
            } else if (keyLower.contains("user") || keyLower.contains("name")) {
                return faker.name().username();
            } else if (keyLower.contains("date")) {
                return ISO_MILLIS.format(Instant.now());
            } else if (isBoolean) {
                return faker.bool().bool();
            } else if (isNumeric) {
                return faker.number().numberBetween(1, 100);
            } else {
                return faker.lorem().word();
            }
        } else if ("negative".equalsIgnoreCase(strategy)) {
            if (keyLower.contains("email")) {
                return "invalid-email";
            } else if (keyLower.contains("password")) {
                return ""; // empty password
            } else if (isNumeric) {
                return -999;
            } else {
                return "";
            }
        } else if ("boundary".equalsIgnoreCase(strategy)) {
            if (isNumeric) {
                return 0;
            } else {
                return "A"; // short boundary
            }
        } else if ("security".equalsIgnoreCase(strategy)) {
            if (isNumeric) {
                return 9999999;
            } else {
                return "' OR '1'='1"; // SQL injection payload
            }
        }
        return defaultValue;
    }
}
