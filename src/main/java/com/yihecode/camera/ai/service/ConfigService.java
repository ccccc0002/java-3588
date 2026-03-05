package com.yihecode.camera.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yihecode.camera.ai.entity.Config;

/**
 * 系统配置管理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public interface ConfigService extends IService<Config> {

    /**
     *
     * @param tag
     * @return
     */
    String getByValTag(String tag);

    /**
     * 清除缓存
     * @param tag
     */
    void evictByTag(String tag);
}