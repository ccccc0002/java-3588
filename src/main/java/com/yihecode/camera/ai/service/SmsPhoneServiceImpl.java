package com.yihecode.camera.ai.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yihecode.camera.ai.entity.SmsPhone;
import com.yihecode.camera.ai.mapper.SmsPhoneMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 短信推送手机号码
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Service
public class SmsPhoneServiceImpl extends ServiceImpl<SmsPhoneMapper, SmsPhone> implements SmsPhoneService {

    /**
     * 查询手机号码，用逗号分割
     *
     * @return
     */
    @Override
    @Cacheable(cacheNames = "phones", key = "#test")
    public String listPhoneStr(String test) {
        List<SmsPhone> smsPhoneList = this.list();
        if(smsPhoneList == null) {
            return "";
        }

        //
        List<String> phones = new ArrayList<>();
        for(SmsPhone smsPhone : smsPhoneList) {
            phones.add(smsPhone.getPhone());
        }
        return String.join(",", phones);
    }

    /**
     * 清除缓存
     */
    @Override
    @CacheEvict(cacheNames = "phones", key = "#test")
    public void evictPhoneStr(String test) {
        listPhoneStr("test");
    }
}