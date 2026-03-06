package com.yihecode.camera.ai.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
