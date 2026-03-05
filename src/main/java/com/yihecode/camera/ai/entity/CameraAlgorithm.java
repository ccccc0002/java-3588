package com.yihecode.camera.ai.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 摄像头与算法关联实体
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Data
@TableName("tbl_biz_camera_algorithm")
public class CameraAlgorithm {
    private Long id;

    @TableField("camera_id")
    private Long cameraId;

    @TableField("algorithm_id")
    private Long algorithmId;

    @TableField("confidence")
    private Float confidence;

    @TableField("mark_points")
    private String markPoints;

    @TableField("image_points")
    private String imagePoints;

}
