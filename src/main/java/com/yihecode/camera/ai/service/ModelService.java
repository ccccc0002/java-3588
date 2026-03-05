package com.yihecode.camera.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yihecode.camera.ai.entity.Model;

import java.util.List;
import java.util.Map;

/**
 * 模型管理
 */
public interface ModelService extends IService<Model> {

    /**
     * 根据文件md5值查询
     * @param md5
     * @return
     */
    Model getByOnnxMd5(String md5);

    /**
     * 根据文件名称查询
     * @param fileName
     * @return
     */
    Model getByOnnxName(String fileName);

    /**
     * 分页查询
     * @param pageObj
     * @return
     */
    IPage<Model> listPage(IPage<Model> pageObj);

    /**
     * 查询数据列表
     * @return
     */
    List<Model> listData();

    /**
     * 根据模型名称查询激活数量
     * @param name
     * @return
     */
    int getActiveCountByName(String name);

    /**
     * 根据模型名称查询版本数量
     * @param name
     * @return
     */
    int getVersionCountByName(String name);

    /**
     * 更新版本数量
     * @param name
     * @param newVersionCount
     */
    void updateVersionCount(String name, int newVersionCount);

    /**
     * 保存模型
     * @param model
     * @throws Exception
     */
    Map<String, Object> saveModel(Model model) throws Exception;

    /**
     * 模型启用
     * @param modelId
     */
    void updateModelEnable(Long modelId) throws Exception;

    /**
     * 查询模型版本
     * @param modelId
     * @return
     */
    List<Model> listVersion(Long modelId);
}