package com.yihecode.camera.ai.service;

import java.util.List;
import java.util.Optional;

public interface PluginRegistryService {

    void save(PluginRegistryRecord record);

    Optional<PluginRegistryRecord> findByRegistrationId(String registrationId);

    List<PluginRegistryRecord> list();

    boolean delete(String registrationId);
}
