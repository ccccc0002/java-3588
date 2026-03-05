package com.yihecode.camera.ai.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yihecode.camera.ai.entity.Report;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 告警管理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public interface ReportMapper extends BaseMapper<Report> {

    List<Map<String, Object>> selectAlgorithmRatio(@Param("ew") Wrapper<Report> wrapper);

    List<Map<String, Object>> selectCamera(@Param("ew") Wrapper<Report> wrapper);

    List<Map<String, Object>> selectCameraAlgorithm(@Param("ew") Wrapper<Report> wrapper);

    List<Map<String, Object>> selectAlgorithmStatics(Map<String, Object> params);
}