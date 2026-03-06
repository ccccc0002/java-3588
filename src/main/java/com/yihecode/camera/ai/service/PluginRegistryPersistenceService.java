package com.yihecode.camera.ai.service;

import java.util.List;

public interface PluginRegistryPersistenceService {

    List<PluginRegistryRecord> loadAll();

    void saveAll(List<PluginRegistryRecord> records);
}
