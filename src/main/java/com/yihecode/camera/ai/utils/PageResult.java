package com.yihecode.camera.ai.utils;

import lombok.Data;

import java.util.List;

/**
 * 分页JSON结果
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Data
public class PageResult {

    /**
     * 状态码
     */
    private Integer code;

    /**
     * 消息
     */
    private String msg;

    /**
     * 总数量
     */
    private Long count;

    /**
     * 数据
     */
    private List data;
}
