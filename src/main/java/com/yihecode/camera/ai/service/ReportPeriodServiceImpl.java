package com.yihecode.camera.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yihecode.camera.ai.entity.ReportPeriod;
import com.yihecode.camera.ai.mapper.ReportPeriodMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 告警时段管理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Service
public class ReportPeriodServiceImpl extends ServiceImpl<ReportPeriodMapper, ReportPeriod> implements ReportPeriodService {

    /**
     *
     * @param cameraId
     * @return
     */
    @Override
    public List<Long> listAlgorithmId(Long cameraId) {
        LambdaQueryWrapper<ReportPeriod> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ReportPeriod::getCameraId, cameraId);

        List<ReportPeriod> list = this.list(queryWrapper);
        if(list == null) {
            list = new ArrayList<>();
        }

        //
        List<Long> algorithmIds = new ArrayList<>();
        for(ReportPeriod reportPeriod : list) {
            algorithmIds.add(reportPeriod.getAlgorithmId());
        }

        return algorithmIds;
    }

    /**
     *
     * @param cameraId
     * @param algorithmId
     */
    @Override
    public void deleteByCameraAndAlgorithm(Long cameraId, Long algorithmId) {
        LambdaQueryWrapper<ReportPeriod> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ReportPeriod::getCameraId, cameraId);
        queryWrapper.eq(ReportPeriod::getAlgorithmId, algorithmId);
        this.remove(queryWrapper);
    }

    /**
     *
     * @param cameraId
     * @param algorithmId
     * @return
     */
    @Override
    public List<ReportPeriod> listData(Long cameraId, Long algorithmId) {
        LambdaQueryWrapper<ReportPeriod> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ReportPeriod::getCameraId, cameraId);
        queryWrapper.eq(ReportPeriod::getAlgorithmId, algorithmId);
        queryWrapper.orderByAsc(ReportPeriod::getStartTime);

        //
        List<ReportPeriod> reportPeriodList = this.list(queryWrapper);
        if(reportPeriodList == null) {
            return new ArrayList<>();
        }
        return reportPeriodList;
    }

    /**
     * 临时方案
     *
     * @param oldCameraId
     * @param newCameraId
     */
    @Override
    public void updateCameraId(Long oldCameraId, Long newCameraId) {
        LambdaUpdateWrapper<ReportPeriod> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(ReportPeriod::getCameraId, newCameraId)
                .eq(ReportPeriod::getCameraId, oldCameraId);
        this.getBaseMapper().update(null, updateWrapper);
    }
}