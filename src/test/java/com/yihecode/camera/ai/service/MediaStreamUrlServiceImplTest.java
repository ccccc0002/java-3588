package com.yihecode.camera.ai.service;

import com.yihecode.camera.ai.entity.Camera;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaStreamUrlServiceImplTest {

    @Mock
    private ConfigService configService;

    private MediaStreamUrlServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MediaStreamUrlServiceImpl();
        ReflectionTestUtils.setField(service, "configService", configService);
    }

    @Test
    void buildPlayUrl_shouldUseZlmHttpFlvNaming() {
        Camera camera = new Camera();
        camera.setId(1L);
        camera.setRtspUrl("rtsp://cam-1");
        when(configService.getByValTag("media_server_type")).thenReturn("zlm");
        when(configService.getByValTag("zlm_schema")).thenReturn("http");
        when(configService.getByValTag("zlm_host_public")).thenReturn("127.0.0.1");
        when(configService.getByValTag("zlm_http_port")).thenReturn("1987");
        when(configService.getByValTag("zlm_app")).thenReturn("live");
        when(configService.getByValTag("zlm_play_mode")).thenReturn("stream");

        String playUrl = service.buildPlayUrl(camera, 18082);

        assertEquals("http://127.0.0.1:1987/live/1.live.flv", playUrl);
    }

    @Test
    void buildPushRtmpUrl_shouldFallbackToPublicHostWhenInnerHostIsMissing() {
        when(configService.getByValTag("media_server_type")).thenReturn("zlm");
        when(configService.getByValTag("zlm_host_inner")).thenReturn("");
        when(configService.getByValTag("video_inner_ip")).thenReturn("");
        when(configService.getByValTag("zlm_host_public")).thenReturn("127.0.0.1");
        when(configService.getByValTag("zlm_rtmp_port")).thenReturn("");
        when(configService.getByValTag("zlm_app")).thenReturn("live");

        String pushUrl = service.buildPushRtmpUrl(1L, 18082);

        assertEquals("rtmp://127.0.0.1:1935/live/1", pushUrl);
    }
}
