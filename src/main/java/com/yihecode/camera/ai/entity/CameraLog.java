package com.yihecode.camera.ai.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 摄像头取图日志（海康安防平台专用,适配中化需求）
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Data
@TableName("tbl_biz_camera_log")
public class CameraLog {

    private Long id;

    @TableField("index_code")
    private String indexCdoe;

    @TableField("camera_id")
    private Long cameraId;

    @TableField("type")
    private Integer type;

    @TableField("params")
    private String params;

    @TableField("result")
    private String result;

    @TableField("url")
    private String url;

    @TableField("code")
    private String code;

    @TableField("created_at")
    private Date createdAt;

    @TableField("camera_name")
    private String cameraName;

}