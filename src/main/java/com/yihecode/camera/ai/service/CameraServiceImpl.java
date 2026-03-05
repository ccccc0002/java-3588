package com.yihecode.camera.ai.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yihecode.camera.ai.entity.Camera;
import com.yihecode.camera.ai.entity.CameraAlgorithm;
import com.yihecode.camera.ai.entity.ReportPeriod;
import com.yihecode.camera.ai.entity.VideoPlay;
import com.yihecode.camera.ai.enums.CameraAction;
import com.yihecode.camera.ai.enums.CameraRunningState;
import com.yihecode.camera.ai.enums.CommState;
import com.yihecode.camera.ai.mapper.CameraMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 鎽勫儚澶寸鐞? *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Slf4j
@Service
public class CameraServiceImpl extends ServiceImpl<CameraMapper, Camera> implements CameraService {

    @Autowired
    private CameraAlgorithmService cameraAlgorithmService;

    @Autowired
    private ReportPeriodService reportPeriodService;

    @Autowired
    private ConfigService configService;

    @Autowired
    private VideoPlayService videoPlayService;

    @Value("${uploadDir}")
    private String uploadDir;

    /**
     *
     * @param id
     */
    @Override
    public void delete(Long id) {
        LambdaUpdateWrapper<Camera> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(Camera::getState, CommState.DISABLED.getType())
                .set(Camera::getAction, CameraAction.ACTION_DEL.getType())
                .set(Camera::getVideoPlay, 0)
                .eq(Camera::getId, id);
        this.getBaseMapper().update(null, updateWrapper);

        // 鍒犻櫎鍏宠仈绠楁硶
        cameraAlgorithmService.deleteByCamera(id);

        // 鍒犻櫎鎾斁绔彛鍗犵敤
        videoPlayService.removeByCamera(id);

        // 鍒犻櫎鍛婅鏃舵閰嶇疆
        List<Long> algorithmIds = reportPeriodService.listAlgorithmId(id);
        for(Long algorithmId : algorithmIds) {
            reportPeriodService.deleteByCameraAndAlgorithm(id, algorithmId);
        }
    }

