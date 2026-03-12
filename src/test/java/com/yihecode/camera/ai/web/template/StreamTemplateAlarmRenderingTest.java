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

    @Test
    void streamIndexTj_shouldSupportAlarmDetailDrawerPreview() throws IOException {
        String content;
        try (InputStream inputStream = new ClassPathResource("templates/stream/index_tj.ftl").getInputStream()) {
            content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(content.contains("openAlarmDetailById"), "index_tj should support alarm detail click handler");
        assertTrue(content.contains("openAlarmDetailByData"), "index_tj should support drawer preview render");
        assertTrue(content.contains("alarm-detail-drawer"), "index_tj should contain drawer detail style/container");
    }

    @Test
    void streamIndexTj_shouldSupportThemeAndLanguageGlobalSwitch() throws IOException {
        String content;
        try (InputStream inputStream = new ClassPathResource("templates/stream/index_tj.ftl").getInputStream()) {
            content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(content.contains("id=\"theme-switch\""), "index_tj should contain global theme switch");
        assertTrue(content.contains("id=\"lang-switch\""), "index_tj should contain global language switch");
        assertTrue(content.contains("window.applyTheme"), "index_tj should expose theme apply function");
        assertTrue(content.contains("window.applyLanguage"), "index_tj should expose language apply function");
        assertTrue(content.contains("stream.dashboard.theme"), "index_tj should persist selected theme");
        assertTrue(content.contains("stream.dashboard.lang"), "index_tj should persist selected language");
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
