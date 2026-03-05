package com.yihecode.camera.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yihecode.camera.ai.entity.Algorithm;

import java.util.List;

/**
 * 算法管理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public interface AlgorithmMapper extends BaseMapper<Algorithm> {
    /**
     * 查询已nameEn
     * @return
     */
    List<Algorithm> selectNameEn(String nameEn);
    /**
     * 查询已使用的算法列表
     * @return
     */
    List<Algorithm> selectUsed();

}