    /**
     *
     * @param camera
     * @param str
     * @param confidencevos
     */
    @Override
    public void saveCamera(Camera camera, String str, String confidencevos, String markpointsvos, Integer updatePoint) {
        // 闃叉鍧愭爣涓嶅仠鍙樺姩
        if(updatePoint == null || updatePoint == 1) {
            //
            List<List<Map<String, Object>>> apiParams = new ArrayList<>();
            if (StrUtil.isNotBlank(camera.getParams()) && StrUtil.isNotBlank(camera.getFileName()) && camera.getScaleRatio() != null) {
                JSONArray points = JSON.parseArray(camera.getParams() );
                int len = points.size();
                for (int i = 0; i < len; i++) {
                    JSONArray subPoint = points.getJSONArray(i);
                    int subLen = subPoint.size();
                    if (subLen >= 3) {
                        List<Map<String, Object>> subParams = new ArrayList<>();
                        for (int j = 0; j < subLen; j++) {
                            JSONObject point = subPoint.getJSONObject(j);
                            float x = point.getFloatValue("x");
                            float y = point.getFloatValue("y");
                            float xNew = x * camera.getScaleRatio();
                            float yNew = y * camera.getScaleRatio();

                            Map<String, Object> p = new HashMap<>();
                            p.put("x", Float.valueOf(xNew).intValue());
                            p.put("y", Float.valueOf(yNew).intValue());
                            subParams.add(p);
                        }
                        apiParams.add(subParams);
                    }
                }
            }

            //
            if (apiParams.isEmpty()) {
                camera.setApiParams("");
            } else {
                camera.setApiParams(JSON.toJSONString(apiParams));
            }
        }

        //
        camera.setFrequency(1000);
        camera.setAction(CameraAction.ACTION_UPD.getType());
        camera.setUpdatedAt(new Date());
        this.saveOrUpdate(camera);

        //
        String[] algorithmIds = str.split(",");
        if(algorithmIds == null) {
            return ;
        }

        //
        String[] confidenceArr = confidencevos.split(",");
        if(confidenceArr == null || confidenceArr.length != algorithmIds.length) {
            return ;
        }

        //
        String[] markPointsStrArr = null;
        String[] imagePointsArr = null; // 杞崲鍧愭爣
        if(StrUtil.isNotBlank(camera.getFileName()) && StrUtil.isNotBlank(markpointsvos)) {
            markPointsStrArr = markpointsvos.split("#");
            if(markPointsStrArr != null) {
                int len = markPointsStrArr.length;
                imagePointsArr = new String[len];

                for(int i = 0; i < len; i++) {
                    String markPointsStr = markPointsStrArr[i]; // [[{x:0, y:0}, ....]]
                    if(StrUtil.isBlank(markPointsStr)) {
                        imagePointsArr[i] = "";
                        continue;
                    }
                    if("[]".equals(markPointsStr)) {
                        markPointsStrArr[i] = "";
                        imagePointsArr[i] = "";
                        continue;
                    }

                    //
                    JSONArray imagePointsGrp = new JSONArray(); // 鏂板潗鏍囩粍闆嗗悎
                    JSONArray markPointsGrp = JSON.parseArray(markPointsStr);
                    int grpSize = markPointsGrp.size();
                    for(int k = 0; k < grpSize; k++) {
                        JSONArray imagePoints = new JSONArray(); // 鏂板潗鏍囩粍
                        JSONArray markPoints = markPointsGrp.getJSONArray(k);
                        int mpSize = markPoints.size();
                        for(int m = 0; m < mpSize; m++) {
                            JSONObject point = markPoints.getJSONObject(m);
                            float x = point.getFloatValue("x");
                            float y = point.getFloatValue("y");
                            float xNew = x * camera.getScaleRatio();
                            float yNew = y * camera.getScaleRatio();
                            //
                            JSONObject imagePoint = new JSONObject();
                            imagePoint.put("x", Float.valueOf(xNew).intValue());
                            imagePoint.put("y", Float.valueOf(yNew).intValue());
                            imagePoints.add(imagePoint);
                        }
                        imagePointsGrp.add(imagePoints);
                    }
                    imagePointsArr[i] = imagePointsGrp.toJSONString();
                }
            }
        }

        //
        cameraAlgorithmService.deleteByCamera(camera.getId());

        //
        int len = algorithmIds.length;
        for(int i = 0; i < len; i++) {
            String algorithmId = algorithmIds[i];
            CameraAlgorithm cameraAlgorithm = new CameraAlgorithm();
            cameraAlgorithm.setCameraId(camera.getId());
            cameraAlgorithm.setAlgorithmId(Long.parseLong(algorithmId));
            cameraAlgorithm.setConfidence(Float.parseFloat(confidenceArr[i]));
            cameraAlgorithm.setMarkPoints(markPointsStrArr == null ? "" : markPointsStrArr[i]);
            cameraAlgorithm.setImagePoints(imagePointsArr == null ? "" : imagePointsArr[i]);
            cameraAlgorithmService.save(cameraAlgorithm);
        }

        // 鍒犻櫎鍛婅鏃舵閰嶇疆
        List<Long> reportAlgorithmIds = reportPeriodService.listAlgorithmId(camera.getId());
        for(Long reportAlgorithmId : reportAlgorithmIds) {
            //
            for(String algorithmId : algorithmIds) {
                if(algorithmId.equals(String.valueOf(reportAlgorithmId))) {
                    continue;
                }
            }

            //
            reportPeriodService.deleteByCameraAndAlgorithm(camera.getId(), reportAlgorithmId);
        }

        // 鏂板榛樿鍛婅鏃舵
        for(String algorithmId : algorithmIds) {
            List<ReportPeriod> reportPeriodList = reportPeriodService.listData(camera.getId(), Long.parseLong(algorithmId));
            if(reportPeriodList.isEmpty()) {
                //
                ReportPeriod reportPeriod = new ReportPeriod();
                reportPeriod.setCameraId(camera.getId());
                reportPeriod.setAlgorithmId(Long.parseLong(algorithmId));
                reportPeriod.setStartText("00:00");
                reportPeriod.setStartTime(0);
                reportPeriod.setEndText("23:59");
                reportPeriod.setEndTime(2359);
                reportPeriodService.save(reportPeriod);
            }
        }
    }

    /**
     * set to NULL state
     */
    @Override
    public void updateAction() {
        LambdaUpdateWrapper<Camera> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.gt(Camera::getAction, CameraAction.ACTION_NULL.getType());
        Camera camera = new Camera();
        camera.setAction(CameraAction.ACTION_NULL.getType());
        this.getBaseMapper().update(camera, updateWrapper);
    }

    /**
     *
     * @param id
     * @param action
     */
    @Override
    public void updateActionByCamera(Long id, Integer action) {
        LambdaUpdateWrapper<Camera> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(Camera::getAction, action);
        updateWrapper.eq(Camera::getId, id);

        this.getBaseMapper().update(null, updateWrapper);
    }

