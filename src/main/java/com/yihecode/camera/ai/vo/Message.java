package com.yihecode.camera.ai.vo;

import lombok.Data;

/**
 * 首页告警推送socket 消息体
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Data
public class Message {

    private String type;

    private String content;

    private Object data;
}
