package com.yihecode.camera.ai.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 摄像头实体
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Data
@TableName("tbl_biz_camera")
public class Camera {

    private Long id;

    @TableField("name")
    private String name;

    @TableField("rtsp_url")
    private String rtspUrl;

    @TableField("action")
    private Integer action;

    @TableField("running")
    private Integer running;

    @TableField("state")
    private Integer state;

    @TableField("created_at")
    private Date createdAt;

    @TableField("updated_at")
    private Date updatedAt;

    @TableField("frequency")
    private Integer frequency;

    @TableField("interval_time")
    private Float intervalTime;

    @TableField("file_name")
    private String fileName;

    @TableField("file_width")
    private Integer fileWidth;

    @TableField("file_height")
    private Integer fileHeight;

    @TableField("canvas_width")
    private Integer canvasWidth;

    @TableField("canvas_height")
    private Integer canvasHeight;

    @TableField("params")
    private String params;

    @TableField("api_params")
    private String apiParams;

    @TableField("scale_ratio")
    private Float scaleRatio;

    @TableField("warehouse_id")
    private Long wareHouseId;

    @TableField("rtsp_type")
    private Integer rtspType;

    @TableField("location_id")
    private Long locationId;

    @TableField("location_ids")
    private String locationIds;

    @TableField("video_play")
    private Integer videoPlay;

    @TableField(exist = false)
    private String algorithmNames;

    @TableField(exist = false)
    private List<Algorithm> algorithms;

}
