package com.yihecode.camera.ai.enums;

/**
 * 摄像头运行状态枚举类型
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public enum CameraRunningState {

    CLOSED(0, "关闭"),
    RUNNING(1, "运行");

    private Integer type;
    private String text;

    CameraRunningState(Integer type, String text) {
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

    public static String getText(Integer type) {
        CameraRunningState[] runningStates = values();
        for (CameraRunningState runningState : runningStates) {
            if (runningState.getType() == type) {
                return runningState.getText();
            }
        }
        return "Unknow";
    }

}
