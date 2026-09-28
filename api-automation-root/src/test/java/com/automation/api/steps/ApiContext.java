package com.automation.api.steps;

import com.automation.api.utils.AuthCredentialsResolver;
import com.automation.api.utils.RandomTokenResolver;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Scenario-scoped shared state for multi-endpoint "journey" tests (e.g. register -> login ->
// view profile -> change password), where a later step needs a value (userId, token, ...)
// extracted from an earlier step's response. PicoContainer creates exactly one instance of
// this per scenario and injects the SAME instance into every step-definition class that
// declares it in its constructor -- state naturally resets between scenarios since a fresh
// instance is created each time, no manual cleanup needed.
//
// This is deliberately separate from TestDataGenerator: ApiContext carries values WITHIN one
// run of a journey (the userId this specific run just created); TestDataGenerator carries
// freshness ACROSS runs (a new random email every time the journey runs). Neither replaces
// the other.
public class ApiContext {
    private final Map<String, Object> store = new HashMap<>();

    public void save(String name, Object value) {
        store.put(name, value);
    }

    public Object get(String name) {
        if (!store.containsKey(name)) {
            throw new IllegalStateException(
                "No value saved as \"" + name + "\" -- did an earlier step run \"I save ... as \"" + name + "\"\"?"
            );
        }
        return store.get(name);
    }

    public String getString(String name) {
        Object value = get(name);
        return value == null ? null : value.toString();
    }

    public boolean has(String name) {
        return store.containsKey(name);
    }

    // Used by ApiSteps to drop "requestBody" right after each request is sent -- a journey
    // scenario sends multiple requests through the SAME ApiContext instance, so leaving a
    // built body in the store would make every later "When I send" in that scenario silently
    // reuse the first request's body instead of building its own.
    public void clear(String name) {
        store.remove(name);
    }

    // "{{auth.username}}"/"{{auth.password}}"/"{{auth.otp}}" -- a real, pre-existing test
    // account's credentials, resolved from auth_credentials.properties (never a literal in
    // any git-committed api-payload.json/testdata/<slug>.json -- see AuthCredentialsResolver).
    private static final Pattern AUTH_TOKEN = Pattern.compile("\\{\\{auth\\.([a-zA-Z0-9_]+)\\}\\}");

    // Substitutes every {{name}} placeholder in the given text with its saved value, then
    // every {{random.field}} placeholder with a FRESH Faker-generated value for that field
    // name (see SwaggerParser.defaultStringValue -- generated feature files carry these
    // tokens instead of a literal value baked in once at generation time, so a "register"-
    // style endpoint gets a genuinely new email/username every run instead of colliding with
    // its own previous run's "already registered" data). Used on request payloads and URLs
    // so step definitions don't do this string-splicing themselves.
    public String resolvePlaceholders(String text) {
        if (text == null) return null;
        String resolved = text;
        for (Map.Entry<String, Object> entry : store.entrySet()) {
            resolved = resolved.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        resolved = RandomTokenResolver.resolve(resolved);
        return resolveAuthTokens(resolved);
    }

    // Same fresh-per-occurrence approach as resolveRandomTokens, but every occurrence of a
    // given "{{auth.field}}" resolves to the SAME real value (unlike random tokens, a real
    // account's email/password must stay consistent within and across scenarios).
    private static String resolveAuthTokens(String text) {
        Matcher matcher = AUTH_TOKEN.matcher(text);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            result.append(text, last, matcher.start());
            result.append(AuthCredentialsResolver.get(matcher.group(1)));
            last = matcher.end();
        }
        result.append(text.substring(last));
        return result.toString();
    }

}
