package com.yihecode.camera.ai.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 描述：摄像头区域节点<树形结构>实体, 适配新版摄像头管理
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Data
@TableName("tbl_biz_location")
public class Location {

    /**
     * 主键
     */
    private Long id;

    /**
     * 位置名称
     */
    @TableField("name")
    private String name;

    /**
     * 排序值
     */
    @TableField("sort")
    private Integer sort;

    /**
     * 上级位置节点
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 上级位置名称, 从根节点到上级节点
     */
    @TableField("parent_names")
    private String parentNames;

    /**
     * 上级区域ids
     */
    private String parentIds;

    /**
     * 纬度
     */
    @TableField("latitude")
    private Float latitude;

    /**
     * 经度
     */
    @TableField("longitude")
    private Float longitude;
}
