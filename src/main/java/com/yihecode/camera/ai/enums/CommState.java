package com.yihecode.camera.ai.enums;

/**
 * 通用枚举类型状态
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public enum CommState {
    NORMAL(0, "正常"),
    DISABLED(1, "禁用");

    private Integer type;
    private String text;

    CommState(Integer type, String text) {
        this.type = type;
        this.text = text;
    }

    public Integer getType() {
        return this.type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getText() {
        return this.text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public static String getText(Integer type) {
        CommState[] accountStates = values();
        for (CommState accountState : accountStates) {
            if (accountState.getType() == type) {
                return accountState.getText();
            }
        }
        return "Unknow";
    }

}
