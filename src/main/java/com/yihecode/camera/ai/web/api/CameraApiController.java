package com.yihecode.camera.ai.web.api;


import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.yihecode.camera.ai.entity.Algorithm;
import com.yihecode.camera.ai.entity.Camera;
import com.yihecode.camera.ai.entity.CameraAlgorithm;
import com.yihecode.camera.ai.entity.VideoPlay;
import com.yihecode.camera.ai.enums.CameraAction;
import com.yihecode.camera.ai.enums.CameraRunningState;
import com.yihecode.camera.ai.enums.CommState;
import com.yihecode.camera.ai.service.*;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 摄像头列表接口，对接算法拉取最新摄像头配置数据
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Slf4j
@Controller
@RequestMapping({"/api/camera"})
public class CameraApiController {

    //
    @Autowired
    private CameraService cameraService;

    //
    @Autowired
    private AlgorithmService algorithmService;

    //
    @Autowired
    private CameraAlgorithmService cameraAlgorithmService;

    //
    @Autowired
    private VideoPlayService videoPlayService;

    @Autowired
    private MediaStreamUrlService mediaStreamUrlService;

    /**
     *
     * @return
     */
    @RequestMapping({"/list"})
    @ResponseBody
    public JsonResult listData() {
        try {
            String traceId = UUID.randomUUID().toString().replace("-", "");
            //
            List<Camera> cameraList = this.cameraService.list();
            if (cameraList == null) {
                cameraList = new ArrayList<>();
            }

            //
            List<Algorithm> algorithmList = this.algorithmService.list();
            if (algorithmList == null) {
                algorithmList = new ArrayList<>();
            }

            //
            Map<Long, String> algorithmNames = new HashMap<>();
            for (Algorithm algorithm : algorithmList) {
                algorithmNames.put(algorithm.getId(), algorithm.getName());
            }

            //英文名字队列
            Map<Long, String> algorithmEnNames = new HashMap<>();
            for (Algorithm algorithm : algorithmList) {
                algorithmEnNames.put(algorithm.getId(), algorithm.getNameEn());
            }

            // 视频播放列表端口映射
            Map<Long, Integer> videoPortMap = new HashMap<>();
            List<VideoPlay> videoPlays = videoPlayService.list();
            if(videoPlays != null) {
                for(VideoPlay videoPlay : videoPlays) {
                    videoPortMap.put(videoPlay.getCameraId(), videoPlay.getVideoPort());
                }
            }

            //
            List<Map<String, Object>> dataList = new ArrayList<>();

            //
            for (Camera camera : cameraList) {
                //
                Map<String, Object> cameraMap = new HashMap<>();
                cameraMap.put("trace_id", traceId);
                cameraMap.put("camera_id", camera.getId());
                cameraMap.put("camera_name", camera.getName());
                cameraMap.put("rtsp_url", camera.getRtspUrl());
                cameraMap.put("action", camera.getAction());
                cameraMap.put("state", camera.getState());
                cameraMap.put("interval_time", camera.getIntervalTime());
                cameraMap.put("frequency", camera.getFrequency());
                cameraMap.put("params", camera.getApiParams());
                cameraMap.put("video_play", camera.getVideoPlay() == null ? 0 : camera.getVideoPlay());


                // 推流地址
                if(videoPortMap.containsKey(camera.getId())) {
                    // 视频是否播放
                    cameraMap.put("video_play", 1);
                    //
                    cameraMap.put("rtmp_url", mediaStreamUrlService.buildPushRtmpUrl(camera.getId(), videoPortMap.get(camera.getId())));
                } else {
                    // 视频是否播放
                    cameraMap.put("video_play", 0);
                    //
                    cameraMap.put("rtmp_url", "");
                }

                // 摄像头为有效状态
                // 当running状态为停止状态时，会将action设置为2，但是摄像头state还是0，所以做下特殊处理，就不需要算法做修改
                if(camera.getState() == 0) {
                    // 摄像停止
                    if(camera.getRunning() == null || camera.getRunning() == CameraRunningState.CLOSED.getType()) {
                        cameraMap.put("action", CameraAction.ACTION_DEL.getType());
                        cameraMap.put("state", CommState.DISABLED.getType());
                    }
                }

                // 图片地址类型，将摄像头状态设置为失效，动作为删除
                if(camera.getRtspType() != null && camera.getRtspType() == 2) {
                    cameraMap.put("action", CameraAction.ACTION_DEL.getType());
                    cameraMap.put("state", CommState.DISABLED.getType());
                }

                // 标记参数处理
                if(StrUtil.isBlank(camera.getApiParams())) {
                    cameraMap.put("params", "");
                } else {
                    // 防止异常
                    try {
                        JSONArray apiParams = JSON.parseArray(camera.getApiParams());
                        int len = apiParams.size();
                        if(len == 0) {
                            cameraMap.put("params", "");
                        } else {
                            JSONArray sub = new JSONArray();
                            for(int i = 0; i < len; i++) {
                                if(i == 0) {
                                    cameraMap.put("params", apiParams.getJSONArray(0).toString());
                                } else {
                                    sub.add(apiParams.getJSONArray(i));
                                }
                            }
                            if(sub.size() == 0) {
                                cameraMap.put("other_params", "");
                            } else {
                                cameraMap.put("other_params", JSON.toJSONString(sub));
                            }
                        }
                    } catch (Exception e) {
                        //
                    }
                }

                //
                List<CameraAlgorithm> cameraAlgorithmList = this.cameraAlgorithmService.listByCamera(camera.getId());
                List<Map<String, Object>> algorithms = new ArrayList<>();
                if (cameraAlgorithmList != null) {
                    for (CameraAlgorithm cameraAlgorithm : cameraAlgorithmList) {
                        if(algorithmNames.containsKey(cameraAlgorithm.getAlgorithmId())) {
                            Map<String, Object> algorithMap = new HashMap<>();
                            algorithMap.put("algorithm_id", cameraAlgorithm.getAlgorithmId());
                            algorithMap.put("algorithm_name", algorithmNames.get(cameraAlgorithm.getAlgorithmId()));
                            algorithMap.put("algorithm_name_en", algorithmEnNames.get(cameraAlgorithm.getAlgorithmId()));
                            algorithMap.put("algorithm_confidence", cameraAlgorithm.getConfidence());
                            algorithms.add(algorithMap);
                        }
                    }
                }
                cameraMap.put("algorithms", algorithms);
                dataList.add(cameraMap);
            }
            this.cameraService.updateAction();
            return JsonResultUtils.success(dataList);
        } catch (Exception e) {
            log.error("调用摄像头列表接口异常", e);
            return JsonResultUtils.fail();
        }
    }
}
