package com.yihecode.camera.ai.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class HttpPluginHealthProbeService implements PluginHealthProbeService {

    private static final int DEFAULT_TIMEOUT_MS = 2000;

    @Autowired
    private ConfigService configService;

    @Autowired
    private InferenceHttpGateway inferenceHttpGateway;

    @Override
    public Map<String, Object> probe(String traceId, String healthUrl) {
        Map<String, Object> data = new LinkedHashMap<>();
        String normalizedHealthUrl = normalizeUrl(healthUrl);
        data.put("trace_id", traceId);
        data.put("health_url", normalizedHealthUrl);

        if (StrUtil.isBlank(normalizedHealthUrl)) {
            data.put("timeout_ms", DEFAULT_TIMEOUT_MS);
            data.put("healthy", false);
            data.put("status", "misconfigured");
            data.put("error", "plugin health_url is blank");
            return data;
        }

        int timeoutMs = getTimeoutMs();
        data.put("timeout_ms", timeoutMs);

        try {
            InferenceHttpResponse response = inferenceHttpGateway.get(normalizedHealthUrl, timeoutMs);
            data.put("http_status", response.getStatus());
            JSONObject body = safeParseObject(response.getBody());
            if (body != null) {
                for (Map.Entry<String, Object> entry : body.entrySet()) {
                    data.put(entry.getKey(), entry.getValue());
                }
            }
            if (response.getStatus() == 200) {
                if (!data.containsKey("status")) {
                    data.put("status", "ok");
                }
                data.put("healthy", true);
            } else {
                data.put("healthy", false);
                if (!data.containsKey("status")) {
                    data.put("status", "down");
                }
                data.put("error", "non-200 response: " + response.getStatus());
            }
            return data;
        } catch (Exception e) {
            data.put("healthy", false);
            data.put("status", "down");
            data.put("error", e.getMessage());
            return data;
        }
    }

    private int getTimeoutMs() {
        String raw = configService == null ? null : configService.getByValTag("plugin_probe_timeout_ms");
        if (StrUtil.isBlank(raw)) {
            return DEFAULT_TIMEOUT_MS;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return DEFAULT_TIMEOUT_MS;
        }
    }

    private String normalizeUrl(String healthUrl) {
        if (StrUtil.isBlank(healthUrl)) {
            return null;
        }
        return healthUrl.trim();
    }

    private JSONObject safeParseObject(String body) {
        if (StrUtil.isBlank(body)) {
            return null;
        }
        try {
            return JSONObject.parseObject(body);
        } catch (Exception ex) {
            return null;
        }
    }
}
