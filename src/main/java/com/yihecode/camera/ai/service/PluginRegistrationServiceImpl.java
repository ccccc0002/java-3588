package com.yihecode.camera.ai.service;

import cn.hutool.core.util.StrUtil;
import com.yihecode.camera.ai.plugin.PluginManifest;
import com.yihecode.camera.ai.plugin.PluginManifestValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PluginRegistrationServiceImpl implements PluginRegistrationService {

    @Autowired
    private PluginHealthProbeService pluginHealthProbeService;

    @Autowired
    private PluginRegistryService pluginRegistryService;

    @Override
    public Map<String, Object> register(String traceId, PluginManifest manifest, String healthUrl) {
        Map<String, Object> data = new LinkedHashMap<>();
        List<String> errors = PluginManifestValidator.validate(manifest);
        String registrationId = buildRegistrationId(manifest);
        data.put("trace_id", traceId);
        data.put("errors", errors);
        data.put("registration_id", registrationId);
        data.put("storage_mode", "memory");

        if (!errors.isEmpty()) {
            data.put("accepted", false);
            data.put("registration_status", "rejected");
            return data;
        }

        Map<String, Object> health = null;
        if (StrUtil.isNotBlank(healthUrl)) {
            health = pluginHealthProbeService.probe(traceId, healthUrl.trim());
            data.put("health", health);
            Object healthy = health.get("healthy");
            if (!(healthy instanceof Boolean) || !((Boolean) healthy)) {
                data.put("accepted", false);
                data.put("registration_status", "health_check_failed");
                return data;
            }
        }

        PluginRegistryRecord record = new PluginRegistryRecord();
        record.setRegistrationId(registrationId);
        record.setPluginId(manifest.getPluginId());
        record.setVersion(manifest.getVersion());
        record.setRuntime(manifest.getRuntime());
        record.setCapabilities(manifest.getCapabilities() == null ? new ArrayList<>() : new ArrayList<>(manifest.getCapabilities()));
        record.setHealthUrl(StrUtil.isBlank(healthUrl) ? null : healthUrl.trim());
        record.setHealthy(health == null ? null : (Boolean) health.get("healthy"));
        record.setStatus(health == null ? "accepted" : String.valueOf(health.get("status")));
        record.setStorageMode("memory");
        record.setTraceId(traceId);
        record.setUpdatedAtMs(System.currentTimeMillis());
        pluginRegistryService.save(record);

        data.put("accepted", true);
        data.put("registration_status", "accepted");
        data.put("plugin", toMap(record));
        return data;
    }

    @Override
    public Map<String, Object> getRegistration(String traceId, String registrationId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("trace_id", traceId);
        data.put("registration_id", registrationId);
        Optional<PluginRegistryRecord> record = pluginRegistryService.findByRegistrationId(registrationId);
        if (record.isPresent()) {
            data.put("found", true);
            data.put("plugin", toMap(record.get()));
        } else {
            data.put("found", false);
            data.put("plugin", null);
        }
        return data;
    }

    @Override
    public Map<String, Object> listRegistrations(String traceId) {
        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, Object>> plugins = new ArrayList<>();
        for (PluginRegistryRecord record : pluginRegistryService.list()) {
            plugins.add(toMap(record));
        }
        data.put("trace_id", traceId);
        data.put("count", plugins.size());
        data.put("plugins", plugins);
        return data;
    }

    private String buildRegistrationId(PluginManifest manifest) {
        if (manifest == null) {
            return null;
        }
        if (StrUtil.isBlank(manifest.getPluginId()) || StrUtil.isBlank(manifest.getVersion())) {
            return null;
        }
        return manifest.getPluginId().trim() + ":" + manifest.getVersion().trim();
    }

    private Map<String, Object> toMap(PluginRegistryRecord record) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("registration_id", record.getRegistrationId());
        data.put("plugin_id", record.getPluginId());
        data.put("version", record.getVersion());
        data.put("runtime", record.getRuntime());
        data.put("capabilities", record.getCapabilities() == null ? new ArrayList<>() : new ArrayList<>(record.getCapabilities()));
        data.put("health_url", record.getHealthUrl());
        data.put("healthy", record.getHealthy());
        data.put("status", record.getStatus());
        data.put("storage_mode", record.getStorageMode());
        data.put("trace_id", record.getTraceId());
        data.put("updated_at_ms", record.getUpdatedAtMs());
        return data;
    }
}
