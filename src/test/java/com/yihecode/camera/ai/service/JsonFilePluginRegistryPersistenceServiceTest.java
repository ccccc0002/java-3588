package com.yihecode.camera.ai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JsonFilePluginRegistryPersistenceServiceTest {

    @Mock
    private ConfigService configService;

    @InjectMocks
    private JsonFilePluginRegistryPersistenceService persistenceService;

    @TempDir
    Path tempDir;

    @Test
    void saveAllAndLoadAll_shouldRoundTripRecords() throws Exception {
        Path file = tempDir.resolve("plugin-registry.json");
        when(configService.getByValTag("plugin_registry_file")).thenReturn(file.toString());

        PluginRegistryRecord first = new PluginRegistryRecord();
        first.setRegistrationId("face-detector:1.0.0");
        first.setPluginId("face-detector");
        first.setVersion("1.0.0");

        PluginRegistryRecord second = new PluginRegistryRecord();
        second.setRegistrationId("helmet-detector:1.0.0");
        second.setPluginId("helmet-detector");
        second.setVersion("1.0.0");

        persistenceService.saveAll(Arrays.asList(first, second));
        List<PluginRegistryRecord> loaded = persistenceService.loadAll();

        assertTrue(Files.exists(file));
        assertEquals(2, loaded.size());
        assertEquals("face-detector:1.0.0", loaded.get(0).getRegistrationId());
        assertEquals("helmet-detector:1.0.0", loaded.get(1).getRegistrationId());
    }

    @Test
    void loadAll_shouldReturnEmptyWhenFileMissing() {
        Path file = tempDir.resolve("missing.json");
        when(configService.getByValTag("plugin_registry_file")).thenReturn(file.toString());

        List<PluginRegistryRecord> loaded = persistenceService.loadAll();

        assertTrue(loaded.isEmpty());
    }

    @Test
    void saveAll_shouldCreateParentDirectories() {
        Path file = tempDir.resolve("nested").resolve("plugin-registry.json");
        when(configService.getByValTag("plugin_registry_file")).thenReturn(file.toString());

        PluginRegistryRecord record = new PluginRegistryRecord();
        record.setRegistrationId("face-detector:1.0.0");
        persistenceService.saveAll(Arrays.asList(record));

        assertTrue(Files.exists(file));
        assertFalse(Files.isDirectory(file));
    }
}
