package com.yihecode.camera.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yihecode.camera.ai.entity.Algorithm;

import java.util.List;
import java.util.Map;

/**
 * 算法管理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public interface AlgorithmService extends IService<Algorithm> {

    /**
     *
     * @return
     */
    Map<Long, String> toMap();

    /**
     * 更新统计标识
     * @param idList
     */
    void updateStaticsFlag(List<Long> idList);

    /**
     * 查询已使用的算法列表
     * @return
     */
    List<Algorithm> listUsed();

    /**
     * 查询NameEn
     * @return
     */
    List<Algorithm> listNameEn(String nameEn);
}
