package com.yihecode.camera.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yihecode.camera.ai.entity.VideoPlay;

import java.util.List;

/**
 * 视频播放控制, 算法推流控制
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public interface VideoPlayService extends IService<VideoPlay> {

    /**
     * 查询已使用端口
     * @return
     */
    List<Integer> listUsePort();

    /**
     * 根据摄像头id删除
     * @param cameraId
     */
    void removeByCamera(Long cameraId);

    /**
     * 根据摄像头id查询
     * @param cameraId
     * @return
     */
    VideoPlay getByCamera(Long cameraId);

    /**
     * 更新最后播放时间
     * @param cameraId
     */
    void updateLastTime(Long cameraId);

    /**
     * 根据端口号查询
     * @param videoPort
     * @return
     */
    VideoPlay getByPort(Integer videoPort);

    /**
     * 删除所有配置
     */
    void removeAll();
}