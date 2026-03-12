package com.yihecode.camera.ai.web;

import com.yihecode.camera.ai.entity.Camera;
import com.yihecode.camera.ai.entity.WareHouse;
import com.yihecode.camera.ai.service.CameraService;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.OperationLogService;
import com.yihecode.camera.ai.service.RoleAccessService;
import com.yihecode.camera.ai.service.WareHouseService;
import com.yihecode.camera.ai.utils.JsonResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WareHouseControllerTest {

    @Mock
    private WareHouseService wareHouseService;

    @Mock
    private CameraService cameraService;

    @Mock
    private ConfigService configService;

    @Mock
    private RoleAccessService roleAccessService;

    @Mock
    private OperationLogService operationLogService;

    @InjectMocks
    private WareHouseController wareHouseController;

    @BeforeEach
    void setupPermissionDefaults() {
        lenient().when(roleAccessService.canSyncWarehouse(any())).thenReturn(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sync2allShouldSkipCreateWhenLicenseLimitReached() {
        WareHouse node = createCameraNode(100L, "cam-node-100", "rtsp://demo/100");
        when(wareHouseService.list()).thenReturn(List.of(node));
        when(configService.getByValTag("license_max_channels")).thenReturn("1");
        when(cameraService.listData()).thenReturn(List.of(new Camera()));
        when(cameraService.getByWareHouseId(100L)).thenReturn(null);

        JsonResult result = wareHouseController.sync2all();

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(0, data.get("synced"));
        assertEquals(1, data.get("skipped_by_license"));
        verify(cameraService, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sync2allShouldCreateWhenLicenseQuotaAvailable() {
        WareHouse node = createCameraNode(101L, "cam-node-101", "rtsp://demo/101");
        when(wareHouseService.list()).thenReturn(List.of(node));
        when(configService.getByValTag("license_max_channels")).thenReturn("2");
        when(cameraService.listData()).thenReturn(List.of(new Camera()));
        when(cameraService.getByWareHouseId(101L)).thenReturn(null);

        JsonResult result = wareHouseController.sync2all();

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(1, data.get("synced"));
        assertEquals(0, data.get("skipped_by_license"));
        verify(cameraService).save(any(Camera.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sync2allShouldUpdateExistingEvenWhenLimitReached() {
        WareHouse node = createCameraNode(102L, "cam-node-102-new", "rtsp://demo/102-new");
        Camera existing = new Camera();
        existing.setId(9L);
        existing.setName("cam-node-102-old");
        existing.setRtspUrl("rtsp://demo/102-old");

        when(wareHouseService.list()).thenReturn(List.of(node));
        when(configService.getByValTag("license_max_channels")).thenReturn("1");
        when(cameraService.listData()).thenReturn(List.of(new Camera()));
        when(cameraService.getByWareHouseId(102L)).thenReturn(existing);

        JsonResult result = wareHouseController.sync2all();

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(1, data.get("synced"));
        assertEquals(0, data.get("skipped_by_license"));
        verify(cameraService).updateById(any(Camera.class));
        verify(cameraService, never()).save(any(Camera.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sync2nodeShouldExposeSkippedByLicense() {
        WareHouse node = createCameraNode(200L, "cam-node-200", "rtsp://demo/200");
        node.setIndexCode("IDX-200");
        when(wareHouseService.getByIndexCode("IDX-200")).thenReturn(node);
        when(configService.getByValTag("license_max_channels")).thenReturn("1");
        when(cameraService.listData()).thenReturn(List.of(new Camera()));
        when(cameraService.getByWareHouseId(200L)).thenReturn(null);

        JsonResult result = wareHouseController.sync2node("IDX-200");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(0, data.get("synced"));
        assertEquals(1, data.get("skipped_by_license"));
        verify(cameraService, never()).save(any(Camera.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void select2exportShouldExposeSkippedByLicense() {
        WareHouse node = createCameraNode(300L, "cam-node-300", "rtsp://demo/300");
        when(wareHouseService.getById(300L)).thenReturn(node);
        when(configService.getByValTag("license_max_channels")).thenReturn("1");
        when(cameraService.listData()).thenReturn(List.of(new Camera()));
        when(cameraService.getByWareHouseId(300L)).thenReturn(null);

        JsonResult result = wareHouseController.select2export("300");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(0, data.get("synced"));
        assertEquals(1, data.get("skipped_by_license"));
        verify(cameraService, never()).save(any(Camera.class));
    }

    @Test
    void sync2allShouldDenyWhenNoPermission() {
        when(roleAccessService.canSyncWarehouse(any())).thenReturn(false);
        JsonResult result = wareHouseController.sync2all();
        assertEquals(500, result.getCode());
    }

    @Test
    void saveShouldDenyWhenNoWritePermission() {
        when(roleAccessService.canWriteSystem(any())).thenReturn(false);
        WareHouse wareHouse = new WareHouse();
        wareHouse.setName("node-a");

        JsonResult result = wareHouseController.save(wareHouse, null, null);

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(wareHouseService, never()).saveOrUpdate(any(WareHouse.class));
    }

    @Test
    void deleteShouldDenyWhenNoWritePermission() {
        when(roleAccessService.canWriteSystem(any())).thenReturn(false);

        JsonResult result = wareHouseController.delete(1L);

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(wareHouseService, never()).removeById(1L);
    }

    private WareHouse createCameraNode(Long id, String name, String rtsp) {
        WareHouse node = new WareHouse();
        node.setId(id);
        node.setName(name);
        node.setRtspUrl(rtsp);
        node.setTreeType(1);
        return node;
    }
}
