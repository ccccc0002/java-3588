package com.yihecode.camera.ai.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yihecode.camera.ai.entity.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportPushTargetService {

    private static final String TARGETS_TAG = "reportPushTargets";
    private static final String LEGACY_URL_TAG = "reportPushUrl";
    private static final String LEGACY_IMAGE_TAG = "reportPushImage";

    @Autowired
    private ConfigService configService;

    public List<Map<String, Object>> listAllTargets() {
        String rawTargets = trim(configService.getByValTag(TARGETS_TAG));
        boolean defaultIncludeImage = "true".equalsIgnoreCase(trim(configService.getByValTag(LEGACY_IMAGE_TAG)));
        List<Map<String, Object>> normalized = normalizeTargets(rawTargets, defaultIncludeImage);
        if (!normalized.isEmpty()) {
            return normalized;
        }

        String legacyUrl = trim(configService.getByValTag(LEGACY_URL_TAG));
        if (StrUtil.isBlank(legacyUrl)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> fallback = new ArrayList<>();
        fallback.add(buildTarget(
                "legacy-default",
                "Legacy Default",
                legacyUrl,
                true,
                "",
                defaultIncludeImage,
                "",
                1
        ));
        return fallback;
    }

    public List<Map<String, Object>> listEnabledTargets() {
        List<Map<String, Object>> all = listAllTargets();
        List<Map<String, Object>> enabled = new ArrayList<>();
        for (Map<String, Object> target : all) {
            if (toBoolean(target.get("enabled"), true) && StrUtil.isNotBlank(trim(target.get("url")))) {
                enabled.add(target);
            }
        }
        return enabled;
    }

    public List<Map<String, Object>> saveTarget(String id,
                                                String name,
                                                String url,
                                                String bearerToken,
                                                Boolean enabled,
                                                Boolean includeImage) {
        return saveTarget(id, name, url, bearerToken, enabled, includeImage, null, null);
    }

    public List<Map<String, Object>> saveTarget(String id,
                                                String name,
                                                String url,
                                                String bearerToken,
                                                Boolean enabled,
                                                Boolean includeImage,
                                                String authFile,
                                                Integer retryCount) {
        String normalizedUrl = trim(url);
        if (StrUtil.isBlank(normalizedUrl)) {
            throw new IllegalArgumentException("target url is required");
        }
        List<Map<String, Object>> all = listAllTargets();
        String normalizedAuthFile = trim(authFile);
        int normalizedRetryCount = toPositiveInt(retryCount, 1);

        String targetId = trim(id);
        if (StrUtil.isBlank(targetId)) {
            targetId = IdUtil.fastSimpleUUID();
        }

        boolean updated = false;
        for (Map<String, Object> target : all) {
            if (!targetId.equals(trim(target.get("id")))) {
                continue;
            }
            target.put("name", StrUtil.blankToDefault(trim(name), normalizedUrl));
            target.put("url", normalizedUrl);
            target.put("bearer_token", trim(bearerToken));
            if (enabled != null) {
                target.put("enabled", enabled);
            }
            if (includeImage != null) {
                target.put("include_image", includeImage);
            }
            if (authFile != null) {
                target.put("auth_file", normalizedAuthFile == null ? "" : normalizedAuthFile);
            }
            if (retryCount != null) {
                target.put("retry_count", normalizedRetryCount);
            }
            updated = true;
            break;
        }
        if (!updated) {
            all.add(buildTarget(
                    targetId,
                    StrUtil.blankToDefault(trim(name), normalizedUrl),
                    normalizedUrl,
                    enabled == null || enabled,
                    trim(bearerToken),
                    includeImage != null && includeImage,
                    normalizedAuthFile,
                    normalizedRetryCount
            ));
        }

        persistTargets(all);
        return listAllTargets();
    }

    public List<Map<String, Object>> deleteTarget(String id) {
        String targetId = trim(id);
        if (StrUtil.isBlank(targetId)) {
            throw new IllegalArgumentException("target id is required");
        }
        List<Map<String, Object>> all = listAllTargets();
        List<Map<String, Object>> retained = new ArrayList<>();
        for (Map<String, Object> target : all) {
            if (!targetId.equals(trim(target.get("id")))) {
                retained.add(target);
            }
        }
        persistTargets(retained);
        return listAllTargets();
    }

    private void persistTargets(List<Map<String, Object>> targets) {
        String val = JSON.toJSONString(targets == null ? new ArrayList<>() : targets);
        Config config = getByTag(TARGETS_TAG);
        if (config == null) {
            config = new Config();
            config.setName("HTTP Push Targets");
            config.setTag(TARGETS_TAG);
        }
        config.setVal(val);
        configService.saveOrUpdate(config);
        configService.evictByTag(TARGETS_TAG);
    }

    private Config getByTag(String tag) {
        return configService.getOne(new LambdaQueryWrapper<Config>().eq(Config::getTag, tag), false);
    }

    private List<Map<String, Object>> normalizeTargets(String rawTargets, boolean defaultIncludeImage) {
        List<Map<String, Object>> data = new ArrayList<>();
        if (StrUtil.isBlank(rawTargets)) {
            return data;
        }
        JSONArray arr = null;
        try {
            arr = JSON.parseArray(rawTargets);
        } catch (Exception ignore) {
        }
        if (arr == null) {
            try {
                JSONObject obj = JSON.parseObject(rawTargets);
                if (obj != null) {
                    arr = obj.getJSONArray("targets");
                }
            } catch (Exception ignore) {
            }
        }
        if (arr == null) {
            return data;
        }

        for (int i = 0; i < arr.size(); i++) {
            JSONObject item = arr.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String url = trim(item.get("url"));
            if (StrUtil.isBlank(url)) {
                continue;
            }
            String id = StrUtil.blankToDefault(trim(item.get("id")), "target-" + i);
            String name = StrUtil.blankToDefault(trim(item.get("name")), url);
            boolean enabled = toBoolean(item.get("enabled"), true);
            String bearerToken = trim(item.get("bearer_token"));
            boolean includeImage = toBoolean(item.get("include_image"), defaultIncludeImage);
            String authFile = trim(item.get("auth_file"));
            int retryCount = toPositiveInt(item.get("retry_count"), 1);
            data.add(buildTarget(id, name, url, enabled, bearerToken, includeImage, authFile, retryCount));
        }
        return data;
    }

    private Map<String, Object> buildTarget(String id,
                                            String name,
                                            String url,
                                            boolean enabled,
                                            String bearerToken,
                                            boolean includeImage,
                                            String authFile,
                                            int retryCount) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("id", id);
        target.put("name", name);
        target.put("url", url);
        target.put("enabled", enabled);
        target.put("bearer_token", StrUtil.blankToDefault(bearerToken, ""));
        target.put("include_image", includeImage);
        target.put("auth_file", StrUtil.blankToDefault(authFile, ""));
        target.put("retry_count", retryCount <= 0 ? 1 : retryCount);
        return target;
    }

    private String trim(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private boolean toBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    private int toPositiveInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value).trim());
            return parsed <= 0 ? defaultValue : parsed;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
