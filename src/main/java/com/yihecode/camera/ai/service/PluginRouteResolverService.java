package com.yihecode.camera.ai.service;

import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class PluginRouteResolverService {

    private static final String BACKEND_LEGACY = "legacy";
    private static final String BACKEND_RK3588 = "rk3588_rknn";

    @Autowired
    private PluginRegistryService pluginRegistryService;

    public boolean hasSelector(Map<String, Object> body) {
        return !extractSelector(body).isEmpty();
    }

    public Map<String, Object> resolve(Map<String, Object> body) {
        Map<String, Object> selector = extractSelector(body);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requested", !selector.isEmpty());
        data.put("selector", selector);
        data.put("matched", false);
        data.put("available", false);
        data.put("source", "none");
        data.put("reason", "no_plugin_selector");
        data.put("backend_hint", null);
        data.put("plugin", null);

        if (selector.isEmpty()) {
            return data;
        }

        String registrationId = asString(selector.get("registration_id"));
        if (StrUtil.isNotBlank(registrationId)) {
            Optional<PluginRegistryRecord> optional = pluginRegistryService.findByRegistrationId(registrationId);
            if (!optional.isPresent()) {
                data.put("reason", "registration_not_found");
                return data;
            }
            return buildResolvedData(data, optional.get(), "registration_id");
        }

        List<PluginRegistryRecord> matches = new ArrayList<>();
        for (PluginRegistryRecord record : pluginRegistryService.list()) {
            if (record == null) {
                continue;
            }
            if (!matchesSelector(record, selector)) {
                continue;
            }
            matches.add(record);
        }
        if (matches.isEmpty()) {
            data.put("reason", "selector_not_matched");
            return data;
        }

        matches.sort(Comparator
                .comparing(this::availabilityRank).reversed()
                .thenComparing(PluginRegistryRecord::getUpdatedAtMs, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(record -> StrUtil.blankToDefault(record.getRegistrationId(), "")));
        return buildResolvedData(data, matches.get(0), "registry_search");
    }

    private Map<String, Object> buildResolvedData(Map<String, Object> seed, PluginRegistryRecord record, String source) {
        Map<String, Object> data = new LinkedHashMap<>(seed);
        data.put("matched", true);
        data.put("source", source);
        data.put("available", isAvailable(record));
        data.put("reason", isAvailable(record) ? "matched" : "plugin_unavailable");
        data.put("backend_hint", normalizeBackendHint(record == null ? null : record.getRuntime()));
        data.put("plugin", toMap(record));
        return data;
    }

    private boolean matchesSelector(PluginRegistryRecord record, Map<String, Object> selector) {
        String pluginId = asString(selector.get("plugin_id"));
        String runtime = asString(selector.get("runtime"));
        List<String> capabilities = asStringList(selector.get("capabilities"));
        if (StrUtil.isNotBlank(pluginId) && !StrUtil.equalsIgnoreCase(pluginId, record.getPluginId())) {
            return false;
        }
        if (StrUtil.isNotBlank(runtime) && !StrUtil.equalsIgnoreCase(runtime, record.getRuntime())) {
            return false;
        }
        List<String> actualCapabilities = normalizeCapabilities(record == null ? null : record.getCapabilities());
        for (String capability : capabilities) {
            if (!actualCapabilities.contains(capability)) {
                return false;
            }
        }
        return true;
    }

    private int availabilityRank(PluginRegistryRecord record) {
        if (record == null) {
            return -1;
        }
        String status = normalizeStatus(record.getStatus(), record.getHealthy());
        if ("healthy".equals(status)) {
            return 4;
        }
        if ("registered".equals(status)) {
            return 3;
        }
        if ("degraded".equals(status)) {
            return 2;
        }
        if ("disabled".equals(status)) {
            return 1;
        }
        return 0;
    }

    private boolean isAvailable(PluginRegistryRecord record) {
        String status = normalizeStatus(record == null ? null : record.getStatus(), record == null ? null : record.getHealthy());
        return !"disabled".equals(status) && !"unreachable".equals(status);
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
            if ("disabled".equals(normalized)) {
                return "disabled";
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

    private Map<String, Object> extractSelector(Map<String, Object> body) {
        Map<String, Object> payload = body == null ? new HashMap<>() : body;
        Map<String, Object> selector = new LinkedHashMap<>();

        String registrationId = firstNonBlank(
                asString(payload.get("plugin_registration_id")),
                asString(payload.get("registration_id"))
        );
        String pluginId = asString(payload.get("plugin_id"));
        String runtime = firstNonBlank(
                asString(payload.get("plugin_runtime")),
                asString(payload.get("runtime"))
        );
        List<String> capabilities = normalizeCapabilities(firstNonNull(
                payload.get("plugin_capabilities"),
                payload.get("plugin_capability"),
                payload.get("capability")
        ));

        if (StrUtil.isNotBlank(registrationId)) {
            selector.put("registration_id", registrationId);
        }
        if (StrUtil.isNotBlank(pluginId)) {
            selector.put("plugin_id", pluginId);
        }
        if (StrUtil.isNotBlank(runtime)) {
            selector.put("runtime", runtime);
        }
        if (!capabilities.isEmpty()) {
            selector.put("capabilities", capabilities);
        }
        return selector;
    }

    private Map<String, Object> toMap(PluginRegistryRecord record) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (record == null) {
            return data;
        }
        data.put("registration_id", record.getRegistrationId());
        data.put("plugin_id", record.getPluginId());
        data.put("version", record.getVersion());
        data.put("runtime", record.getRuntime());
        data.put("capabilities", normalizeCapabilities(record.getCapabilities()));
        data.put("health_url", record.getHealthUrl());
        data.put("healthy", record.getHealthy());
        data.put("status", normalizeStatus(record.getStatus(), record.getHealthy()));
        data.put("storage_mode", record.getStorageMode());
        data.put("trace_id", record.getTraceId());
        data.put("updated_at_ms", record.getUpdatedAtMs());
        return data;
    }

    private String normalizeBackendHint(String runtime) {
        String value = asString(runtime);
        if (BACKEND_RK3588.equalsIgnoreCase(value)) {
            return BACKEND_RK3588;
        }
        if (BACKEND_LEGACY.equalsIgnoreCase(value)) {
            return BACKEND_LEGACY;
        }
        return null;
    }

    private List<String> normalizeCapabilities(Object value) {
        List<String> capabilities = asStringList(value);
        List<String> normalized = new ArrayList<>();
        for (String capability : capabilities) {
            String item = asString(capability);
            if (StrUtil.isBlank(item)) {
                continue;
            }
            if (!normalized.contains(item)) {
                normalized.add(item);
            }
        }
        return normalized;
    }

    private List<String> asStringList(Object value) {
        List<String> list = new ArrayList<>();
        if (value == null) {
            return list;
        }
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                String text = asString(item);
                if (StrUtil.isNotBlank(text)) {
                    list.add(text);
                }
            }
            return list;
        }
        String text = asString(value);
        if (StrUtil.isBlank(text)) {
            return list;
        }
        for (String item : text.split(",")) {
            String trimmed = asString(item);
            if (StrUtil.isNotBlank(trimmed)) {
                list.add(trimmed);
            }
        }
        return list;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String firstNonBlank(String first, String second) {
        if (StrUtil.isNotBlank(first)) {
            return first;
        }
        return second;
    }

    private Object firstNonNull(Object first, Object second, Object third) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return third;
    }
}