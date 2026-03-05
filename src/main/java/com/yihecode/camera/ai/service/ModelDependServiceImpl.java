package com.yihecode.camera.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yihecode.camera.ai.entity.ModelDepend;
import com.yihecode.camera.ai.mapper.ModelDependMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型依赖管理
 */
@Service
public class ModelDependServiceImpl extends ServiceImpl<ModelDependMapper, ModelDepend> implements ModelDependService {

    /**
     * 根据模型id删除
     *
     * @param modelId
     */
    @Override
    public void removeByModel(Long modelId) {
        LambdaQueryWrapper<ModelDepend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ModelDepend::getModelId, modelId);
        this.remove(queryWrapper);
    }

    /**
     * 根据模型id查询
     *
     * @param modelId
     * @return
     */
    @Override
    public List<ModelDepend> listByModel(Long modelId) {
        LambdaQueryWrapper<ModelDepend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ModelDepend::getModelId, modelId);
        //
        List<ModelDepend> modelDependList = this.list(queryWrapper);
        if(modelDependList == null) {
            return new ArrayList<>();
        }
        return modelDependList;
    }
}