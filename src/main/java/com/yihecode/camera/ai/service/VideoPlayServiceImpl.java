package com.yihecode.camera.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yihecode.camera.ai.entity.VideoPlay;
import com.yihecode.camera.ai.mapper.VideoPlayMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 视频播放控制, 算法推流控制
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Service
public class VideoPlayServiceImpl extends ServiceImpl<VideoPlayMapper, VideoPlay> implements VideoPlayService {

    /**
     * 查询已使用端口
     *
     * @return
     */
    @Override
    public List<Integer> listUsePort() {
        List<Integer> usePorts = new ArrayList<>();
        List<VideoPlay> videoPlays = this.list();
        if(videoPlays == null || videoPlays.isEmpty()) {
            return usePorts;
        }
        //
        for(VideoPlay videoPlay : videoPlays) {
            usePorts.add(videoPlay.getVideoPort());
        }
        return usePorts;
    }

    /**
     * 根据摄像头id删除
     *
     * @param cameraId
     */
    @Override
    public void removeByCamera(Long cameraId) {
        LambdaQueryWrapper<VideoPlay> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(VideoPlay::getCameraId, cameraId);
        this.remove(queryWrapper);
    }

    /**
     * 根据摄像头id查询
     *
     * @param cameraId
     * @return
     */
    @Override
    public VideoPlay getByCamera(Long cameraId) {
        LambdaQueryWrapper<VideoPlay> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(VideoPlay::getCameraId, cameraId);
        return this.getOne(queryWrapper);
    }

    /**
     * 更新最后播放时间
     *
     * @param cameraId
     */
    @Override
    public void updateLastTime(Long cameraId) {
        LambdaUpdateWrapper<VideoPlay> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(VideoPlay::getLastTime, System.currentTimeMillis());
        updateWrapper.eq(VideoPlay::getCameraId, cameraId);
        this.update(null, updateWrapper);
    }

    /**
     * 根据端口号查询
     *
     * @param videoPort
     * @return
     */
    @Override
    public VideoPlay getByPort(Integer videoPort) {
        LambdaQueryWrapper<VideoPlay> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(VideoPlay::getVideoPort, videoPort);
        return this.getOne(queryWrapper);
    }

    /**
     * 删除所有配置, 系统启动执行一次
     */
    @Override
    public void removeAll() {
        List<VideoPlay> videoPlayList = this.list();
        if(videoPlayList == null) {
            return ;
        }
        //
        for(VideoPlay videoPlay : videoPlayList) {
            this.removeById(videoPlay.getId());
        }
    }
}