package com.yihecode.camera.ai.service;

import cn.hutool.core.util.StrUtil;
import com.yihecode.camera.ai.plugin.PluginManifest;
import com.yihecode.camera.ai.plugin.PluginManifestValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class PluginRegistrationServiceImpl implements PluginRegistrationService {

    private static final int DEFAULT_LIST_LIMIT = 100;
    private static final int MAX_LIST_LIMIT = 500;

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
        record.setStatus(normalizeStatus(health == null ? null : String.valueOf(health.get("status")), record.getHealthy()));
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
    public Map<String, Object> listRegistrations(String traceId,
                                                 String pluginId,
                                                 String runtime,
                                                 String status,
                                                 Boolean healthy,
                                                 Integer offset,
                                                 Integer limit) {
        int resolvedOffset = Math.max(0, offset == null ? 0 : offset);
        int resolvedLimit = resolveLimit(limit);
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (PluginRegistryRecord record : pluginRegistryService.list()) {
            Map<String, Object> mapped = toMap(record);
            if (!matchesFilters(mapped, pluginId, runtime, status, healthy)) {
                continue;
            }
            filtered.add(mapped);
        }

        int total = filtered.size();
        int start = Math.min(resolvedOffset, total);
        int end = Math.min(start + resolvedLimit, total);
        List<Map<String, Object>> window = new ArrayList<>(filtered.subList(start, end));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("trace_id", traceId);
        data.put("total", total);
        data.put("count", window.size());
        data.put("requested_offset", offset == null ? 0 : offset);
        data.put("effective_offset", start);
        data.put("limit", resolvedLimit);
        data.put("has_more", end < total);
        data.put("plugins", window);
        return data;
    }

    @Override
    public Map<String, Object> refreshRegistration(String traceId, String registrationId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("trace_id", traceId);
        data.put("registration_id", registrationId);
        Optional<PluginRegistryRecord> optional = pluginRegistryService.findByRegistrationId(registrationId);
        if (!optional.isPresent()) {
            data.put("found", false);
            data.put("refreshed", false);
            data.put("plugin", null);
            return data;
        }

        PluginRegistryRecord record = optional.get();
        data.put("found", true);
        if (StrUtil.isBlank(record.getHealthUrl())) {
            record.setStatus(normalizeStatus(record.getStatus(), record.getHealthy()));
            data.put("refreshed", false);
            data.put("plugin", toMap(record));
            return data;
        }

        Map<String, Object> health = pluginHealthProbeService.probe(traceId, record.getHealthUrl());
        record.setHealthy((Boolean) health.get("healthy"));
        record.setStatus(normalizeStatus(String.valueOf(health.get("status")), record.getHealthy()));
        record.setTraceId(traceId);
        record.setUpdatedAtMs(System.currentTimeMillis());
        pluginRegistryService.save(record);
        data.put("refreshed", true);
        data.put("health", health);
        data.put("plugin", toMap(record));
        return data;
    }

    @Override
    public Map<String, Object> unregisterRegistration(String traceId, String registrationId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("trace_id", traceId);
        data.put("registration_id", registrationId);
        data.put("removed", pluginRegistryService.delete(registrationId));
        return data;
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIST_LIMIT;
        }
        return Math.min(limit, MAX_LIST_LIMIT);
    }

    private boolean matchesFilters(Map<String, Object> mapped,
                                   String pluginId,
                                   String runtime,
                                   String status,
                                   Boolean healthy) {
        if (!containsIgnoreCase((String) mapped.get("plugin_id"), pluginId)) {
            return false;
        }
        if (!containsIgnoreCase((String) mapped.get("runtime"), runtime)) {
            return false;
        }
        if (StrUtil.isNotBlank(status) && !StrUtil.equalsIgnoreCase((String) mapped.get("status"), status.trim())) {
            return false;
        }
        if (healthy != null && !healthy.equals(mapped.get("healthy"))) {
            return false;
        }
        return true;
    }

    private boolean containsIgnoreCase(String actual, String expectedPart) {
        if (StrUtil.isBlank(expectedPart)) {
            return true;
        }
        if (StrUtil.isBlank(actual)) {
            return false;
        }
        return actual.toLowerCase(Locale.ROOT).contains(expectedPart.trim().toLowerCase(Locale.ROOT));
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
        data.put("status", normalizeStatus(record.getStatus(), record.getHealthy()));
        data.put("storage_mode", record.getStorageMode());
        data.put("trace_id", record.getTraceId());
        data.put("updated_at_ms", record.getUpdatedAtMs());
        return data;
    }

    private String normalizeStatus(String status, Boolean healthy) {
        if (Boolean.TRUE.equals(healthy)) {
            return "healthy";
        }
        String normalized = StrUtil.isBlank(status) ? "" : status.trim().toLowerCase(Locale.ROOT);
        if (Boolean.FALSE.equals(healthy)) {
            if ("degraded".equals(normalized)) {
                return "degraded";
            }
            return "unreachable";
        }
        if ("healthy".equals(normalized)) {
            return "healthy";
        }
        if ("degraded".equals(normalized)) {
            return "degraded";
        }
        if ("disabled".equals(normalized)) {
            return "disabled";
        }
        return "registered";
    }
}
