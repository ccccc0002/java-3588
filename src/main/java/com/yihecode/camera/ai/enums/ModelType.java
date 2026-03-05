package com.yihecode.camera.ai.enums;

/**
 * 模型分类枚举类型
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public enum ModelType {
    OBJECT_DETECH(1, "目标检测"),
    CLASS_TASK(2, "分类任务"),
    SPLIT_TASK(3, "分割任务");

    private Integer type;

    private String text;

    ModelType(Integer type, String text) {
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
        ModelType[] modelTypes = values();
        for (ModelType modelType : modelTypes) {
            if (modelType.getType() == type) {
                return modelType.getText();
            }
        }
        return "未知";
    }
}
