package com.yihecode.camera.ai.service;

import com.yihecode.camera.ai.entity.Camera;

/**
 * 媒体流地址构建服务
 * 统一生成播放地址和推流地址，兼容 legacy 与 zlm 两种模式。
 */
public interface MediaStreamUrlService {

    /**
     * 是否启用 ZLM 地址规则。
     *
     * @return true-zlm false-legacy
     */
    boolean isZlmMode();

    /**
     * 构建播放地址。
     *
     * @param camera 摄像头
     * @param videoPort 播放端口（legacy 模式使用）
     * @return 播放地址
     */
    String buildPlayUrl(Camera camera, Integer videoPort);

    /**
     * 构建算法推流地址（rtmp_url）。
     *
     * @param cameraId 摄像头ID
     * @param videoPlayPort 播放端口（legacy 模式用于推导推流端口）
     * @return 推流地址
     */
    String buildPushRtmpUrl(Long cameraId, Integer videoPlayPort);
}

