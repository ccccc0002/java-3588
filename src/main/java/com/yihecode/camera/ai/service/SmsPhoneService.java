package com.yihecode.camera.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yihecode.camera.ai.entity.SmsPhone;

/**
 * 短信推送手机号码
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public interface SmsPhoneService extends IService<SmsPhone> {

    /**
     * 查询手机号码，用逗号分割
     * @return
     */
    String listPhoneStr(String test);

    /**
     * 清除缓存
     */
    void evictPhoneStr(String test);
}