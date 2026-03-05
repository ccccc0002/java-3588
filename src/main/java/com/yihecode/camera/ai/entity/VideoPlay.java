package com.yihecode.camera.ai.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 描述：摄像头播放控制实体
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Data
@TableName("tbl_biz_video_play")
public class VideoPlay {

    private Long id;

    /**
     * 摄像头id
     */
    @TableField("camera_id")
    private Long cameraId;

    /**
     * 占据视频端口
     */
    @TableField("video_port")
    private Integer videoPort;

    /**
     * 最后更新时间
     */
    @TableField("last_time")
    private Long lastTime;
}
