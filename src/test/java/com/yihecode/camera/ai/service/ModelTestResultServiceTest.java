package com.yihecode.camera.ai.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelTestResultServiceTest {

    private final ModelTestResultService modelTestResultService = new ModelTestResultService();

    @BeforeAll
    static void setupHeadlessMode() {
        System.setProperty("java.awt.headless", "true");
    }

    @TempDir
    Path tempDir;

    @Test
    void flattenDetections_shouldFlattenNestedAndDirectPayload() {
        JSONArray payload = new JSONArray();

        JSONObject root = new JSONObject();
        root.put("algorithm_id", 1L);
        root.put("algorithm_name", "helmet");
        JSONArray data = new JSONArray();
        JSONObject child = new JSONObject();
        child.put("type", "nohelmet");
        child.put("confidence", 0.91);
        child.put("position", List.of(10, 20, 40, 80));
        data.add(child);
        root.put("data", data);
        payload.add(root);

        JSONObject direct = new JSONObject();
        direct.put("label", "person");
        direct.put("score", 0.83);
        direct.put("bbox", List.of(5, 6, 15, 20));
        payload.add(direct);

        List<Map<String, Object>> detections = modelTestResultService.flattenDetections(payload);

        assertEquals(2, detections.size());
        assertEquals("nohelmet", detections.get(0).get("type"));
        assertEquals(1L, ((Number) detections.get(0).get("algorithm_id")).longValue());
        assertEquals("helmet", detections.get(0).get("algorithm_name"));
        assertEquals("person", detections.get(1).get("type"));
        assertNotNull(detections.get(1).get("position"));
    }

    @Test
    void saveAnnotatedImage_shouldWriteAnnotatedFileUsingRelativePath() throws Exception {
        Path root = tempDir;
        Path sourceRelative = Path.of("2026", "0311", "sample.jpg");
        Path sourceAbsolute = root.resolve(sourceRelative);
        File sourceDir = sourceAbsolute.getParent().toFile();
        assertTrue(sourceDir.mkdirs() || sourceDir.exists());

        BufferedImage image = new BufferedImage(200, 120, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "jpg", sourceAbsolute.toFile());

        List<Map<String, Object>> detections = List.of(
                Map.of("type", "person", "confidence", 0.98, "position", List.of(20, 20, 160, 100))
        );

        String resultRelative = modelTestResultService.saveAnnotatedImage(root.toString(), sourceRelative.toString().replace("\\", "/"), detections);

        assertNotNull(resultRelative);
        assertTrue(resultRelative.contains("annotated"));
        assertTrue(root.resolve(resultRelative).toFile().exists());
    }
}
