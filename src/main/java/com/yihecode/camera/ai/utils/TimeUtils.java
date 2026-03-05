package com.yihecode.camera.ai.utils;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

/**
 * 时间格式
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public class TimeUtils {

    public static String toYmd(String str) {
        if(StrUtil.isBlank(str)) {
            return "";
        }

        return str.replaceAll("-", "");
    }

    public static Date toTime(String str) {
        if(str == null) {
            return null;
        }

        //
        try {
            Date date = DateUtil.parse(str, "yyyy-MM-dd");

            //
            Calendar calendar1 = Calendar.getInstance();
            calendar1.setTime(date);

            //
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.YEAR, calendar1.get(Calendar.YEAR));
            calendar.set(Calendar.MONTH, calendar1.get(Calendar.MONTH));
            calendar.set(Calendar.DAY_OF_MONTH, calendar1.get(Calendar.DAY_OF_MONTH));

            return calendar.getTime();
        } catch (Exception e) {}

        return null;
    }

    public static String day7() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, -8);
        return DateUtil.format(calendar.getTime(), "yyyyMMdd");
    }

    public static String day() {
        return DateUtil.format(new Date(), "yyyy-MM-dd");
    }

    public static String day15() {
        return DateUtil.format(DateUtil.offsetDay(new Date(), -16), "yyyyMMdd");
    }

    public static String month3() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -3);
        return DateUtil.format(calendar.getTime(), "yyyyMMdd");
    }

    public static String month6() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -6);
        return DateUtil.format(calendar.getTime(), "yyyyMMdd");
    }

    public static String getFormatedDateString(float timeZoneOffset) {
        if (timeZoneOffset > 13 || timeZoneOffset < -12) {
            timeZoneOffset = 0;
        }

        int newTime = (int) (timeZoneOffset * 60 * 60 * 1000);
        TimeZone timeZone;
        String[] ids = TimeZone.getAvailableIDs(newTime);
        if (ids.length == 0) {
            timeZone = TimeZone.getDefault();
        } else {
            timeZone = new SimpleTimeZone(newTime, ids[0]);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        sdf.setTimeZone(timeZone);
        return sdf.format(new Date());
    }
}
