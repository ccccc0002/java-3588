package com.yihecode.camera.ai.service;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class InMemoryPluginRegistryService implements PluginRegistryService {

    private final Map<String, PluginRegistryRecord> registry = new LinkedHashMap<>();
    private final Object lock = new Object();

    @Override
    public void save(PluginRegistryRecord record) {
        if (record == null || StrUtil.isBlank(record.getRegistrationId())) {
            return;
        }
        synchronized (lock) {
            registry.put(record.getRegistrationId(), copy(record));
        }
    }

    @Override
    public Optional<PluginRegistryRecord> findByRegistrationId(String registrationId) {
        if (StrUtil.isBlank(registrationId)) {
            return Optional.empty();
        }
        synchronized (lock) {
            PluginRegistryRecord record = registry.get(registrationId.trim());
            return record == null ? Optional.empty() : Optional.of(copy(record));
        }
    }

    @Override
    public List<PluginRegistryRecord> list() {
        synchronized (lock) {
            List<PluginRegistryRecord> records = new ArrayList<>();
            for (PluginRegistryRecord record : registry.values()) {
                records.add(copy(record));
            }
            records.sort(Comparator.comparing(PluginRegistryRecord::getUpdatedAtMs, Comparator.nullsLast(Comparator.reverseOrder())));
            return records;
        }
    }

    @Override
    public boolean delete(String registrationId) {
        if (StrUtil.isBlank(registrationId)) {
            return false;
        }
        synchronized (lock) {
            return registry.remove(registrationId.trim()) != null;
        }
    }

    private PluginRegistryRecord copy(PluginRegistryRecord source) {
        PluginRegistryRecord target = new PluginRegistryRecord();
        target.setRegistrationId(source.getRegistrationId());
        target.setPluginId(source.getPluginId());
        target.setVersion(source.getVersion());
        target.setRuntime(source.getRuntime());
        target.setCapabilities(source.getCapabilities() == null ? new ArrayList<>() : new ArrayList<>(source.getCapabilities()));
        target.setHealthUrl(source.getHealthUrl());
        target.setHealthy(source.getHealthy());
        target.setStatus(source.getStatus());
        target.setStorageMode(source.getStorageMode());
        target.setTraceId(source.getTraceId());
        target.setUpdatedAtMs(source.getUpdatedAtMs());
        return target;
    }
}
