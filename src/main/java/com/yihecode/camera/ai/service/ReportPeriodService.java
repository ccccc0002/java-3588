package com.yihecode.camera.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yihecode.camera.ai.entity.ReportPeriod;

import java.util.List;

/**
 * 告警时段管理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public interface ReportPeriodService extends IService<ReportPeriod> {

    /**
     *
     * @param cameraId
     * @return
     */
    List<Long> listAlgorithmId(Long cameraId);

    /**
     *
     * @param cameraId
     * @param algorithmId
     */
    void deleteByCameraAndAlgorithm(Long cameraId, Long algorithmId);

    /**
     *
     * @param cameraId
     * @param algorithmId
     * @return
     */
    List<ReportPeriod> listData(Long cameraId, Long algorithmId);

    /**
     * 临时方案
     * @param oldCameraId
     * @param newCameraId
     */
    void updateCameraId(Long oldCameraId, Long newCameraId);
}