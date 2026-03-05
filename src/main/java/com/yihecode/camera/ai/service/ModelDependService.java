package com.yihecode.camera.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yihecode.camera.ai.entity.ModelDepend;

import java.util.List;

/**
 * 模型依赖管理
 */
public interface ModelDependService extends IService<ModelDepend> {

    /**
     * 根据模型id删除
     * @param modelId
     */
    void removeByModel(Long modelId);

    /**
     * 根据模型id查询
     * @param modelId
     * @return
     */
    List<ModelDepend> listByModel(Long modelId);
}