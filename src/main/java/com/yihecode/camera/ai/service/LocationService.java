package com.yihecode.camera.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yihecode.camera.ai.entity.Location;
import com.yihecode.camera.ai.exception.BizException;

import java.util.List;

/**
 * 摄像头区域节点<树形结构>表
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public interface LocationService extends IService<Location> {

    /**
     * 新增区域
     * @param location
     */
    void saveNode(Location location) throws BizException;

    /**
     * 删除区域
     * @param id
     */
    void deleteNodes(Long id);

    /**
     * 查询数据
     * @return
     */
    List<Location> listData();
}