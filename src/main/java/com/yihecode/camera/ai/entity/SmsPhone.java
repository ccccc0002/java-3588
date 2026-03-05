package com.yihecode.camera.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 描述：短信推送手机号实体
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Data
@TableName("tbl_biz_sms_phone")
public class SmsPhone {

    /**
     * 主键id
     */
    private Long id;

    /**
     * 手机号码
     */
    private String phone;
}
