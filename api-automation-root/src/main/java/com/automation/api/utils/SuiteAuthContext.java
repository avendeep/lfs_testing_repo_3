package com.automation.api.utils;

/**
 * JVM-level (NOT per-scenario like ApiContext) holder for the throwaway real account
 * RegisterOnceHook self-registers at most once per suite run. Never written to disk, never
 * committed -- lives only in memory for the lifetime of this JVM. AuthCredentialsResolver
 * checks here first for "username"/"password" before falling back to
 * auth_credentials.properties, so every existing "{{auth.username}}"/"{{auth.password}}"
 * token keeps working unchanged regardless of which source actually backs it. Both fields stay
 * null (hasCredentials() false) whenever RegisterOnceHook found no register endpoint configured
 * for this workspace, or the register call itself failed -- in either case callers fall back to
 * auth_credentials.properties exactly as they did before this class existed.
 */
public class SuiteAuthContext {
    private static volatile String username;
    private static volatile String password;

    private SuiteAuthContext() {
    }

    public static void set(String username, String password) {
        SuiteAuthContext.username = username;
        SuiteAuthContext.password = password;
    }

    public static boolean hasCredentials() {
        return username != null && password != null;
    }

    public static String getUsername() {
        return username;
    }

    public static String getPassword() {
        return password;
    }
}
