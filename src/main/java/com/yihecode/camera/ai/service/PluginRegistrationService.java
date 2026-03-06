package com.yihecode.camera.ai.service;

import com.yihecode.camera.ai.plugin.PluginManifest;

import java.util.Map;

public interface PluginRegistrationService {

    Map<String, Object> register(String traceId, PluginManifest manifest, String healthUrl);

    Map<String, Object> getRegistration(String traceId, String registrationId);

    Map<String, Object> listRegistrations(String traceId,
                                          String pluginId,
                                          String runtime,
                                          String status,
                                          Boolean healthy,
                                          Integer offset,
                                          Integer limit);

    Map<String, Object> refreshRegistration(String traceId, String registrationId);

    Map<String, Object> stats(String traceId);

    Map<String, Object> unregisterRegistration(String traceId, String registrationId);
}
