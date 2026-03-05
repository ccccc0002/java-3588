package com.yihecode.camera.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yihecode.camera.ai.entity.CameraLog;

/**
 * 摄像头取图日志管理，适配中化定制需求
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public interface CameraLogService extends IService<CameraLog> {

    /**
     *
     * @param pageObj
     * @param indexCode
     * @return
     */
    IPage<CameraLog> listPage(IPage<CameraLog> pageObj, String indexCode);
}