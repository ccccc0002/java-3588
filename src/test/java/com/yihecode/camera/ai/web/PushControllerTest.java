package com.yihecode.camera.ai.web;

import com.yihecode.camera.ai.entity.Config;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.OperationLogService;
import com.yihecode.camera.ai.service.RoleAccessService;
import com.yihecode.camera.ai.utils.JsonResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class PushControllerTest {

    @Mock
    private ConfigService configService;

    @Mock
    private RoleAccessService roleAccessService;

    @Mock
    private OperationLogService operationLogService;

    @InjectMocks
    private PushController pushController;

    @BeforeEach
    void setUp() {
        lenient().when(roleAccessService.canManagePushTargets(any())).thenReturn(true);
    }

    @Test
    void voicePushPageShouldReturnTemplate() {
        assertEquals("push/voice", pushController.voicePushPage());
    }

    @Test
    void smsPushPageShouldReturnTemplate() {
        assertEquals("push/sms_config", pushController.smsPushPage());
    }

    @Test
    void saveVoicePushConfigShouldFailWhenPermissionDenied() {
        when(roleAccessService.canManagePushTargets(any())).thenReturn(false);

        JsonResult result = pushController.saveVoicePushConfig(true, "provider", "http://localhost", "tk", "13800000000");

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(configService, never()).saveOrUpdate(any(Config.class));
    }

    @Test
    void saveVoicePushConfigShouldValidateUrlPrefix() {
        JsonResult result = pushController.saveVoicePushConfig(true, "provider", "ftp://invalid", "tk", "13800000000");

        assertEquals(500, result.getCode());
        assertEquals("url must start with http:// or https://", result.getMsg());
        verify(configService, never()).saveOrUpdate(any(Config.class));
    }

    @Test
    void saveVoicePushConfigShouldPersistConfigTags() {
        JsonResult result = pushController.saveVoicePushConfig(true, "provider", "https://voice.example.com/push", "tk", "13800000000");

        assertEquals(0, result.getCode());
        verify(configService, times(5)).saveOrUpdate(any(Config.class));
        verify(configService).evictByTag(eq("voice_push_enabled"));
        verify(configService).evictByTag(eq("voice_push_provider"));
        verify(configService).evictByTag(eq("voice_push_url"));
        verify(configService).evictByTag(eq("voice_push_bearer"));
        verify(configService).evictByTag(eq("voice_push_numbers"));
    }

    @Test
    void saveSmsPushConfigShouldFailWhenPermissionDenied() {
        when(roleAccessService.canManagePushTargets(any())).thenReturn(false);

        JsonResult result = pushController.saveSmsPushConfig(true, "yunpian", "https://sms.example.com/send", null, "key", null, "tpl", null);

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(configService, never()).saveOrUpdate(any(Config.class));
    }

    @Test
    void saveSmsPushConfigShouldValidateUrlPrefix() {
        JsonResult result = pushController.saveSmsPushConfig(true, "yunpian", "ftp://invalid", null, "key", null, "tpl", null);

        assertEquals(500, result.getCode());
        assertEquals("api_url must start with http:// or https://", result.getMsg());
        verify(configService, never()).saveOrUpdate(any(Config.class));
    }

    @Test
    void saveSmsPushConfigShouldRequireApiKeyAndTplWhenEnabled() {
        JsonResult noKey = pushController.saveSmsPushConfig(true, "yunpian", "https://sms.example.com/send", null, "", null, "tpl", null);
        assertEquals(500, noKey.getCode());
        assertEquals("api_key is required when sms enabled", noKey.getMsg());

        JsonResult noTpl = pushController.saveSmsPushConfig(true, "yunpian", "https://sms.example.com/send", null, "key", null, "", null);
        assertEquals(500, noTpl.getCode());
        assertEquals("tpl_id is required when sms enabled", noTpl.getMsg());
    }

    @Test
    void saveSmsPushConfigShouldPersistConfigTags() {
        JsonResult result = pushController.saveSmsPushConfig(true, "yunpian", "https://sms.example.com/send", null, "key", null, "tpl", null);

        assertEquals(0, result.getCode());
        verify(configService, times(5)).saveOrUpdate(any(Config.class));
        verify(configService).evictByTag(eq("smsEnable"));
        verify(configService).evictByTag(eq("sms_provider"));
        verify(configService).evictByTag(eq("sms_api_url"));
        verify(configService).evictByTag(eq("sms_api_key"));
        verify(configService).evictByTag(eq("sms_tpl_id"));
    }
}
