package com.yihecode.camera.ai.enums;

/**
 * websocket 消息类型
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public enum MessageType {

    HEART("HEART", "心跳消息"),
    STREAM("STREAM", "视频流消息"),
    REPORT("REPORT", "告警消息");

    private String type;

    private String content;

    MessageType(String type, String content) {
        this.type = type;
        this.content = content;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
