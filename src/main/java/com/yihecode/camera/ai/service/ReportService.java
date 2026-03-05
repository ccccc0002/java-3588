package com.yihecode.camera.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yihecode.camera.ai.entity.Report;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 告警管理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public interface ReportService extends IService<Report> {

    /**
     *
     * @param pageObj
     * @param report
     * @return
     */
    IPage<Report> listPage(IPage<Report> pageObj, Report report);

    /**
     *
     * @param cameraId
     * @param algorithmId
     * @return
     */
    Report findLast(Long cameraId, Long algorithmId);

    /**
     *
     * @param startDate
     * @param endDate
     * @return
     */
    List<Map<String, Object>> findAlgorithmRatio(Date startDate, Date endDate);

    /**
     *
     * @param startDate
     * @param endDate
     * @return
     */
    List<Map<String, Object>> findCamera(Date startDate, Date endDate);

    /**
     *
     * @param startDate
     * @param endDate
     * @return
     */
    List<Map<String, Object>> findCameraAlgorithm(Date startDate, Date endDate);

    /**
     *
     * @param id
     * @param display
     */
    void updateDisplay(Long id, Integer display);

    /**
     * 审核
     * @param id
     * @param result
     */
    void updateAudit(Long id, Integer result);

    /**
     *
     * @param baseDir
     * @param algorithmId
     * @param cameraId
     * @param startDate
     * @param endDate
     * @return
     */
    String export(String baseDir, Long algorithmId, Long cameraId, Date startDate, Date endDate, Integer auditState);

    /**
     * 根据算法id，开始和结束毫秒值查询总数
     * @param algorithmId
     * @param startMills
     * @param endMills
     * @return
     */
    Integer getAlgorithmCounter(Long algorithmId, long startMills, long endMills);

    /**
     * 开始和结束毫秒值查询总数
     * @param startMills
     * @param endMills
     * @return
     */
    int getCounter(long startMills, long endMills);

    /**
     * 分页查询
     * @param objectPage
     * @param cameraId
     * @param algorithmId
     * @param startMills
     * @param endMills
     * @return
     */
    IPage<Report> listByPage(IPage<Report> objectPage, Long cameraId, Long algorithmId, Integer type, Long startMills, Long endMills);

    /**
     * 查询最近3天记录
     * @param nums
     * @return
     */
    List<Report> listNewly(int nums);

    /**
     * 统计总数
     * @param startMills
     * @param endMills
     * @return
     */
    Integer getCount(Long startMills, Long endMills, Long cameraId, Long algorithmId, Integer type);

    /**
     * 统计总数
     * @param startMills
     * @param endMills
     * @return
     */
    Integer getMarkCount(Long startMills, Long endMills, Long cameraId, Long algorithmId);

    /**
     * 根据算法统计数量
     * @param startMills
     * @param endMills
     * @return
     */
    Map<Long, Integer> getCountByAlgorithm(Long startMills, Long endMills);
}
