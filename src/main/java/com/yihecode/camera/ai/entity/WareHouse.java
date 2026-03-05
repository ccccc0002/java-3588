package com.yihecode.camera.ai.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 仓库实体, 对接海康安防管理系统，适配中华需求
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Data
@TableName("tbl_biz_warehouse")
public class WareHouse {

    private Long id;

    @TableField("index_code")
    private String indexCode;

    @TableField("name")
    private String name;

    @TableField("parent_index_code")
    private String parentIndexCode;

    @TableField("tree_code")
    private String treeCode;

    // 节点层次 1,2,3,4
    @TableField("tree_level")
    private Integer treeLevel;

    // 状态 0-正常 1-异常，重新同步时，将状态全部设为异常，更新时修改为正常，然后删除掉所有异常数据
    @TableField("status")
    private Integer status;

    @TableField("rtsp_url")
    private String rtspUrl;

    // 拉取视频地址状态 0-正常 1-异常
    @TableField("pull_status")
    private Integer pullStatus;

    // 最后拉取时间
    @TableField("pull_time")
    private Date pullTime;

    // 树节点类型 0-目录 1-摄像头
    @TableField("tree_type")
    private Integer treeType;

    @TableField(exist = false)
    private List<WareHouse> children;

}
