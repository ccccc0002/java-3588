package com.yihecode.camera.ai.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 算法模型管理实体
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Data
@TableName("tbl_biz_model")
public class Model {

    /**
     * 主键
     */
    private Long id;

    /**
     * 模型名称
     */
    @TableField("name")
    private String name;

    /**
     * 模型类型
     */
    @TableField("type")
    private Integer type;

    /**
     * 模型描述
     */
    @TableField("description")
    private String description;

    /**
     * 模型业务标签
     */
    @TableField("class_biz")
    private String classBiz;

    /**
     * 模型保留标签
     */
    @TableField("class_all")
    private String classAll;

    /**
     * 输入参数
     */
    @TableField("input_param")
    private String inputParam;

    /**
     * 输出参数
     */
    @TableField("output_param")
    private String outputParam;

    /**
     * 图片宽度
     */
    @TableField("img_width")
    private Integer imgWidth;

    /**
     * 图片高度
     */
    @TableField("img_height")
    private Integer imgHeight;

    /**
     * 模型文件名称
     */
    @TableField("onnx_name")
    private String onnxName;

    /**
     * 模型文件tag
     */
    @TableField("onnx_tag")
    private String onnxTag;

    /**
     * 模型文件MD5 (文件名称+文件大小)
     */
    @TableField("onnx_md5")
    private String onnxMd5;

    /**
     * 调用地址
     */
    @TableField("call_url")
    private String callUrl;

    /**
     * 模型状态
     */
    @TableField("state")
    private Integer state;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private Date createdAt;

    /**
     * 模型大小
     */
    @TableField("onnx_size")
    private Long onnxSize;

    /**
     * 版本数量
     */
    @TableField("version_count")
    private Integer versionCount;

    /**
     * 类型名称
     */
    @TableField(exist = false)
    private String typeName;

    /**
     * 文件大小
     */
    @TableField(exist = false)
    private String fileSize;

    /**
     * 模型Ids - 前端参数
     */
    @TableField(exist = false)
    private List<Long> modelIds;


}

