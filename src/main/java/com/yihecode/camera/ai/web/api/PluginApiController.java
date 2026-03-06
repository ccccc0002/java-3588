package com.yihecode.camera.ai.web.api;

import cn.hutool.core.util.StrUtil;
import com.yihecode.camera.ai.plugin.PluginManifest;
import com.yihecode.camera.ai.plugin.PluginManifestValidator;
import com.yihecode.camera.ai.service.PluginHealthProbeService;
import com.yihecode.camera.ai.service.PluginRegistrationService;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Controller
@RequestMapping({"/api/plugin"})
public class PluginApiController {

    private static final String PLUGIN_MANIFEST_SCHEMA_VERSION = "plugin-manifest.v1";

    @Autowired(required = false)
    private PluginRegistrationService pluginRegistrationService;

    @Autowired(required = false)
    private PluginHealthProbeService pluginHealthProbeService;

    @RequestMapping(value = {"/manifest/validate"}, method = {RequestMethod.POST})
    @ResponseBody
    public JsonResult validateManifest(@RequestBody(required = false) Map<String, Object> body) {
        PluginManifest manifest = toManifest(body);
        List<String> errors = PluginManifestValidator.validate(manifest);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("trace_id", UUID.randomUUID().toString());
        data.put("schema_version", PLUGIN_MANIFEST_SCHEMA_VERSION);
        data.put("valid", errors.isEmpty());
        data.put("errors", errors);
        data.put("normalized_manifest", toMap(manifest));
        return JsonResultUtils.success(data);
    }

    @RequestMapping(value = {"/health/probe"}, method = {RequestMethod.POST})
    @ResponseBody
    public JsonResult healthProbe(@RequestBody(required = false) Map<String, Object> body) {
        String traceId = UUID.randomUUID().toString();
        String healthUrl = trimToNull(firstString(body, "health_url", "healthUrl"));
        if (pluginHealthProbeService == null) {
            return JsonResultUtils.fail("plugin health probe service is unavailable", Map.of("trace_id", traceId));
        }
        return JsonResultUtils.success(pluginHealthProbeService.probe(traceId, healthUrl));
    }

    @RequestMapping(value = {"/register"}, method = {RequestMethod.POST})
    @ResponseBody
    public JsonResult register(@RequestBody(required = false) Map<String, Object> body) {
        String traceId = UUID.randomUUID().toString();
        PluginManifest manifest = toManifest(body);
        String healthUrl = trimToNull(firstString(body, "health_url", "healthUrl"));
        if (pluginRegistrationService == null) {
            return JsonResultUtils.fail("plugin registration service is unavailable", Map.of("trace_id", traceId));
        }
        Map<String, Object> data = new LinkedHashMap<>(pluginRegistrationService.register(traceId, manifest, healthUrl));
        data.put("schema_version", PLUGIN_MANIFEST_SCHEMA_VERSION);
        data.put("normalized_manifest", toMap(manifest));
        return JsonResultUtils.success(data);
    }

