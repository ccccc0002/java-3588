package com.yihecode.camera.ai.web.api;

import cn.hutool.crypto.SecureUtil;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警丢弃管理，对同一告警重复推送进行管控过滤
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public class ReportDiscard {

    /**
     * private inst
     */
    private static final ReportDiscard INST = new ReportDiscard();

    /**
     * key: camera_id#algorithm_id
     * value: params的MD5值
     */
    private final ConcurrentHashMap<String, String> md5Map = new ConcurrentHashMap();

    /**
     * key: camera_id#algorithm_id
     * value: 最后记录的时间戳, 当超过1小时，则删除该值
     */
    private final ConcurrentHashMap<String, Long> timeMap = new ConcurrentHashMap<>();

    /**
     * key: camera_id#algorithm_id
     * value: 次数累计
     */
    private final ConcurrentHashMap<String, Integer> countMap = new ConcurrentHashMap<>();

    /**
     * private
     */
    private ReportDiscard() {}

    /**
     * get inst
     * @return
     */
    public static ReportDiscard getInst() {
        return INST;
    }

    /**
     * 是否过滤该告警信息
     * @param cameraId
     * @param algorithmId
     * @param params
     * @return
     */
    public boolean isFilter(Long cameraId, Long algorithmId, String params) {
        String key = cameraId + "#" + algorithmId;
        String md5 = SecureUtil.md5(params);
        if(!md5Map.containsKey(key)) { // 还没有记录过，不过滤
            md5Map.put(key, md5);
            timeMap.put(key, System.currentTimeMillis());
            countMap.put(key, 1);
            return false;
        }

        String oldMd5 = md5Map.get(key);
        if(md5.equals(oldMd5)) { // 已经记录过同一值
            Integer count = countMap.get(key);
            if(count == null) {
                count = 1;
            }

            //
            if(count > 2) { // 超过2次，过滤掉
                return true;
            }
            countMap.put(key, count + 1);
            timeMap.put(key, System.currentTimeMillis());
            return false;
        } else {
            md5Map.put(key, md5);
            timeMap.put(key, System.currentTimeMillis());
            countMap.put(key, 1);
            return false;
        }
    }

    /**
     * 删除超过1小时的记录
     */
    public void remove() {
        long current = System.currentTimeMillis();
        for(Iterator<Map.Entry<String, Long>> it = timeMap.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, Long> item = it.next();
            String key = item.getKey();
            Long errorTime = item.getValue(); // 记录错误的时间点

            if((current - errorTime) > 60 * 60 * 1000) { // 超过5分钟，清除
                it.remove();

                //
                md5Map.remove(key);
                countMap.remove(key);
            }
        }
    }
}
