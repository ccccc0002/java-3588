package com.yihecode.camera.ai.web;

import com.yihecode.camera.ai.entity.Camera;
import com.yihecode.camera.ai.entity.Config;
import com.yihecode.camera.ai.service.CameraService;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.OperationLogService;
import com.yihecode.camera.ai.service.RoleAccessService;
import com.yihecode.camera.ai.service.SystemProfileService;
import com.yihecode.camera.ai.service.VideoPlayService;
import com.yihecode.camera.ai.utils.JsonResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigControllerTest {

    @Mock
    private ConfigService configService;

    @Mock
    private VideoPlayService videoPlayService;

    @Mock
    private CameraService cameraService;

    @Mock
    private SystemProfileService systemProfileService;

    @Mock
    private RoleAccessService roleAccessService;

    @Mock
    private OperationLogService operationLogService;

    @InjectMocks
    private ConfigController configController;

    @BeforeEach
    void setupPermissionDefaults() {
        lenient().when(roleAccessService.canWriteSystem(any())).thenReturn(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void licenseInfoShouldReturnComputedFields() {
        when(systemProfileService.getDeviceId()).thenReturn("dev-001");
        when(configService.getByValTag("license_key")).thenReturn("license-abc");
        when(configService.getByValTag("license_max_channels")).thenReturn("8");
        when(configService.getByValTag("license_expire_at")).thenReturn("2099-12-31");
        when(configService.getByValTag("license_tenant")).thenReturn("tenant-a");
        when(cameraService.listData()).thenReturn(Arrays.asList(new Camera(), new Camera()));

        JsonResult result = configController.licenseInfo();

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("dev-001", data.get("device_id"));
        assertEquals("license-abc", data.get("license_key"));
        assertEquals(8, data.get("max_channels"));
        assertEquals(2, data.get("current_camera_count"));
        assertEquals(true, data.get("valid"));
    }

    @Test
    void saveLicenseShouldPersistTags() {
        JsonResult result = configController.saveLicense(
                null,
                "license-xyz",
                null,
                16,
                null,
                "2099-01-01",
                "tenant-b"
        );

        assertEquals(0, result.getCode());
        ArgumentCaptor<Config> captor = ArgumentCaptor.forClass(Config.class);
        verify(configService, atLeast(4)).saveOrUpdate(captor.capture(), any());
        List<Config> saved = captor.getAllValues();
        assertTrue(saved.stream().anyMatch(c -> "license_key".equals(c.getTag()) && "license-xyz".equals(c.getVal())));
        assertTrue(saved.stream().anyMatch(c -> "license_max_channels".equals(c.getTag()) && "16".equals(c.getVal())));
        assertTrue(saved.stream().anyMatch(c -> "license_expire_at".equals(c.getTag()) && "2099-01-01".equals(c.getVal())));
        assertTrue(saved.stream().anyMatch(c -> "license_tenant".equals(c.getTag()) && "tenant-b".equals(c.getVal())));
    }

    @Test
    void saveLicenseShouldRejectInvalidExpireAtFormat() {
        JsonResult result = configController.saveLicense(
                null,
                "license-xyz",
                null,
                8,
                null,
                "2099/01/01",
                "tenant-x"
        );

        assertEquals(500, result.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void networkInterfacesShouldDelegateToSystemProfileService() {
        when(systemProfileService.listNetworkInterfaces()).thenReturn(List.of(Map.of(
                "name", "eth0",
                "mac", "AA:BB:CC:DD:EE:FF",
                "ipv4", "192.168.1.100"
        )));

        JsonResult result = configController.listNetworkInterfaces();

        assertEquals(0, result.getCode());
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.getData();
        assertEquals(1, data.size());
        assertEquals("eth0", data.get(0).get("name"));
    }

    @Test
    void saveNetworkConfigShouldRequireInterfaceName() {
        JsonResult result = configController.saveNetworkConfig(null, null, "192.168.1.10", "192.168.1.1", "8.8.8.8");
        assertEquals(500, result.getCode());
    }

    @Test
    void saveNetworkConfigShouldRejectInvalidIpv4() {
        JsonResult result = configController.saveNetworkConfig("eth0", null, "300.1.1.1", "192.168.1.1", "8.8.8.8");
        assertEquals(500, result.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteNetworkConfigShouldRemoveTargetInterface() {
        when(configService.getByValTag("network_configurations")).thenReturn("[{\"interface_name\":\"eth0\",\"ip\":\"192.168.1.2\"},{\"interface_name\":\"eth1\",\"ip\":\"192.168.1.3\"}]");

        JsonResult result = configController.deleteNetworkConfig("eth0", null);

        assertEquals(0, result.getCode());
        List<Map<String, Object>> remaining = (List<Map<String, Object>>) result.getData();
        assertNotNull(remaining);
        assertEquals(1, remaining.size());
        assertEquals("eth1", remaining.get(0).get("interface_name"));
    }

    @Test
    void saveNetworkConfigShouldDenyWhenNoPermission() {
        when(roleAccessService.canWriteSystem(any())).thenReturn(false);
        JsonResult result = configController.saveNetworkConfig("eth0", null, "192.168.1.10", "192.168.1.1", "8.8.8.8");
        assertEquals(500, result.getCode());
        verify(operationLogService).record(eq("network:save"), eq("interface=eth0"), eq(false), eq("permission denied"), eq(""));
    }

    @Test
    void saveShouldDenyWhenNoPermission() {
        when(roleAccessService.canWriteSystem(any())).thenReturn(false);
        Config config = new Config();
        config.setTag("wsUrl");

        JsonResult result = configController.save(config);

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(operationLogService).record(eq("config:save"), eq("tag=wsUrl"), eq(false), eq("permission denied"), eq(""));
    }

    @Test
    void deleteShouldDenyWhenNoPermission() {
        when(roleAccessService.canWriteSystem(any())).thenReturn(false);

        JsonResult result = configController.delete(99L);

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(operationLogService).record(eq("config:delete"), eq("id=99"), eq(false), eq("permission denied"), eq(""));
    }

    @Test
    void saveLicenseShouldDenyWhenNoPermission() {
        when(roleAccessService.canWriteSystem(any())).thenReturn(false);

        JsonResult result = configController.saveLicense("k", null, 8, null, "2099-01-01", null, "tenant-a");

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(operationLogService).record(eq("license:save"), eq("license"), eq(false), eq("permission denied"), eq(""));
    }

    @Test
    void deleteNetworkConfigShouldDenyWhenNoPermission() {
        when(roleAccessService.canWriteSystem(any())).thenReturn(false);

        JsonResult result = configController.deleteNetworkConfig("eth1", null);

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(operationLogService).record(eq("network:delete"), eq("interface=eth1"), eq(false), eq("permission denied"), eq(""));
    }
}
