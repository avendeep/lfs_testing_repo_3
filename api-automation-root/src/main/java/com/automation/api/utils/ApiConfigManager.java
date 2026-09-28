package com.automation.api.utils;

public class ApiConfigManager {
    public static String getBaseUri() {
        return EnvironmentResolver.getBaseUrl();
    }
}
