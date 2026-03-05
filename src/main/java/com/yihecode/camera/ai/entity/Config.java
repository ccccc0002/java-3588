package com.yihecode.camera.ai.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 配置管理实体
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Data
@TableName("tbl_biz_config")
public class Config {

    private Long id;

    @TableField("name")
    private String name;

    @TableField("tag")
    private String tag;

    @TableField("val")
    private String val;

}