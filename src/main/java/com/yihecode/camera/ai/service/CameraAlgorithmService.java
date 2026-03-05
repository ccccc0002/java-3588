package com.yihecode.camera.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yihecode.camera.ai.entity.CameraAlgorithm;

import java.util.List;

/**
 * 摄像头与算法关联管理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public interface CameraAlgorithmService extends IService<CameraAlgorithm> {

    /**
     *
     * @param algorithmId
     * @return
     */
    List<CameraAlgorithm> listByAlgorithm(Long algorithmId);

    /**
     *
     * @param cameraId
     * @return
     */
    List<CameraAlgorithm> listByCamera(Long cameraId);

    /**
     *
     * @param cameraId
     */
    void deleteByCamera(Long cameraId);

    /**
     *
     * @param cameraId
     * @return
     */
    String getNames(Long cameraId);

    /**
     * 临时方案
     * @param oldCameraId
     * @param newCameraId
     */
    void updateCameraId(Long oldCameraId, Long newCameraId);
}