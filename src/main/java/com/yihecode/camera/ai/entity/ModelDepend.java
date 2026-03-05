package com.yihecode.camera.ai.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 算法模型依赖管理
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Data
@TableName("tbl_biz_model_depend")
public class ModelDepend {

    /**
     * 主键
     */
    private Long id;

    /**
     * 模型id
     */
    @TableField("model_id")
    private Long modelId;

    /**
     * 依赖模型id
     */
    @TableField("depend_model_id")
    private Long dependModelId;

}

