package com.yihecode.camera.ai.utils;

import cn.hutool.core.util.StrUtil;

import java.math.BigDecimal;

/**
 * 数字格式化处理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public class NumUtils {

    public static String fmt(String str) {
        if(StrUtil.isBlank(str)) {
            return "0.00";
        }

        boolean hasDot = str.contains(".");
        if(hasDot) {
            String[] arr = str.split("\\.");
            String p = arr[0];
            if(StrUtil.isBlank(p)) {
                p = "0";
            }

            String s = arr[1];
            if(s.length() == 1) {
                return p + "." + s + "0";
            } else if(s.length() == 2) {
                return str;
            } else {
                return p + "." + s.substring(0, 2);
            }
        } else {
            return str + ".00";
        }
    }

    public static Double subtract(Double ...vals) {
        if(vals == null || vals.length < 2) {
            return 0d;
        }

        //
        BigDecimal prev = null;

        //
        int len = vals.length;
        for(int i = 0; i < len; i++) {
            if(i == 0) {
                prev = new BigDecimal(String.valueOf(vals[0]));
                continue;
            }
            //
            BigDecimal next = new BigDecimal(String.valueOf(vals[i]));
            //
            prev = prev.subtract(next);
        }
        return prev.doubleValue();
    }

    public static String format(Long v1, Long v2) {
        if(v2 == 0) {
            return "100.0%";
        }

        BigDecimal b1 = new BigDecimal(v1);
        BigDecimal b2 = new BigDecimal(v2);

        double v = b1.divide(b2, 2, BigDecimal.ROUND_HALF_DOWN).doubleValue() * 100;
        return v + "%";
    }
}
