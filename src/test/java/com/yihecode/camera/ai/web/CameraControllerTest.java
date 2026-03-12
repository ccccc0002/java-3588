package com.yihecode.camera.ai.web;

import com.yihecode.camera.ai.javacv.TakePhoto;
import com.yihecode.camera.ai.entity.Camera;
import com.yihecode.camera.ai.service.AlgorithmService;
import com.yihecode.camera.ai.service.CameraAlgorithmService;
import com.yihecode.camera.ai.service.CameraService;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.LocationService;
import com.yihecode.camera.ai.service.MediaStreamUrlService;
import com.yihecode.camera.ai.service.OperationLogService;
import com.yihecode.camera.ai.service.ReportPeriodService;
import com.yihecode.camera.ai.service.RoleAccessService;
import com.yihecode.camera.ai.service.VideoPlayService;
import com.yihecode.camera.ai.service.WareHouseService;
import com.yihecode.camera.ai.utils.JsonResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CameraControllerTest {

    @Mock private CameraService cameraService;
    @Mock private AlgorithmService algorithmService;
    @Mock private ReportPeriodService reportPeriodService;
    @Mock private CameraAlgorithmService cameraAlgorithmService;
    @Mock private WareHouseService wareHouseService;
    @Mock private LocationService locationService;
    @Mock private VideoPlayService videoPlayService;
    @Mock private TakePhoto takePhoto;
    @Mock private ConfigService configService;
    @Mock private MediaStreamUrlService mediaStreamUrlService;
    @Mock private RoleAccessService roleAccessService;
    @Mock private OperationLogService operationLogService;

    @InjectMocks
    private CameraController cameraController;

    @BeforeEach
    void setUp() {
        lenient().when(roleAccessService.canWriteSystem(any())).thenReturn(true);
    }

    @Test
    void takePhotoShouldUseConfiguredFfmpegBin() {
        when(configService.getByValTag("media_ffmpeg_bin")).thenReturn("/usr/local/bin/ffmpeg-rockchip");
        when(takePhoto.take("rtsp://demo/stream", "/usr/local/bin/ffmpeg-rockchip")).thenReturn("frame.jpg");

        JsonResult result = cameraController.takePhoto("rtsp://demo/stream");

        assertEquals(0, result.getCode());
        assertEquals("frame.jpg", result.getData());
        verify(takePhoto).take("rtsp://demo/stream", "/usr/local/bin/ffmpeg-rockchip");
    }

    @Test
    void saveShouldRejectCreateWhenLicenseMaxChannelsReached() {
        Camera camera = createValidCamera();
        when(configService.getByValTag("license_max_channels")).thenReturn("2");
        when(cameraService.listData()).thenReturn(java.util.Arrays.asList(new Camera(), new Camera()));

        JsonResult result = cameraController.save(camera, "1", "0.8", "", 1);

        assertEquals(500, result.getCode());
        verify(cameraService, never()).saveCamera(any(), any(), any(), any(), any());
    }

    @Test
    void saveShouldAllowUpdateWhenLicenseMaxChannelsReached() {
        Camera camera = createValidCamera();
        camera.setId(10L);

        JsonResult result = cameraController.save(camera, "1", "0.8", "", 1);

        assertEquals(0, result.getCode());
        verify(cameraService).saveCamera(camera, "1", "0.8", "", 1);
    }

    @Test
    void saveShouldFailWhenPermissionDenied() {
        Camera camera = createValidCamera();
        when(roleAccessService.canWriteSystem(any())).thenReturn(false);

        JsonResult result = cameraController.save(camera, "1", "0.8", "", 1);

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(cameraService, never()).saveCamera(any(), any(), any(), any(), any());
    }

    private Camera createValidCamera() {
        Camera camera = new Camera();
        camera.setName("cam-1");
        camera.setRtspUrl("rtsp://demo/stream");
        camera.setIntervalTime(5F);
        return camera;
    }
}
