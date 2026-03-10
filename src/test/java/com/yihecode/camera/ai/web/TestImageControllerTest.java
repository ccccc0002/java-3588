package com.yihecode.camera.ai.web;

import com.yihecode.camera.ai.entity.Camera;
import com.yihecode.camera.ai.javacv.TakePhoto;
import com.yihecode.camera.ai.service.CameraService;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.utils.JsonResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestImageControllerTest {

    @Mock
    private CameraService cameraService;

    @Mock
    private TakePhoto takePhoto;

    @Mock
    private ConfigService configService;

    @Spy
    @InjectMocks
    private TestImageController testImageController;

    @Test
    @SuppressWarnings("unchecked")
    void getImageShouldCaptureMatchingCameraSnapshot() {
        Camera camera = new Camera();
        camera.setId(1L);
        camera.setName("rtsp-camera-245");
        camera.setRtspUrl("rtsp://demo/stream");
        camera.setVideoPlay(1);
        when(cameraService.listData()).thenReturn(List.of(camera));
        when(configService.getByValTag("media_ffmpeg_bin")).thenReturn("ffmpeg-rockchip");
        when(takePhoto.take("rtsp://demo/stream", "ffmpeg-rockchip")).thenReturn("frame.jpg");

        JsonResult result = testImageController.getImage("1");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("/image/stream?fileName=frame.jpg", data.get("picUrl"));
        assertEquals("success", data.get("result"));
        assertEquals(1L, ((Number) data.get("cameraId")).longValue());
    }

    @Test
    void getImageShouldFailWhenNoUsableCameraExists() {
        when(cameraService.listData()).thenReturn(Collections.emptyList());

        JsonResult result = testImageController.getImage("missing");

        assertEquals(500, result.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void ffmpegShouldReturnCodecProbePayload() {
        when(configService.getByValTag("media_ffmpeg_bin")).thenReturn("ffmpeg");
        doReturn(" V..... h264_rkmpp Rockchip H264 encoder\n V..... hevc_rkmpp Rockchip H265 encoder")
                .when(testImageController).runFfmpegCommand("ffmpeg", "-encoders");
        doReturn(" V..... h264_rkmpp Rockchip H264 decoder\n V..... hevc_rkmpp Rockchip H265 decoder")
                .when(testImageController).runFfmpegCommand("ffmpeg", "-decoders");

        JsonResult result = testImageController.ffmpeg("h264_rkmpp", "hevc_rkmpp");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertTrue(String.valueOf(data.get("encoderc")).contains("h264_rkmpp"));
        assertTrue(String.valueOf(data.get("decoderc")).contains("hevc_rkmpp"));
    }
}
