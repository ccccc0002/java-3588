package com.yihecode.camera.ai.web.template;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamTemplateAlarmRenderingTest {

    @Test
    void streamTemplates_shouldUseAlertLabelsAndCountForRealtimeAlarmRendering() throws IOException {
        assertTemplateSupportsAlertFields("templates/stream/index.ftl");
        assertTemplateSupportsAlertFields("templates/stream/index_tj.ftl");
        assertTemplateSupportsAlertFields("templates/stream/index_mas.ftl");
        assertTemplateSupportsAlertFields("templates/stream/index426.ftl");
    }

    private void assertTemplateSupportsAlertFields(String path) throws IOException {
        String content;
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(content.contains("window.resolveAlarmType"), () -> path + " should resolve alarm display text");
        assertTrue(content.contains("window.buildAlarmTemplateData"), () -> path + " should build shared alarm template payload");
        assertTrue(content.contains("alertLabelsZh"), () -> path + " should prefer zh alert labels");
        assertTrue(content.contains("alertCount"), () -> path + " should expose alert count in rendered title");
    }
}