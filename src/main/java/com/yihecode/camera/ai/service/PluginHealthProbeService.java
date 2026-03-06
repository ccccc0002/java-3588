package com.yihecode.camera.ai.service;

import java.util.Map;

public interface PluginHealthProbeService {

    Map<String, Object> probe(String traceId, String healthUrl);
}
