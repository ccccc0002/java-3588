package com.yihecode.camera.ai.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreemarkerSettingsResourceTest {

    @Test
    void applicationYamlShouldDeclareFtlSuffix() throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            assertNotNull(stream, "application.yml should exist on the classpath");
            String yaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yaml.contains("suffix: .ftl"));
            assertTrue(yaml.contains("template-loader-path: classpath:/templates/"));
        }
    }
}