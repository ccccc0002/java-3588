package com.yihecode.camera.ai.enums;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警类型枚举类型
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public enum ReportType {

    AI(0, "监控告警"),
    STREAM(1, "视频流告警");

    private Integer type;
    private String text;

    ReportType(Integer type, String text) {
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

    /**
     * get text
     * @param type
     * @return
     */
    public static String getText(Integer type) {
        ReportType[] reportTypes = values();
        for (ReportType reportType : reportTypes) {
            if (reportType.getType() == type) {
                return reportType.getText();
            }
        }
        return "Unknow";
    }

    /**
     * to list
     * @return
     */
    public static List<Map<String, Object>> toList() {
        List<Map<String, Object>> dataList = new ArrayList<>();
        ReportType[] values = values();
        for (ReportType reportType : values) {
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("id", reportType.type);
            dataMap.put("name", reportType.text);
            dataList.add(dataMap);
        }
        return dataList;
    }
}