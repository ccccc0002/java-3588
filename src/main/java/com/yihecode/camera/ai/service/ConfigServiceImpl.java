package com.yihecode.camera.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yihecode.camera.ai.entity.Config;
import com.yihecode.camera.ai.mapper.ConfigMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 系统配置管理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Service
public class ConfigServiceImpl extends ServiceImpl<ConfigMapper, Config> implements ConfigService {

    /**
     *
     * @param tag
     * @return
     */
    @Override
    @Cacheable(value = "configs", key = "#tag")
    public String getByValTag(String tag) {
        LambdaQueryWrapper<Config> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Config::getTag, tag);

        //
        Config config = this.getOne(queryWrapper, false);
        if(config == null || config.getVal() == null) {
            return "";
        }
        return config.getVal();
    }

    /**
     * 清除缓存
     *
     * @param tag
     */
    @Override
    @CacheEvict(value = "configs", key = "#tag")
    public void evictByTag(String tag) {
        //
    }
}