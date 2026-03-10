package com.yihecode.camera.ai.service;

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