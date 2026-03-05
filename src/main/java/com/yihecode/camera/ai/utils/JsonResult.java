package com.yihecode.camera.ai.utils;

import lombok.Data;

/**
 * JSON结果
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Data
public class JsonResult {

    /**
     * 状态码
     */
    private Integer code;

    /**
     * 消息
     */
    private String msg;

    /**
     * 数据
     */
    private Object data;
}
