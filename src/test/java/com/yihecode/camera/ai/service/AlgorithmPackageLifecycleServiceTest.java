package com.yihecode.camera.ai.service;

import com.yihecode.camera.ai.entity.Algorithm;
import com.yihecode.camera.ai.entity.CameraAlgorithm;
import com.yihecode.camera.ai.plugin.PluginManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlgorithmPackageLifecycleServiceTest {

    @Mock
    private AlgorithmService algorithmService;

    @Mock
    private CameraAlgorithmService cameraAlgorithmService;

    @Mock
    private PluginRegistrationService pluginRegistrationService;

    @Mock
    private ConfigService configService;

    @InjectMocks
    private AlgorithmPackageLifecycleService algorithmPackageLifecycleService;

    @TempDir
    Path tempDir;

    @Test
    void importPackage_shouldRejectNonZipUpload() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "algo.txt",
                "text/plain",
                "demo".getBytes(StandardCharsets.UTF_8)
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> algorithmPackageLifecycleService.importPackage(file)
        );
        assertTrue(ex.getMessage().contains(".zip"));
    }

    @Test
    void importPackage_shouldRejectWhenManifestMissing() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("plugin/readme.txt", "missing manifest");
        MockMultipartFile file = zipFile("broken.zip", entries);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> algorithmPackageLifecycleService.importPackage(file)
        );
        assertTrue(ex.getMessage().toLowerCase().contains("manifest.json"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void importPackage_shouldImportAndUpsertAlgorithm() throws Exception {
        when(configService.getByValTag("algorithm_package_dir")).thenReturn(tempDir.toString());
        when(configService.getByValTag("infer_service_url")).thenReturn("http://127.0.0.1:19090");
        when(algorithmService.listNameEn("yolov8n")).thenReturn(Collections.emptyList());
        when(algorithmService.saveOrUpdate(any(Algorithm.class))).thenAnswer(invocation -> {
            Algorithm algorithm = invocation.getArgument(0);
            algorithm.setId(1001L);
            return true;
        });

        Map<String, Object> registrationResult = new LinkedHashMap<>();
        registrationResult.put("accepted", true);
        registrationResult.put("registration_status", "accepted");
        when(pluginRegistrationService.register(anyString(), any(PluginManifest.class), eq("http://127.0.0.1:19090/health")))
                .thenReturn(registrationResult);

        String manifest = "{"
                + "\"plugin_id\":\"yolov8n\","
                + "\"version\":\"1.0.0\","
                + "\"runtime\":\"rk3588_rknn\","
                + "\"capabilities\":[\"inference\",\"alert\"]"
                + "}";
        String config = "{"
                + "\"display_name\":\"YOLOv8n\","
                + "\"obj_threshold\":0.5,"
                + "\"nms_threshold\":0.45,"
                + "\"alert_labels\":[\"person\"],"
                + "\"label_aliases_zh\":{\"person\":\"人员\"}"
                + "}";
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("plugins/yolov8n/manifest.json", manifest);
        entries.put("plugins/yolov8n/config/plugin.json", config);
        entries.put("plugins/yolov8n/model/yolov8.rknn", "rknn-binary-placeholder");

        MockMultipartFile file = zipFile("yolov8n.zip", entries);

        Map<String, Object> data = algorithmPackageLifecycleService.importPackage(file);

        assertEquals("yolov8n", data.get("plugin_id"));
        assertEquals("rk3588_rknn", data.get("runtime"));
        assertEquals(1001L, data.get("algorithm_id"));
        Map<String, Object> registration = (Map<String, Object>) data.get("registration");
        assertEquals(true, registration.get("accepted"));

        ArgumentCaptor<Algorithm> algorithmCaptor = ArgumentCaptor.forClass(Algorithm.class);
        verify(algorithmService).saveOrUpdate(algorithmCaptor.capture());
        Algorithm saved = algorithmCaptor.getValue();
        assertEquals("yolov8n", saved.getNameEn());
        assertEquals("YOLOv8n", saved.getName());
        assertNotNull(saved.getParams());
        assertTrue(saved.getParams().contains("\"obj_threshold\":0.5"));
        assertTrue(saved.getParams().contains("\"alert_labels\":[\"person\"]"));

        Path copiedManifest = tempDir.resolve("yolov8n").resolve("manifest.json");
        assertTrue(Files.exists(copiedManifest));
    }

    @Test
    void forceDelete_shouldUnbindThenRemoveAlgorithm() {
        Algorithm algorithm = new Algorithm();
        algorithm.setId(8L);
        algorithm.setName("demo");
        algorithm.setNameEn("demo_algo");
        when(algorithmService.getById(8L)).thenReturn(algorithm);
        when(cameraAlgorithmService.listByAlgorithm(8L)).thenReturn(Arrays.asList(new CameraAlgorithm(), new CameraAlgorithm()));
        when(cameraAlgorithmService.remove(any())).thenReturn(true);
        when(algorithmService.removeById(8L)).thenReturn(true);

        Map<String, Object> data = algorithmPackageLifecycleService.forceDelete(8L);

        assertEquals(8L, data.get("algorithm_id"));
        assertEquals(2, data.get("unbind_count"));
        assertEquals(true, data.get("removed"));
        verify(cameraAlgorithmService).remove(any());
        verify(algorithmService).removeById(8L);
    }

    @Test
    void updateMetadata_shouldUpdateNameDescriptionAndLabelAliases() {
        Algorithm algorithm = new Algorithm();
        algorithm.setId(11L);
        algorithm.setName("old-name");
        algorithm.setNameEn("yolov8n");
        algorithm.setParams("{\"description\":\"old\",\"label_aliases_zh\":{\"person\":\"人\"}}");
        when(algorithmService.getById(11L)).thenReturn(algorithm);
        when(algorithmService.saveOrUpdate(any(Algorithm.class))).thenReturn(true);

        Map<String, Object> data = algorithmPackageLifecycleService.updateMetadata(
                11L,
                "new-name",
                "new description",
                "{\"person\":\"人员\",\"helmet\":\"安全帽\"}"
        );

        assertEquals(11L, data.get("algorithm_id"));
        assertEquals("new-name", data.get("algorithm_name"));
        assertEquals("new description", data.get("description"));
        assertTrue(algorithm.getParams().contains("\"description\":\"new description\""));
        assertTrue(algorithm.getParams().contains("\"label_aliases_zh\""));
        verify(algorithmService).saveOrUpdate(algorithm);
    }

    @Test
    void updateMetadata_shouldRejectInvalidLabelAliasesJson() {
        Algorithm algorithm = new Algorithm();
        algorithm.setId(12L);
        algorithm.setName("old");
        algorithm.setParams("{}");
        when(algorithmService.getById(12L)).thenReturn(algorithm);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> algorithmPackageLifecycleService.updateMetadata(12L, null, null, "[1,2,3]")
        );

        assertTrue(ex.getMessage().toLowerCase().contains("json object"));
    }

    private MockMultipartFile zipFile(String fileName, Map<String, String> entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                byte[] payload = entry.getValue().getBytes(StandardCharsets.UTF_8);
                zip.write(payload);
                zip.closeEntry();
            }
        }
        return new MockMultipartFile("file", fileName, "application/zip", output.toByteArray());
    }
}
