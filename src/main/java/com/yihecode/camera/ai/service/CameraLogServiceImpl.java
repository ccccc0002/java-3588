package com.yihecode.camera.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yihecode.camera.ai.entity.CameraLog;
import com.yihecode.camera.ai.mapper.CameraLogMapper;
import org.springframework.stereotype.Service;

/**
 * 摄像头取图日志管理，适配中化定制需求
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Service
public class CameraLogServiceImpl extends ServiceImpl<CameraLogMapper, CameraLog> implements CameraLogService {

    /**
     * @param pageObj
     * @param indexCode
     * @return
     */
    @Override
    public IPage<CameraLog> listPage(IPage<CameraLog> pageObj, String indexCode) {
        LambdaQueryWrapper<CameraLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CameraLog::getIndexCdoe, indexCode);
        queryWrapper.orderByDesc(CameraLog::getCreatedAt);
        return this.page(pageObj, queryWrapper);
    }
}