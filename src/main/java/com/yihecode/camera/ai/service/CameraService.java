package com.yihecode.camera.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yihecode.camera.ai.entity.Camera;

import java.util.List;
import java.util.Map;

/**
 * 摄像头管理
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public interface CameraService extends IService<Camera> {

    /**
     *
     * @param id
     */
    void delete(Long id);

    /**
     *
     * @param camera
     * @param algorithms
     * @param confidencevos
     */
    void saveCamera(Camera camera, String algorithms, String confidencevos, String markpointsvos, Integer updatePoint);

    /**
     * set to NULL state
     */
    void updateAction();

    /**
     *
     * @param id
     * @param action
     */
    void updateActionByCamera(Long id, Integer action);

    /**
     *
     * @param cameraId
     */
    void updateActionById(Long cameraId, Integer action);

    /**
     *
     * @param id
     * @param running
     */
    void updateRunning(Long id, Integer running);

    /**
     *
     * @return
     */
    Map<Long, String> toMap();

    /**
     *
     * @return
     */
    List<Camera> listData();

    /**
     * 根据基地ID查询
     * @param wareHouseId
     * @return
     */
    Camera getByWareHouseId(Long wareHouseId);

    /**
     * 更新rtsp地址
     * @param cameraId
     * @param rtspUrl
     */
    void updateRtspUrl(Long cameraId, String rtspUrl);

    /**
     * 分页查询
     * @param pageObj
     * @return
     */
    IPage<Camera> listPage(IPage<Camera> pageObj, Camera queryCamera);

    /**
     * 临时方案
     * @param id
     * @param action
     * @param state
     */
    void updateAndState(Long id, Integer action, Integer state);

    /**
     * 切换视频流类型
     * @param id
     * @param rtspType
     */
    void updateRtspType(Long id, Integer rtspType);

    /**
     * 根据rtsp_type查询
     * @param rtspType
     * @return
     */
    List<Camera> listByRtspType(Integer rtspType);

    /**
     * 根据区域节点删除摄像头
     * @param locationId
     */
    void removeByLocation(Long locationId);

    /**
     * 查询活动的摄像头
     * @return
     */
    List<Camera> listActives();

    /**
     * 根据摄像头名称查询
     * @param cameraName
     * @return
     */
    Camera getByName(String cameraName);

    /**
     * 根据运行状态统计总数
     * @param runState
     * @return
     */
    Integer getCountByRunState(Integer runState);

    /**
     * 更新摄像头播放状态
     * @param playCameraIds
     */
    void updateVideoPlays(List<Long> playCameraIds);

    /**
     * 修改播放状态
     * @param cameraId
     * @param playState 0-不播放 1-播放
     */
    boolean updatePlay(Long cameraId, Integer playState);

    /**
     * 修改播放状态
     * @param cameraId
     * @param playState 0-不播放 1-播放
     * @param videoPort
     * @return
     */
    Map<String, Object> updateVideoPlay(Long cameraId, Integer playState, Integer videoPort);

    /**
     * 分页查询， 根据视频播放标识进行排序
     * @param pageObj
     * @return
     */
    IPage<Camera> listPageAndOrderVideoPlay(IPage<Camera> pageObj);

    /**
     * 已删除摄像头删除关联的算法
     */
    void removeDeleted();
}
