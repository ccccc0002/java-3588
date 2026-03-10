package com.yihecode.camera.ai.service;

import cn.hutool.core.util.StrUtil;
import com.yihecode.camera.ai.entity.Camera;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 濯掍綋娴佸湴鍧€鏋勫缓瀹炵幇
 */
@Service
public class MediaStreamUrlServiceImpl implements MediaStreamUrlService {

    @Autowired
    private ConfigService configService;

    @Override
    public boolean isZlmMode() {
        String mediaServerType = configService.getByValTag("media_server_type");
        if ("zlm".equalsIgnoreCase(StrUtil.trim(mediaServerType))) {
            return true;
        }
        // 鍏煎鑰侀厤缃細浠呴厤缃?zlm_enable=1 鏃朵篃鍚敤 zlm 妯″紡
        return "1".equals(StrUtil.trim(configService.getByValTag("zlm_enable")));
    }

    @Override
    public String buildPlayUrl(Camera camera, Integer videoPort) {
        if (isZlmMode()) {
            return buildZlmPlayUrl(camera);
        }
        return buildLegacyPlayUrl(videoPort);
    }

    @Override
    public String buildPushRtmpUrl(Long cameraId, Integer videoPlayPort) {
        if (cameraId == null) {
            return "";
        }
        if (isZlmMode()) {
            return buildZlmPushUrl(cameraId);
        }
        return buildLegacyPushUrl(cameraId, videoPlayPort);
    }

    private String buildZlmPlayUrl(Camera camera) {
        String schema = configService.getByValTag("zlm_schema");
        if (StrUtil.isBlank(schema)) {
            schema = "http";
        }
        String host = configService.getByValTag("zlm_host_public");
        if (StrUtil.isBlank(host)) {
            host = configService.getByValTag("streamUrl");
        }
        String httpPort = configService.getByValTag("zlm_http_port");
        String app = configService.getByValTag("zlm_app");
        if (StrUtil.isBlank(app)) {
            app = "live";
        }
        String playMode = configService.getByValTag("zlm_play_mode");
        if (StrUtil.isBlank(playMode)) {
            playMode = "stream";
        }

        String base = joinBase(schema, host, httpPort);
        if (StrUtil.isBlank(base)) {
            return "";
        }

        if ("proxy".equalsIgnoreCase(playMode)) {
            String rtspUrl = (camera == null) ? "" : camera.getRtspUrl();
            if (StrUtil.isBlank(rtspUrl)) {
                return base + "/live?url={rtspUrl}";
            }
            return base + "/live?url=" + rtspUrl;
        }

        if (camera == null || camera.getId() == null) {
            return base + "/" + app + "/{cameraId}.live.flv";
        }
        return base + "/" + app + "/" + camera.getId() + ".live.flv";
    }

    private String buildLegacyPlayUrl(Integer videoPort) {
        String streamUrl = configService.getByValTag("streamUrl");
        if (StrUtil.isBlank(streamUrl) || videoPort == null || videoPort <= 0) {
            return "";
        }
        return streamUrl + ":" + videoPort + "/live";
    }

    private String buildZlmPushUrl(Long cameraId) {
        String host = configService.getByValTag("zlm_host_inner");
        if (StrUtil.isBlank(host)) {
            host = configService.getByValTag("video_inner_ip");
        }
        if (StrUtil.isBlank(host)) {
            host = configService.getByValTag("zlm_host_public");
        }
        String rtmpPort = configService.getByValTag("zlm_rtmp_port");
        if (StrUtil.isBlank(rtmpPort)) {
            rtmpPort = "1935";
        }
        String app = configService.getByValTag("zlm_app");
        if (StrUtil.isBlank(app)) {
            app = "live";
        }
        if (StrUtil.isBlank(host)) {
            return "";
        }
        return "rtmp://" + host + ":" + rtmpPort + "/" + app + "/" + cameraId;
    }

    private String buildLegacyPushUrl(Long cameraId, Integer videoPlayPort) {
        if (videoPlayPort == null || videoPlayPort <= 0) {
            return "";
        }
        String videoPortRule = configService.getByValTag("video_port_rule");
        int videoPortDiss = 0;
        if (StrUtil.isNotBlank(videoPortRule)) {
            try {
                videoPortDiss = Integer.parseInt(videoPortRule);
            } catch (Exception e) {
                videoPortDiss = 0;
            }
        }
        String videoInnerIp = configService.getByValTag("video_inner_ip");
        if (StrUtil.isBlank(videoInnerIp)) {
            return "";
        }
        int videoPushPort = videoPlayPort + videoPortDiss;
        return "rtmp://" + videoInnerIp + ":" + videoPushPort + "/live/" + cameraId;
    }

    private String joinBase(String schema, String host, String port) {
        if (StrUtil.isBlank(host)) {
            return "";
        }
        String h = StrUtil.trim(host);
        if (h.startsWith("http://") || h.startsWith("https://")) {
            // 鍏煎鍘嗗彶 streamUrl 宸茬粡甯﹀崗璁殑鍦烘櫙
            if (StrUtil.isBlank(port) || h.contains(":" + port)) {
                return h;
            }
            return h + ":" + port;
        }
        String s = StrUtil.isBlank(schema) ? "http" : schema.trim();
        if (StrUtil.isBlank(port)) {
            return s + "://" + h;
        }
        return s + "://" + h + ":" + port.trim();
    }
}
