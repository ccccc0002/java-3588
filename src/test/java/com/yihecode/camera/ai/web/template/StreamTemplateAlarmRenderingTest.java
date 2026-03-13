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
    void streamIndexTj_shouldSupportAlarmDetailPopupPreview() throws IOException {
        String content;
        try (InputStream inputStream = new ClassPathResource("templates/stream/index_tj.ftl").getInputStream()) {
            content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(content.contains("renderAlarmDetail"), "index_tj should support alarm detail render function");
        assertTrue(content.contains("window.openAlarmDetail"), "index_tj should expose alarm detail click handler");
        assertTrue(content.contains("layer.open"), "index_tj should open alarm detail by layer popup");
        assertTrue(content.contains("window.addAlarmAlert = function(json) {}"), "index_tj should keep realtime alert popup disabled");
    }

    @Test
    void streamIndexTj_shouldSupportDashboardGridAndCharts() throws IOException {
        String content;
        try (InputStream inputStream = new ClassPathResource("templates/stream/index_tj.ftl").getInputStream()) {
            content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(content.contains("id=\"grid_1\""), "index_tj should contain 1-grid switch");
        assertTrue(content.contains("id=\"grid_4\""), "index_tj should contain 4-grid switch");
        assertTrue(content.contains("id=\"grid_9\""), "index_tj should contain 9-grid switch");
        assertTrue(content.contains("id=\"grid_16\""), "index_tj should contain 16-grid switch");
        assertTrue(content.contains("window.show = function(nextCols)"), "index_tj should provide grid switch logic");
        assertTrue(content.contains("id=\"chart-trend\""), "index_tj should contain trend chart container");
        assertTrue(content.contains("id=\"chart-pie\""), "index_tj should contain pie chart container");
        assertTrue(content.contains("id=\"chart-ranking\""), "index_tj should contain ranking chart container");
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
