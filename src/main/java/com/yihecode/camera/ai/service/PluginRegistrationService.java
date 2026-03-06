package com.yihecode.camera.ai.service;

import com.yihecode.camera.ai.plugin.PluginManifest;

import java.util.Map;

public interface PluginRegistrationService {

    Map<String, Object> register(String traceId, PluginManifest manifest, String healthUrl);

    Map<String, Object> getRegistration(String traceId, String registrationId);

    Map<String, Object> listRegistrations(String traceId);
}
