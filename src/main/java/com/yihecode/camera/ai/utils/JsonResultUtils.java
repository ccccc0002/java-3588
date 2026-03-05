package com.yihecode.camera.ai.utils;

/**
 * JSON结果集包装
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public class JsonResultUtils {

    /**
     * 成功
     * @return
     */
    public static JsonResult success() {
        JsonResult result = new JsonResult();
        result.setCode(0);
        result.setMsg("OK");
        return result;
    }

    /**
     * 成功
     * @return
     */
    public static JsonResult success(Object data) {
        JsonResult result = new JsonResult();
        result.setCode(0);
        result.setMsg("OK");
        result.setData(data);
        return result;
    }

    /**
     * 成功
     * @return
     */
    public static JsonResult successMsg(String msg) {
        JsonResult result = new JsonResult();
        result.setCode(0);
        result.setMsg(msg);
        return result;
    }

    /**
     * 失败
     * @return
     */
    public static JsonResult fail() {
        JsonResult result = new JsonResult();
        result.setCode(500);
        result.setMsg("系统繁忙，请稍后重试");
        return result;
    }

    /**
     * 失败
     * @return
     */
    public static JsonResult fail(String msg) {
        JsonResult result = new JsonResult();
        result.setCode(500);
        result.setMsg(msg);
        return result;
    }

    /**
     * 失败
     * @return
     */
    public static JsonResult fail(Integer code, String msg) {
        JsonResult result = new JsonResult();
        result.setCode(code);
        result.setMsg(msg);
        return result;
    }

    /**
     * 失败
     * @return
     */
    public static JsonResult fail(String msg, Object data) {
        JsonResult result = new JsonResult();
        result.setCode(500);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }
}
