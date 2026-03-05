package com.yihecode.camera.ai.utils;

import cn.hutool.core.util.StrUtil;

/**
 * 描述：字符串工具类
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public class StrUtils {

    /**
     * 隐藏字符串
     * @return
     */
    public static String hide(String source) {
        if(StrUtil.isBlank(source)) {
            return "";
        }

        //
        int len = source.length();
        if(len == 1 || len == 2) {
            return "**";
        }
        //
        if(len == 3) {
            return source.substring(0, 1) + "**";
        }
        //
        if(len <= 5) {
            return source.substring(0, 1) + "**" + source.substring(len - 2);
        }
        //
        if(len < 9) {
            return source.substring(0, 2) + "***" + source.substring(len - 2);
        }
        //
        if(len >= 9) {
            return source.substring(0, 3) + "****" + source.substring(len - 3);
        }

        return "******";
    }
}
