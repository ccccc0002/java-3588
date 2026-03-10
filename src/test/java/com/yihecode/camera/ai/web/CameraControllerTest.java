package com.yihecode.camera.ai.web;

import com.yihecode.camera.ai.javacv.TakePhoto;
import com.yihecode.camera.ai.service.AlgorithmService;
import com.yihecode.camera.ai.service.CameraAlgorithmService;
import com.yihecode.camera.ai.service.CameraService;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.LocationService;
import com.yihecode.camera.ai.service.MediaStreamUrlService;
import com.yihecode.camera.ai.service.ReportPeriodService;
import com.yihecode.camera.ai.service.VideoPlayService;
import com.yihecode.camera.ai.service.WareHouseService;
import com.yihecode.camera.ai.utils.JsonResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @InjectMocks
    private CameraController cameraController;

    @Test
    void takePhotoShouldUseConfiguredFfmpegBin() {
        when(configService.getByValTag("media_ffmpeg_bin")).thenReturn("/usr/local/bin/ffmpeg-rockchip");
        when(takePhoto.take("rtsp://demo/stream", "/usr/local/bin/ffmpeg-rockchip")).thenReturn("frame.jpg");

        JsonResult result = cameraController.takePhoto("rtsp://demo/stream");

        assertEquals(0, result.getCode());
        assertEquals("frame.jpg", result.getData());
        verify(takePhoto).take("rtsp://demo/stream", "/usr/local/bin/ffmpeg-rockchip");
    }
}
