package com.yihecode.camera.ai.vo;

import lombok.Data;

import java.util.List;

/**
 * 树形结构 - jstree要求的树形结构
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Data
public class TreeVo {

    private String meId;

    private String text;

    private String icon;

    private String parent;

    private List<TreeVo> children;
}
