package com.yihecode.camera.ai.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * 分页JSON结果集包装
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public class PageResultUtils {

    public static PageResult success(Long total, List data) {
        PageResult result = new PageResult();
        result.setCode(0);
        result.setMsg("OK");
        result.setCount(total);
        result.setData(data == null ? new ArrayList() : data);
        return result;
    }

    /**
     * 失败
     * @return
     */
    public static PageResult fail() {
        PageResult result = new PageResult();
        result.setCode(500);
        result.setMsg("系统繁忙，请稍后重试");
        result.setCount(0L);
        return result;
    }

    /**
     * 失败
     * @return
     */
    public static PageResult fail(String msg) {
        PageResult result = new PageResult();
        result.setCode(500);
        result.setCount(0L);
        result.setMsg(msg);
        return result;
    }

    /**
     * 失败
     * @return
     */
    public static PageResult fail(Integer code, String msg) {
        PageResult result = new PageResult();
        result.setCode(code);
        result.setMsg(msg);
        result.setCount(0L);
        return result;
    }
}