    @RequestMapping(value = {"/list"}, method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public JsonResult list(@RequestParam(value = "plugin_id", required = false) String pluginId,
                           @RequestParam(value = "runtime", required = false) String runtime,
                           @RequestParam(value = "status", required = false) String status,
                           @RequestParam(value = "healthy", required = false) Boolean healthy,
                           @RequestParam(value = "dispatch_ready", required = false) Boolean dispatchReady,
                           @RequestParam(value = "capability", required = false) String capability,
                           @RequestParam(value = "offset", required = false, defaultValue = "0") Integer offset,
                           @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {
        String traceId = UUID.randomUUID().toString();
        if (pluginRegistrationService == null) {
            return JsonResultUtils.fail("plugin registration service is unavailable", Map.of("trace_id", traceId));
        }
        Map<String, Object> data = new LinkedHashMap<>(pluginRegistrationService.listRegistrations(traceId,
                trimToNull(pluginId),
                trimToNull(runtime),
                trimToNull(status),
                healthy,
                dispatchReady,
                trimToNull(capability),
                offset,
                limit));
        data.put("schema_version", PLUGIN_MANIFEST_SCHEMA_VERSION);
        return JsonResultUtils.success(data);
    }

    @RequestMapping(value = {"/detail"}, method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public JsonResult detail(@RequestParam(value = "registration_id", required = false) String registrationId) {
        String traceId = UUID.randomUUID().toString();
        if (pluginRegistrationService == null) {
            return JsonResultUtils.fail("plugin registration service is unavailable", Map.of("trace_id", traceId));
        }
        Map<String, Object> data = new LinkedHashMap<>(pluginRegistrationService.getRegistration(traceId, trimToNull(registrationId)));
        data.put("schema_version", PLUGIN_MANIFEST_SCHEMA_VERSION);
        return JsonResultUtils.success(data);
    }


    @RequestMapping(value = {"/stats"}, method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public JsonResult stats() {
        String traceId = UUID.randomUUID().toString();
        if (pluginRegistrationService == null) {
            return JsonResultUtils.fail("plugin registration service is unavailable", Map.of("trace_id", traceId));
        }
        Map<String, Object> data = new LinkedHashMap<>(pluginRegistrationService.stats(traceId));
        data.put("schema_version", PLUGIN_MANIFEST_SCHEMA_VERSION);
        return JsonResultUtils.success(data);
    }

    @RequestMapping(value = {"/refresh"}, method = {RequestMethod.POST})
    @ResponseBody
    public JsonResult refresh(@RequestParam(value = "registration_id", required = false) String registrationId) {
        String traceId = UUID.randomUUID().toString();
        if (pluginRegistrationService == null) {
            return JsonResultUtils.fail("plugin registration service is unavailable", Map.of("trace_id", traceId));
        }
        Map<String, Object> data = new LinkedHashMap<>(pluginRegistrationService.refreshRegistration(traceId, trimToNull(registrationId)));
        data.put("schema_version", PLUGIN_MANIFEST_SCHEMA_VERSION);
        return JsonResultUtils.success(data);
    }


    @RequestMapping(value = {"/refresh/batch"}, method = {RequestMethod.POST})
    @ResponseBody
    public JsonResult refreshBatch(@RequestBody(required = false) Map<String, Object> body,
                                   @RequestParam(value = "only_unhealthy", required = false) Boolean onlyUnhealthy,
                                   @RequestParam(value = "limit", required = false) Integer limit) {
        String traceId = UUID.randomUUID().toString();
        if (pluginRegistrationService == null) {
            return JsonResultUtils.fail("plugin registration service is unavailable", Map.of("trace_id", traceId));
        }
        List<String> registrationIds = normalizeStringList(body == null ? null : body.get("registration_ids"));
        boolean effectiveOnlyUnhealthy = toBooleanFlag(body == null ? null : body.get("only_unhealthy"), Boolean.TRUE.equals(onlyUnhealthy));
        Integer effectiveLimit = resolveInteger(body == null ? null : body.get("limit"), limit);
        Map<String, Object> data = new LinkedHashMap<>(pluginRegistrationService.refreshRegistrations(traceId,
                registrationIds,
                effectiveOnlyUnhealthy,
                effectiveLimit));
        data.put("schema_version", PLUGIN_MANIFEST_SCHEMA_VERSION);
        return JsonResultUtils.success(data);
    }

    @RequestMapping(value = {"/unregister"}, method = {RequestMethod.POST})
    @ResponseBody
    public JsonResult unregister(@RequestParam(value = "registration_id", required = false) String registrationId) {
        String traceId = UUID.randomUUID().toString();
        if (pluginRegistrationService == null) {
            return JsonResultUtils.fail("plugin registration service is unavailable", Map.of("trace_id", traceId));
        }
        Map<String, Object> data = new LinkedHashMap<>(pluginRegistrationService.unregisterRegistration(traceId, trimToNull(registrationId)));
        data.put("schema_version", PLUGIN_MANIFEST_SCHEMA_VERSION);
        return JsonResultUtils.success(data);
    }

    private PluginManifest toManifest(Map<String, Object> body) {
        PluginManifest manifest = new PluginManifest();
        if (body == null || body.isEmpty()) {
            manifest.setCapabilities(Collections.emptyList());
            return manifest;
        }

        manifest.setPluginId(trimToNull(firstString(body, "plugin_id", "pluginId")));
        manifest.setVersion(trimToNull(firstString(body, "version")));
        manifest.setRuntime(trimToNull(firstString(body, "runtime")));
        manifest.setCapabilities(normalizeCapabilities(body.get("capabilities")));
        manifest.setInferUrl(trimToNull(firstString(body, "infer_url", "inferUrl")));
        return manifest;
    }

    private Map<String, Object> toMap(PluginManifest manifest) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("plugin_id", trimToNull(manifest.getPluginId()));
        data.put("version", trimToNull(manifest.getVersion()));
        data.put("runtime", trimToNull(manifest.getRuntime()));
        data.put("capabilities", normalizeCapabilities(manifest.getCapabilities()));
        data.put("infer_url", trimToNull(manifest.getInferUrl()));
        return data;
    }

    private String firstString(Map<String, Object> body, String... keys) {
        if (body == null) {
            return null;
        }
        for (String key : keys) {
            Object value = body.get(key);
            if (value instanceof String) {
                return (String) value;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private List<String> normalizeCapabilities(Object value) {
        if (!(value instanceof List<?>)) {
            return Collections.emptyList();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (Object item : (List<?>) value) {
            if (item == null) {
                continue;
            }
            String capability = item.toString();
            if (StrUtil.isNotBlank(capability)) {
                normalized.add(capability.trim());
            }
        }
        return new ArrayList<>(normalized);
    }

    private List<String> normalizeStringList(Object value) {
        if (!(value instanceof List<?>)) {
            return Collections.emptyList();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (Object item : (List<?>) value) {
            if (item == null) {
                continue;
            }
            String text = item.toString();
            if (StrUtil.isNotBlank(text)) {
                normalized.add(text.trim());
            }
        }
        return new ArrayList<>(normalized);
    }

    private boolean toBooleanFlag(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = value.toString().trim();
        if ("1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text)) {
            return true;
        }
        if ("0".equals(text) || "false".equalsIgnoreCase(text) || "no".equalsIgnoreCase(text)) {
            return false;
        }
        return defaultValue;
    }

    private Integer resolveInteger(Object bodyValue, Integer queryValue) {
        if (bodyValue instanceof Number) {
            return ((Number) bodyValue).intValue();
        }
        if (bodyValue instanceof String && StrUtil.isNotBlank((String) bodyValue)) {
            try {
                return Integer.parseInt(((String) bodyValue).trim());
            } catch (Exception ignored) {
                return queryValue;
            }
        }
        return queryValue;
    }
}
