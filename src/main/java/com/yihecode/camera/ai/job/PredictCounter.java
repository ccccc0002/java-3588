package com.yihecode.camera.ai.job;

import java.util.HashMap;
import java.util.Map;

/**
 * 描述：算法调用计数器
 * <p>
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public class PredictCounter {

    /**
     * 计数，camera_id -> 调用次数
     */
    private final Map<Long, Integer> counter = new HashMap<>();

    /**
     * 单例
     */
    private static final PredictCounter INST = new PredictCounter();

    /**
     * 返回实例
     * @return
     */
    public static PredictCounter getInst() {
        return INST;
    }

    /**
     * 获取count值
     * @param cameraId
     * @return
     */
    public int getCount(Long cameraId) {
        if(cameraId == null) {
            return 1;
        }

        //
        Integer count = counter.get(cameraId);
        if(count == null) {
            count = 1;
            counter.put(cameraId, 1);
            return count;
        } else {
            count = count + 1;
            counter.put(cameraId, count);
            return count;
        }
    }
}
