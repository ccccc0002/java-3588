package com.yihecode.camera.ai.enums;

/**
 * 摄像头动作枚举类型， 对接算法对摄像头调用进行启动/停止/删除等
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public enum CameraAction {
    ACTION_NULL(0, "不操作"),
    ACTION_UPD(1, "更新摄像头"),
    ACTION_DEL(2, "删除摄像头");

    private Integer type;

    private String text;

    CameraAction(Integer type, String text) {
        this.type = type;
        this.text = text;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
