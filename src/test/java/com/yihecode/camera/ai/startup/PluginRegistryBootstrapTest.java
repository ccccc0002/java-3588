package com.yihecode.camera.ai.startup;

import com.yihecode.camera.ai.plugin.PluginManifest;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.PluginRegistrationService;
import com.yihecode.camera.ai.service.PluginRegistryRecord;
import com.yihecode.camera.ai.service.PluginRegistryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginRegistryBootstrapTest {

    @Mock
    private ConfigService configService;

    @Mock
    private PluginRegistrationService pluginRegistrationService;

    @Mock
    private PluginRegistryService pluginRegistryService;

    @InjectMocks
    private PluginRegistryBootstrap bootstrap;

    @Test
    void bootstrapDefaultPlugin_shouldRegisterWhenRegistryIsEmpty() {
        when(configService.getByValTag("infer_service_url")).thenReturn("http://127.0.0.1:19080");
        when(configService.getByValTag("infer_default_plugin_id")).thenReturn(null);
        when(configService.getByValTag("infer_default_plugin_version")).thenReturn(null);
        when(configService.getByValTag("infer_default_plugin_runtime")).thenReturn(null);
        when(configService.getByValTag("infer_default_plugin_capabilities")).thenReturn(null);
        when(pluginRegistryService.list()).thenReturn(Collections.emptyList());

        bootstrap.bootstrapDefaultPlugin();

        ArgumentCaptor<PluginManifest> manifestCaptor = ArgumentCaptor.forClass(PluginManifest.class);
        ArgumentCaptor<String> healthUrlCaptor = ArgumentCaptor.forClass(String.class);
        verify(pluginRegistrationService).register(anyString(), manifestCaptor.capture(), healthUrlCaptor.capture());
        assertEquals("yolov8n", manifestCaptor.getValue().getPluginId());
        assertEquals("http://127.0.0.1:19080/v1/infer", manifestCaptor.getValue().getInferUrl());
        assertTrue(manifestCaptor.getValue().getCapabilities().contains("inference"));
        assertEquals("http://127.0.0.1:19080/health", healthUrlCaptor.getValue());
    }

    @Test
    void bootstrapDefaultPlugin_shouldSkipWhenPluginAlreadyRegistered() {
        PluginRegistryRecord record = new PluginRegistryRecord();
        record.setPluginId("yolov8n");
        when(configService.getByValTag("infer_service_url")).thenReturn("http://127.0.0.1:19080");
        when(pluginRegistryService.list()).thenReturn(List.of(record));

        bootstrap.bootstrapDefaultPlugin();

        verify(pluginRegistrationService, never()).register(anyString(), org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void bootstrapDefaultPlugin_shouldSkipWhenInferServiceUrlIsBlank() {
        when(configService.getByValTag("infer_service_url")).thenReturn(" ");

        bootstrap.bootstrapDefaultPlugin();

        verify(pluginRegistrationService, never()).register(anyString(), org.mockito.ArgumentMatchers.any(), anyString());
    }
}
