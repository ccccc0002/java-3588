package com.yihecode.camera.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yihecode.camera.ai.entity.Algorithm;
import com.yihecode.camera.ai.entity.CameraAlgorithm;
import com.yihecode.camera.ai.mapper.CameraAlgorithmMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 摄像头与算法关联管理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Service
public class CameraAlgorithmServiceImpl extends ServiceImpl<CameraAlgorithmMapper, CameraAlgorithm> implements CameraAlgorithmService {

    //
    @Autowired
    private AlgorithmService algorithmService;

    /**
     *
     * @param algorithmId
     * @return
     */
    @Override
    public List<CameraAlgorithm> listByAlgorithm(Long algorithmId) {
        LambdaQueryWrapper<CameraAlgorithm> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CameraAlgorithm::getAlgorithmId, algorithmId);
        List<CameraAlgorithm> list = this.list(queryWrapper);
        if(list == null) {
            return new ArrayList<>();
        }
        return list;
    }

    /**
     *
     * @param cameraId
     * @return
     */
    @Override
    public List<CameraAlgorithm> listByCamera(Long cameraId) {
        LambdaQueryWrapper<CameraAlgorithm> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CameraAlgorithm::getCameraId, cameraId);
        List<CameraAlgorithm> list = this.list(queryWrapper);
        if(list == null) {
            return new ArrayList<>();
        }
        return list;
    }

    /**
     *
     * @param cameraId
     */
    @Override
    public void deleteByCamera(Long cameraId) {
        LambdaQueryWrapper<CameraAlgorithm> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CameraAlgorithm::getCameraId, cameraId);
        this.remove(queryWrapper);
    }

    /**
     *
     * @param cameraId
     * @return
     */
    public String getNames(Long cameraId) {
        List<Algorithm> algorithmList = this.algorithmService.list();
        if (algorithmList == null) {
            algorithmList = new ArrayList<>();
        }
        Map<Long, String> algorithmMap = new HashMap<>();
        for (Algorithm algorithm : algorithmList) {
            algorithmMap.put(algorithm.getId(), algorithm.getName());
        }
        List<CameraAlgorithm> cameraAlgorithmList = listByCamera(cameraId);
        List<String> names = new ArrayList<>();
        for (CameraAlgorithm cameraAlgorithm : cameraAlgorithmList) {
            String name = algorithmMap.get(cameraAlgorithm.getAlgorithmId());
            names.add(name == null ? "-" : name);
        }
        if (names.isEmpty()) {
            return "No Binds";
        }
        return String.join(" | ", names);
    }

    /**
     * 临时方案
     *
     * @param oldCameraId
     * @param newCameraId
     */
    @Override
    public void updateCameraId(Long oldCameraId, Long newCameraId) {
        LambdaUpdateWrapper<CameraAlgorithm> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(CameraAlgorithm::getCameraId, newCameraId)
                .eq(CameraAlgorithm::getCameraId, oldCameraId);
        this.getBaseMapper().update(null, updateWrapper);
    }
}
