package com.yihecode.camera.ai.web;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.core.util.StrUtil;
import com.yihecode.camera.ai.entity.Algorithm;
import com.yihecode.camera.ai.entity.CameraAlgorithm;
import com.yihecode.camera.ai.service.AlgorithmService;
import com.yihecode.camera.ai.service.CameraAlgorithmService;
import com.yihecode.camera.ai.service.CameraService;
import com.yihecode.camera.ai.utils.PageResult;
import com.yihecode.camera.ai.utils.PageResultUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 摄像头与算法关联管理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@SaCheckLogin
@Controller
@RequestMapping({"/camera/algorithm"})
public class CameraAlgorithmController {

    //
    @Autowired
    private CameraService cameraService;

    //
    @Autowired
    private CameraAlgorithmService cameraAlgorithmService;

    //
    @Autowired
    private AlgorithmService algorithmService;

    /**
     * 查询数据列表
     * @param cameraId
     * @return
     */
    @PostMapping({"/listData"})
    @ResponseBody
    public PageResult listData(Long cameraId) {
        //
        List<Algorithm> algorithmList = this.algorithmService.list();
        if (algorithmList == null) {
            algorithmList = new ArrayList<>();
        }

        //
        List<CameraAlgorithm> cameraAlgorithmList = this.cameraAlgorithmService.listByCamera(cameraId);
        if (cameraAlgorithmList == null) {
            cameraAlgorithmList = new ArrayList<>();
        }

        //
        List<Long> aList = new ArrayList<>();
        Map<Long, Float> confidenceMap = new HashMap<>();
        Map<Long, String> markPointsMap = new HashMap<>();
        for (CameraAlgorithm cameraAlgorithm : cameraAlgorithmList) {
            aList.add(cameraAlgorithm.getAlgorithmId());
            confidenceMap.put(cameraAlgorithm.getAlgorithmId(), cameraAlgorithm.getConfidence());
            markPointsMap.put(cameraAlgorithm.getAlgorithmId(), cameraAlgorithm.getMarkPoints());
        }

        //
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (Algorithm algorithm : algorithmList) {
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("id", algorithm.getId());
            dataMap.put("name", algorithm.getName());
            dataMap.put("confidence", confidenceMap.get(algorithm.getId()) == null ? 0f : confidenceMap.get(algorithm.getId()));
            dataMap.put("checked", aList.contains(algorithm.getId()));
            dataMap.put("markPoints", StrUtil.isBlank(markPointsMap.get(algorithm.getId())) ? "" : markPointsMap.get(algorithm.getId()));
            dataList.add(dataMap);
        }
        return PageResultUtils.success(null, dataList);
    }
}