    /**
     * @param cameraId
     * @param action
     */
    @Override
    public void updateActionById(Long cameraId, Integer action) {
        LambdaUpdateWrapper<Camera> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(Camera::getAction, action);
        updateWrapper.eq(Camera::getId, cameraId);
        this.getBaseMapper().update(null, updateWrapper);
    }

    /**
     *
     * @param id
     * @param running
     */
    @Override
    public void updateRunning(Long id, Integer running) {
        LambdaUpdateWrapper<Camera> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(Camera::getRunning, running)
                .eq(Camera::getId, id);
        this.getBaseMapper().update(null, updateWrapper);

        // 瑙﹀彂鎽勫儚澶存洿鏂帮紝鎺ュ彛灏嗗垽鏂璻unning鍜宎ction涓や釜瀛楁, 鍏蜂綋瑙?CameraApiController.java
        this.updateActionById(id, 1);
    }

    /**
     *
     * @return
     */
    @Override
    public Map<Long, String> toMap() {
        List<Camera> cameraList = listData();
        Map<Long, String> cameraMap = new HashMap<>();
        for (Camera camera : cameraList) {
            cameraMap.put(camera.getId(), camera.getName());
        }
        return cameraMap;
    }

    /**
     *
     * @return
     */
    @Override
    public List<Camera> listData() {
        LambdaQueryWrapper<Camera> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Camera::getState, CommState.NORMAL.getType());
        List<Camera> cameraList = this.list(queryWrapper);
        if(cameraList == null) {
            return new ArrayList<>();
        }
        return cameraList;
    }

    /**
     * 鏍规嵁鍩哄湴ID鏌ヨ
     *
     * @param wareHouseId
     * @return
     */
    @Override
    public Camera getByWareHouseId(Long wareHouseId) {
        LambdaQueryWrapper<Camera> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Camera::getWareHouseId, wareHouseId);
        queryWrapper.eq(Camera::getState, CommState.NORMAL.getType());
        return this.getOne(queryWrapper);
    }

    /**
     * 鏇存柊rtsp鍦板潃
     *
     * @param cameraId
     * @param rtspUrl
     */
    @Override
    public void updateRtspUrl(Long cameraId, String rtspUrl) {
        LambdaUpdateWrapper<Camera> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(Camera::getRtspUrl, rtspUrl)
                .eq(Camera::getId, cameraId);
        this.getBaseMapper().update(null, updateWrapper);

        // 瑙﹀彂鎽勫儚澶存洿鏂帮紝鎺ュ彛灏嗗垽鏂璻unning鍜宎ction涓や釜瀛楁, 鍏蜂綋瑙?CameraApiController.java
        //this.updateActionById(cameraId, CameraAction.ACTION_UPD.getType());
    }

    /**
     * 鍒嗛〉鏌ヨ
     *
     * @param pageObj
     * @return
     */
    @Override
    public IPage<Camera> listPage(IPage<Camera> pageObj, Camera queryCamera) {
        LambdaQueryWrapper<Camera> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Camera::getState, CommState.NORMAL.getType());
        if(StrUtil.isNotBlank(queryCamera.getName())) {
            queryWrapper.like(Camera::getName, queryCamera.getName());
        }
        if(queryCamera.getLocationId() != null) {
            queryWrapper.like(Camera::getLocationIds, queryCamera.getLocationId());
        }
        queryWrapper.orderByDesc(Camera::getRunning);
        return this.page(pageObj, queryWrapper);
    }

    /**
     * 涓存椂鏂规
     *
     * @param id
     * @param action
     * @param state
     */
    @Override
    public void updateAndState(Long id, Integer action, Integer state) {
        LambdaUpdateWrapper<Camera> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(Camera::getAction, action)
                .set(Camera::getState, state)
                .eq(Camera::getId, id);
        this.getBaseMapper().update(null, updateWrapper);
    }

    /**
     * 鍒囨崲瑙嗛娴佺被鍨?     *
     * @param cameraId
     * @param rtspType
     */
    @Override
    public void updateRtspType(Long cameraId, Integer rtspType) {
        LambdaUpdateWrapper<Camera> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(Camera::getRtspType, rtspType)
                .eq(Camera::getId, cameraId);
        this.getBaseMapper().update(null, updateWrapper);
    }

    /**
     * 鏍规嵁rtsp_type鏌ヨ
     *
     * @param rtspType
     * @return
     */
    @Override
    public List<Camera> listByRtspType(Integer rtspType) {
        LambdaQueryWrapper<Camera> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(Camera::getId, Camera::getName, Camera::getWareHouseId, Camera::getApiParams);
        queryWrapper.eq(Camera::getState, CommState.NORMAL.getType());
        queryWrapper.eq(Camera::getRunning, CameraRunningState.RUNNING.getType());
        queryWrapper.eq(Camera::getRtspType, rtspType);
        return this.list(queryWrapper);
    }

    /**
     * 鏍规嵁鍖哄煙鑺傜偣鍒犻櫎鎽勫儚澶?     *
     * @param locationId
     */
    @Override
    public void removeByLocation(Long locationId) {
        if(locationId == null) {
            return;
        }
        LambdaQueryWrapper<Camera> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Camera::getLocationId, locationId);
        List<Camera> cameras = this.list(queryWrapper);
        if(cameras == null || cameras.isEmpty()) {
            return;
        }
        for(Camera camera : cameras) {
            if(camera == null || camera.getId() == null) {
                continue;
            }
            this.delete(camera.getId());
        }
    }

    /**
     * 鏌ヨ娲诲姩鐨勬憚鍍忓ご
     *
     * @return
     */
    @Override
    public List<Camera> listActives() {
        LambdaQueryWrapper<Camera> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(Camera::getId, Camera::getName, Camera::getRtspUrl);
        queryWrapper.eq(Camera::getState, CommState.NORMAL.getType());
        queryWrapper.eq(Camera::getRunning, CameraRunningState.RUNNING.getType());
        queryWrapper.eq(Camera::getRtspType, 0);
        queryWrapper.last(" limit 0, 10 ");

        //
        List<Camera> cameraList = this.list(queryWrapper);
        if(cameraList == null) {
            return new ArrayList<>();
        }
        return cameraList;
    }

    /**
     * 鏍规嵁鎽勫儚澶村悕绉版煡璇?     *
     * @param cameraName
     * @return
     */
    @Override
    public Camera getByName(String cameraName) {
        LambdaQueryWrapper<Camera> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Camera::getName, cameraName);
        queryWrapper.eq(Camera::getState, CommState.NORMAL.getType());
        queryWrapper.last(" limit 0, 1 ");
        return this.getOne(queryWrapper);
    }

    /**
     * 鏍规嵁杩愯鐘舵€佺粺璁℃€绘暟
     *
     * @param runState
     * @return
     */
    @Override
    public Integer getCountByRunState(Integer runState) {
        LambdaQueryWrapper<Camera> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Camera::getState, 0);
        if(runState >= 0) {
            queryWrapper.eq(Camera::getRunning, runState);
        }
        return Math.toIntExact(this.count(queryWrapper));
    }

    /**
     * 鏇存柊鎽勫儚澶存挱鏀剧姸鎬?     *
     * @param playCameraIds
     */
    @Override
    public void updateVideoPlays(List<Long> playCameraIds) {
        //
        if(playCameraIds == null || playCameraIds.isEmpty()) {
            return ;
        }
        //
        for(Long cameraId : playCameraIds) {
            System.out.println("update last time " + cameraId);
            videoPlayService.updateLastTime(cameraId);
        }


        /*
        //
        LambdaQueryWrapper<Camera> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Camera::getVideoPlay, 1);
        List<Camera> cameraList = this.list(queryWrapper);

        //
        if(cameraList != null) {
            for(Camera camera : cameraList) {
                if(!playCameraIds.contains(camera.getId())) {
                    Camera updateCamera = new Camera();
                    updateCamera.setId(camera.getId());
                    updateCamera.setVideoPlay(0);
                    this.updateById(updateCamera);
                }
            }
        }

        //
        for(Long cameraId : playCameraIds) {
            Camera updateCamera = new Camera();
            updateCamera.setId(cameraId);
            updateCamera.setVideoPlay(1);
            this.updateById(updateCamera);
        }

         */
    }

    private final Lock lock = new ReentrantLock();

    /**
     * 淇敼鎾斁鐘舵€?     *
     * @param cameraId
     * @param playState
     */
    @Override
    public boolean updatePlay(Long cameraId, Integer playState) {
        lock.lock();
        try {
            // 鍋滄鎾斁
            if (playState == null || playState != 1) {
                videoPlayService.removeByCamera(cameraId);
                //
                Camera camera = new Camera();
                camera.setId(cameraId);
                camera.setVideoPlay(0);
                this.updateById(camera);
                return true;
            }

            // 姝ｅ湪鎾斁
            VideoPlay videoPlayDb = videoPlayService.getByCamera(cameraId);
            if (videoPlayDb != null) {
                return true;
            }

            // 鏌ヨ閰嶇疆鐨勭鍙ｅ彿
            String videoPorts = configService.getByValTag("video_ports");
            if (StrUtil.isBlank(videoPorts)) {
                //return "娌℃湁閰嶇疆澶栫綉鎷夋祦绔彛鍒楄〃";
                return false;
            }
            List<Integer> portList = new ArrayList<>();
            String[] ports = videoPorts.split(",");
            for (int i = 0; i < ports.length; i++) {
                portList.add(Integer.valueOf(ports[i]));
            }
            List<Integer> usePorts = videoPlayService.listUsePort();
            //
            boolean useOk = false;
            for (Integer port : portList) {
                if (usePorts.contains(port)) {
                    continue;
                }
                // 澧炲姞瑙嗛绔彛鍗犵敤
                VideoPlay videoPlay = new VideoPlay();
                videoPlay.setCameraId(cameraId);
                videoPlay.setVideoPort(port);
                videoPlay.setLastTime(System.currentTimeMillis());
                videoPlayService.save(videoPlay);
                Camera camera = new Camera();
                camera.setId(cameraId);
                camera.setVideoPlay(1);
                this.updateById(camera);
                // 鎾斁鍗犵敤鎴愬姛
                useOk = true;
                break;
            }
            return useOk;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 淇敼鎾斁鐘舵€?     *
     * @param cameraId
     * @param playState 0-涓嶆挱鏀?1-鎾斁
     * @param videoPort
     * @return
     */
    @Override
    public Map<String, Object> updateVideoPlay(Long cameraId, Integer playState, Integer videoPort) {
        lock.lock();
        // 杩斿洖鏁版嵁缁撴瀯
        Map<String, Object> retMap = new HashMap<>();
        retMap.put("code", 200);
        retMap.put("msg", "Err");

        // 鏄惁绛夊緟
        boolean waitFlag = true;

        //
        try {
            // 鍋滄鎾斁
            if (playState == null || playState != 1) {
                videoPlayService.removeByCamera(cameraId);
                //
                Camera camera = new Camera();
                camera.setId(cameraId);
                camera.setVideoPlay(0);
                this.updateById(camera);
                return retMap;
            }

            // 鏍规嵁绔彛鏌ヨ
            VideoPlay videoPlayByPort = videoPlayService.getByPort(videoPort);
            if(videoPlayByPort != null) {
                waitFlag = false;

                //
                retMap.put("code", 500);
                retMap.put("msg", "play address is already in use");
                return retMap;
            }

            // 澧炲姞瑙嗛绔彛鍗犵敤
            VideoPlay videoPlay = new VideoPlay();
            videoPlay.setCameraId(cameraId);
            videoPlay.setVideoPort(videoPort);
            videoPlay.setLastTime(System.currentTimeMillis());
            videoPlayService.save(videoPlay);

            Camera camera = new Camera();
            camera.setId(cameraId);
            camera.setVideoPlay(1);
            this.updateById(camera);

            //
            return retMap;
        } finally {
            if(waitFlag) {
                try {
                    Thread.sleep(15000);
                } catch (Exception e) {
                }
            }

            lock.unlock();
        }
    }

    /**
     * 鍒嗛〉鏌ヨ锛?鏍规嵁瑙嗛鎾斁鏍囪瘑杩涜鎺掑簭
     *
     * @param pageObj
     * @return
     */
    @Override
    public IPage<Camera> listPageAndOrderVideoPlay(IPage<Camera> pageObj) {
        LambdaQueryWrapper<Camera> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Camera::getState, CommState.NORMAL.getType());
        queryWrapper.orderByDesc(Camera::getVideoPlay);
        return this.page(pageObj, queryWrapper);
    }

    /**
     * 宸插垹闄ゆ憚鍍忓ご鍒犻櫎鍏宠仈鐨勭畻娉? 绯荤粺鍒濆鍖栨椂璋冪敤涓€娆?     */
    @Override
    public void removeDeleted() {
        LambdaQueryWrapper<Camera> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Camera::getState, CommState.DISABLED.getType());
        //
        List<Camera> cameraList = this.list(queryWrapper);
        if(cameraList == null) {
            return ;
        }
        //
        for(Camera camera : cameraList) {
            cameraAlgorithmService.deleteByCamera(camera.getId());
        }
    }
}
