package com.automation.api.utils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class EnvironmentResolver {
    // Relative file path, not a classpath resource -- this file lives under
    // src/test/resources, so a classpath lookup only ever found it when running under
    // "mvn test". A relative path resolves correctly as long as the working directory is
    // api-automation-root/ (true for both an IDE run and a Maven run).
    private static final Path CONFIG_PATH = Paths.get("src/test/resources/config/environments.properties");
    private static final Properties props = new Properties();
    // "dev" by default, overridable per-run via -Denv=qa/staging/prod without touching
    // environments.properties itself.
    private static final String activeEnv = System.getProperty("env", "dev");

    static {
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream input = Files.newInputStream(CONFIG_PATH)) {
                props.load(input);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static String getBaseUrl() {
        return props.getProperty(activeEnv + ".baseUrl", "http://localhost:8080");
    }
}
