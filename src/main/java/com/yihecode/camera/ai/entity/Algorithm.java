package com.yihecode.camera.ai.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 算法管理实体
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Data
@TableName("tbl_biz_algorithm")
public class Algorithm {

    private Long id;

    @TableField("name")
    private String name;

    @TableField("name_en")
    private String nameEn;

    @TableField("model_path")
    private String modelPath;


    @TableField("frequency")
    private Integer frequency;

    @TableField("interval_time")
    private Integer intervalTime;

    @TableField("params")
    private String params;

    @TableField("created_at")
    private Date createdAt;

    @TableField("updated_at")
    private Date updatedAt;

    @TableField("file_name")
    private String fileName;

    @TableField("file_width")
    private Integer fileWidth;

    @TableField("file_height")
    private Integer fileHeight;

    @TableField("canvas_height")
    private Integer canvasHeight;

    @TableField("canvas_width")
    private Integer canvasWidth;

    @TableField("scale_ratio")
    private Float scaleRatio;

    /**
     * 关联统计配置显示
     */
    @TableField("statics_flag")
    private Integer staticsFlag;

    /**
     * 关联统计配置显示，转为checked或者空字符串
     */
    @TableField(exist = false)
    private String staticsFlagVal;
}

