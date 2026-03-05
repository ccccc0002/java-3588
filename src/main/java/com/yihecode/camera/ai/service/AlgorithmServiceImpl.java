package com.yihecode.camera.ai.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yihecode.camera.ai.entity.Algorithm;
import com.yihecode.camera.ai.mapper.AlgorithmMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 算法管理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Service
public class AlgorithmServiceImpl extends ServiceImpl<AlgorithmMapper, Algorithm> implements AlgorithmService {

    public Map<Long, String> toMap() {
        List<Algorithm> algorithmList = list();
        if (algorithmList == null) {
            algorithmList = new ArrayList<>();
        }
        Map<Long, String> algorithmMap = new HashMap<>();
        for (Algorithm algorithm : algorithmList) {
            algorithmMap.put(algorithm.getId(), algorithm.getName());
        }
        return algorithmMap;
    }

    /**
     * 更新统计标识
     *
     * @param idList
     */
    @Override
    public void updateStaticsFlag(List<Long> idList) {
        LambdaUpdateWrapper<Algorithm> updateWrapper = new LambdaUpdateWrapper();
        updateWrapper.set(Algorithm::getStaticsFlag, 0);
        updateWrapper.eq(Algorithm::getStaticsFlag, 1);
        this.getBaseMapper().update(null, updateWrapper);

        //
        for(Long id : idList) {
            Algorithm algorithm = new Algorithm();
            algorithm.setId(id);
            algorithm.setStaticsFlag(1);
            this.updateById(algorithm);
        }
    }

    /**
     * 查询已使用的算法列表
     *
     * @return
     */
    @Override
    public List<Algorithm> listUsed() {
        return this.getBaseMapper().selectUsed();
    }

    /**
     * 查询NameEn
     *
     * @return
     */
    @Override
    public List<Algorithm> listNameEn(String nameEn) {
        return this.getBaseMapper().selectNameEn(nameEn);
    }
}