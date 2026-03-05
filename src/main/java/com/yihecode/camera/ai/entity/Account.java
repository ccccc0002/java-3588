package com.yihecode.camera.ai.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 账号管理实体 - 前期简单处理
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@ApiModel(value = "账号管理实体")
@Data
@TableName("tbl_biz_account")
public class Account {

    @ApiModelProperty(value = "id")
    private Long id;

    @ApiModelProperty(value = "登录账号")
    @TableField("account")
    private String account;

    @ApiModelProperty(value = "登录密码")
    @TableField("password")
    private String password;

    @ApiModelProperty(value = "账号名称")
    @TableField("name")
    private String name;

    @ApiModelProperty(value = "状态", notes = "0-正常 1-禁用")
    @TableField("state")
    private Integer state;

    @ApiModelProperty(value = "创建时间")
    @TableField("created_at")
    private Date createdAt;

    @ApiModelProperty(value = "更新时间")
    @TableField("updated_at")
    private Date updatedAt;

}