package com.yihecode.camera.ai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InMemoryPluginRegistryServiceTest {

    private final InMemoryPluginRegistryService pluginRegistryService = new InMemoryPluginRegistryService();

    @Test
    void saveAndFind_shouldReturnCopiedRecord() {
        PluginRegistryRecord record = new PluginRegistryRecord();
        record.setRegistrationId("face-detector:1.0.0");
        record.setPluginId("face-detector");
        record.setVersion("1.0.0");
        record.setCapabilities(Arrays.asList("inference"));
        record.setUpdatedAtMs(100L);

        pluginRegistryService.save(record);
        Optional<PluginRegistryRecord> loaded = pluginRegistryService.findByRegistrationId("face-detector:1.0.0");

        assertTrue(loaded.isPresent());
        assertEquals("face-detector", loaded.get().getPluginId());
        assertNotSame(record, loaded.get());
    }

    @Test
    void list_shouldReturnRecordsSortedByUpdatedAtDesc() {
        PluginRegistryRecord first = new PluginRegistryRecord();
        first.setRegistrationId("a:1.0.0");
        first.setUpdatedAtMs(100L);

        PluginRegistryRecord second = new PluginRegistryRecord();
        second.setRegistrationId("b:1.0.0");
        second.setUpdatedAtMs(200L);

        pluginRegistryService.save(first);
        pluginRegistryService.save(second);

        List<PluginRegistryRecord> records = pluginRegistryService.list();

        assertEquals(2, records.size());
        assertEquals("b:1.0.0", records.get(0).getRegistrationId());
        assertEquals("a:1.0.0", records.get(1).getRegistrationId());
    }

    @Test
    void delete_shouldRemoveRecord() {
        PluginRegistryRecord record = new PluginRegistryRecord();
        record.setRegistrationId("face-detector:1.0.0");
        pluginRegistryService.save(record);

        boolean removed = pluginRegistryService.delete("face-detector:1.0.0");

        assertTrue(removed);
        assertFalse(pluginRegistryService.findByRegistrationId("face-detector:1.0.0").isPresent());
    }

    @Test
    void save_shouldPersistSnapshotWhenPersistenceConfigured() {
        PluginRegistryPersistenceService persistenceService = mock(PluginRegistryPersistenceService.class);
        ReflectionTestUtils.setField(pluginRegistryService, "pluginRegistryPersistenceService", persistenceService);

        PluginRegistryRecord record = new PluginRegistryRecord();
        record.setRegistrationId("face-detector:1.0.0");

        pluginRegistryService.save(record);

        verify(persistenceService).saveAll(anyList());
    }

    @Test
    void list_shouldLoadPersistedSnapshotOnFirstAccess() {
        InMemoryPluginRegistryService service = new InMemoryPluginRegistryService();
        PluginRegistryPersistenceService persistenceService = mock(PluginRegistryPersistenceService.class);
        ReflectionTestUtils.setField(service, "pluginRegistryPersistenceService", persistenceService);

        PluginRegistryRecord record = new PluginRegistryRecord();
        record.setRegistrationId("face-detector:1.0.0");
        when(persistenceService.loadAll()).thenReturn(Arrays.asList(record));

        List<PluginRegistryRecord> records = service.list();

        assertEquals(1, records.size());
        assertEquals("face-detector:1.0.0", records.get(0).getRegistrationId());
    }
}
