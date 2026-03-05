package com.yihecode.camera.ai.vo;

import lombok.Data;

/**
 * 描述：摄像头告警描框
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Data
public class ReportMessage {

    /**
     * 类型 HEART | REPORT
     */
    private String type;

    /**
     * 摄像头id
     */
    private String cameraId;

    private String algorithmId;

    /**
     * 描框坐标
     */
    private String params;

    private String cameraName;

    private String algorithmName;

    private String alarmTime;

    private String wareName;

    private String id;

    private String webUrl;
}
