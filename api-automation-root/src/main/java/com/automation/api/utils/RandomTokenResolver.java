package com.automation.api.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared "{{random.field}}" substitution, extracted so ApiContext (per-scenario, resolves a
 * request body's tokens right before sending) and RegisterOnceHook (suite-level, resolves a
 * register endpoint's base payload once at startup) always generate a fresh value for a given
 * field name the exact same way, via TestDataGenerator/Faker -- one implementation, not two
 * copies that could quietly drift apart.
 */
public class RandomTokenResolver {
    private static final Pattern RANDOM_TOKEN = Pattern.compile("\\{\\{random\\.([a-zA-Z0-9_]+)\\}\\}");

    private RandomTokenResolver() {
    }

    // Regex-based (not a simple .replace()) so each occurrence gets its OWN fresh value --
    // EXCEPT a "confirm password"-style field (confirm_password, passwordConfirmation,
    // retypePassword, ...), which must match whatever the plain "password" field in the SAME
    // call resolved to, or every register/signup-style payload would send a self-contradictory
    // password/confirm-password pair. Coordination state is local to one resolve() call (not a
    // static/instance field), so it never leaks across calls -- callers that resolve a whole
    // payload in one resolve() call (ApiContext.resolvePlaceholders, and RegisterOnceHook, which
    // must therefore resolve its WHOLE serialized payload in one call too, not field-by-field)
    // get the coordination; a caller resolving a single lone field's value in isolation has no
    // "password" field alongside for a confirm-field to match against, same as before.
    public static String resolve(String text) {
        Matcher matcher = RANDOM_TOKEN.matcher(text);
        StringBuilder result = new StringBuilder();
        int last = 0;
        Map<String, String> resolvedThisCall = new HashMap<>();
        while (matcher.find()) {
            result.append(text, last, matcher.start());
            result.append(randomValueFor(matcher.group(1), resolvedThisCall));
            last = matcher.end();
        }
        result.append(text.substring(last));
        return result.toString();
    }

    private static String randomValueFor(String fieldName, Map<String, String> resolvedThisCall) {
        String fieldLower = fieldName.toLowerCase();
        if (fieldLower.contains("password") || fieldLower.contains("passwd") || fieldLower.contains("pwd")) {
            boolean isConfirmVariant = fieldLower.contains("confirm") || fieldLower.contains("repeat")
                || fieldLower.contains("retype") || fieldLower.contains("verify");
            if (isConfirmVariant && resolvedThisCall.containsKey("password")) {
                return resolvedThisCall.get("password");
            }
            Object value = TestDataGenerator.generateValueForField(fieldName, "string", "positive");
            String stringValue = value != null ? value.toString() : fieldName;
            if (!isConfirmVariant) {
                resolvedThisCall.put("password", stringValue);
            }
            return stringValue;
        }
        if (fieldLower.contains("email")) {
            return TestDataGenerator.randomEmail();
        }
        Object value = TestDataGenerator.generateValueForField(fieldName, "string", "positive");
        return value != null ? value.toString() : fieldName;
    }
}
