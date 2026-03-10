package com.yihecode.camera.ai.service;

import cn.hutool.core.util.StrUtil;
import com.yihecode.camera.ai.entity.Camera;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;

/**
 * Stream play/push URL resolver for legacy and ZLMediaKit modes.
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
        host = resolveBrowserFacingHost(host);
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
        String streamUrl = resolveBrowserFacingHost(configService.getByValTag("streamUrl"));
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

    private String resolveBrowserFacingHost(String configuredHost) {
        String trimmed = StrUtil.trim(configuredHost);
        if (StrUtil.isBlank(trimmed) || !isLoopbackOrWildcardHost(trimmed)) {
            return trimmed;
        }
        String requestHost = resolveCurrentRequestHost();
        if (StrUtil.isBlank(requestHost) || isLoopbackOrWildcardHost(requestHost)) {
            return trimmed;
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            try {
                URI uri = URI.create(trimmed);
                URI updated = new URI(uri.getScheme(), uri.getUserInfo(), requestHost, uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
                return updated.toString();
            } catch (Exception ignored) {
                return trimmed;
            }
        }
        return requestHost;
    }

    private boolean isLoopbackOrWildcardHost(String value) {
        String candidate = StrUtil.trim(value);
        if (StrUtil.isBlank(candidate)) {
            return false;
        }
        if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
            try {
                URI uri = URI.create(candidate);
                candidate = uri.getHost();
            } catch (Exception ignored) {
                return false;
            }
        } else {
            int slash = candidate.indexOf('/');
            if (slash >= 0) {
                candidate = candidate.substring(0, slash);
            }
            if (candidate.startsWith("[") && candidate.contains("]")) {
                candidate = candidate.substring(1, candidate.indexOf(']'));
            } else {
                int colon = candidate.indexOf(':');
                if (colon >= 0) {
                    candidate = candidate.substring(0, colon);
                }
            }
        }
        String normalized = StrUtil.trim(candidate).toLowerCase(Locale.ROOT);
        return "127.0.0.1".equals(normalized)
                || "localhost".equals(normalized)
                || "0.0.0.0".equals(normalized)
                || "::1".equals(normalized)
                || "[::1]".equals(normalized);
    }

    private String resolveCurrentRequestHost() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return "";
            }
            HttpServletRequest request = attributes.getRequest();
            return request == null ? "" : StrUtil.trim(request.getServerName());
        } catch (Exception ignored) {
            return "";
        }
    }

    private String joinBase(String schema, String host, String port) {
        if (StrUtil.isBlank(host)) {
            return "";
        }
        String h = StrUtil.trim(host);
        if (h.startsWith("http://") || h.startsWith("https://")) {
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
