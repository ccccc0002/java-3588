package com.yihecode.camera.ai.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 告警管理实体
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Data
@TableName("tbl_biz_report")
public class Report {

    private Long id;

    @TableField("camera_id")
    private Long cameraId;

    @TableField("algorithm_id")
    private Long algorithmId;

    @TableField("file_name")
    private String fileName;

    @TableField("params")
    private String params;

    @TableField("type")
    private Integer type;

    @TableField("display")
    private Integer display;

    @TableField("created_at")
    private Date createdAt;

    @TableField("created_mills")
    private Long createdMills;

    @TableField("audit_state")
    private Integer auditState;

    @TableField("audit_result")
    private Integer auditResult;

    @TableField(exist = false)
    private String createdStr;

    @TableField(exist = false)
    private String cameraName;

    @TableField(exist = false)
    private String algorithmName;

    @TableField(exist = false)
    private String typeName;

    @TableField(exist = false)
    private String conf;

}
