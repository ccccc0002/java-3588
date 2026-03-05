package com.yihecode.camera.ai.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 告警时段配置实体
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Data
@TableName("tbl_biz_report_period")
public class ReportPeriod {

    private Long id;

    @TableField("camera_id")
    private Long cameraId;

    @TableField("algorithm_id")
    private Long algorithmId;

    @TableField("start_time")
    private Integer startTime;

    @TableField("end_time")
    private Integer endTime;

    @TableField("start_text")
    private String startText;

    @TableField("end_text")
    private String endText;

}
