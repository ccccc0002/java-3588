package com.yihecode.camera.ai.service;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ZipUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yihecode.camera.ai.entity.Report;
import com.yihecode.camera.ai.enums.ReportType;
import com.yihecode.camera.ai.mapper.ReportMapper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 鍛婅绠＄悊
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Service
public class ReportServiceImpl extends ServiceImpl<ReportMapper, Report> implements ReportService {

    /**
     *
     * @param pageObj
     * @param report
     * @return
     */
    @Override
    public IPage<Report> listPage(IPage<Report> pageObj, Report report) {
        LambdaQueryWrapper<Report> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Report::getDisplay, 0);
        if(report.getCameraId() != null) {
            queryWrapper.eq(Report::getCameraId, report.getCameraId());
        }
        if(report.getAlgorithmId() != null) {
            queryWrapper.eq(Report::getAlgorithmId, report.getAlgorithmId());
        }
        if(report.getType() != null) {
            queryWrapper.eq(Report::getType, report.getType());
        }
        if(report.getAuditResult() != null) {
            queryWrapper.eq(Report::getAuditResult, report.getAuditResult());
        }
        if(report.getAuditState() != null) {
            queryWrapper.eq(Report::getAuditState, report.getAuditState());
        }
        queryWrapper.orderByDesc(Report::getCreatedMills);
        return this.page(pageObj, queryWrapper);
    }

    /**
     *
     * @param cameraId
     * @param algorithmId
     * @return
     */
    @Override
    public Report findLast(Long cameraId, Long algorithmId) {
        LambdaQueryWrapper<Report> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Report::getAlgorithmId, algorithmId);
        queryWrapper.eq(Report::getCameraId, cameraId);
        queryWrapper.eq(Report::getType, ReportType.AI.getType());
        queryWrapper.orderByDesc(Report::getCreatedMills);
        queryWrapper.last(" limit 0, 1");
        return this.getOne(queryWrapper);
    }

    /**
     *
     * @param startDate
     * @param endDate
     * @return
     */
    @Override
    public List<Map<String, Object>> findAlgorithmRatio(Date startDate, Date endDate) {
        LambdaQueryWrapper<Report> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.gt(Report::getCreatedAt, startDate);
        queryWrapper.lt(Report::getCreatedAt, endDate);
        queryWrapper.eq(Report::getDisplay, 0);
        queryWrapper.eq(Report::getType, ReportType.AI.getType());
        queryWrapper.last(" group by algorithm_id");
        return this.getBaseMapper().selectAlgorithmRatio(queryWrapper);
    }

    /**
     *
     * @param startDate
     * @param endDate
     * @return
     */
    @Override
    public List<Map<String, Object>> findCamera(Date startDate, Date endDate) {
        LambdaQueryWrapper<Report> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.gt(Report::getCreatedAt, startDate);
        queryWrapper.lt(Report::getCreatedAt, endDate);
        queryWrapper.eq(Report::getDisplay, 0);
        queryWrapper.eq(Report::getType, ReportType.AI.getType());
        queryWrapper.last(" group by camera_id");
        return this.getBaseMapper().selectCamera(queryWrapper);
    }

    /**
     *
     * @param startDate
     * @param endDate
     * @return
     */
    @Override
    public List<Map<String, Object>> findCameraAlgorithm(Date startDate, Date endDate) {
        LambdaQueryWrapper<Report> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.gt(Report::getCreatedAt, startDate);
        queryWrapper.lt(Report::getCreatedAt, endDate);
        queryWrapper.eq(Report::getDisplay, 0);
        queryWrapper.eq(Report::getType, ReportType.AI.getType());
        queryWrapper.last(" group by camera_id, algorithm_id");
        return this.getBaseMapper().selectCameraAlgorithm(queryWrapper);
    }

    /**
     *
     * @param id
     * @param display
     */
    @Override
    public void updateDisplay(Long id, Integer display) {
        LambdaUpdateWrapper<Report> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(Report::getDisplay, display)
                .eq(Report::getId, id);
        this.getBaseMapper().update(null, updateWrapper);
    }

    /**
     * 瀹℃牳
     *
     * @param id
     * @param result
     */
    @Override
    public void updateAudit(Long id, Integer result) {
        LambdaUpdateWrapper<Report> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(Report::getAuditState, 1)
                .set(Report::getAuditResult, result)
                .eq(Report::getId, id);
        this.getBaseMapper().update(null, updateWrapper);
    }

    /**
     * @param baseDir
     * @param algorithmId
     * @param cameraId
     * @param startDate
     * @param endDate
     * @return
     */
    @Override
    public String export(String baseDir, Long algorithmId, Long cameraId, Date startDate, Date endDate, Integer auditState) {
        // 鍒涘缓瀵煎嚭鐩綍
        String datePath = DateUtil.format(new Date(), "yyyyMMddHHmmss");
        String zipName = "export_" + datePath + ".zip";
        String exportDir = "export_" + datePath + "/";
        File folder = new File(baseDir + exportDir);
        if(folder.exists()) {
            folder.delete();
        }
        folder.mkdirs();

        // 姝ｇ‘杈撳嚭鐩綍
        File okFolder = new File(baseDir + exportDir + "ok/");
        okFolder.mkdirs();

        // 閿欒杈撳嚭鐩綍
        File failFolder = new File(baseDir + exportDir + "fail/");
        failFolder.mkdirs();

        //
        LambdaQueryWrapper<Report> queryWrapper = new LambdaQueryWrapper<>();
        if(algorithmId != null) {
            queryWrapper.eq(Report::getAlgorithmId, algorithmId);
        }
        if(cameraId != null) {
            queryWrapper.eq(Report::getCameraId, cameraId);
        }
        if(startDate != null) {
            queryWrapper.gt(Report::getCreatedAt, startDate);
        }
        if(endDate != null) {
            queryWrapper.lt(Report::getCreatedAt, endDate);
        }
        if(auditState != null) {
            if(auditState == 1) {
                queryWrapper.eq(Report::getAuditState, 1); // 宸插鏍哥殑鏁版嵁
            } else if(auditState == 2) {
                queryWrapper.eq(Report::getAuditState, 0); // 寰呭鏍哥殑鏁版嵁
            }
        }

        //
        List<Report> reportList = this.list(queryWrapper);
        if(reportList == null || reportList.isEmpty()) {
            return null;
        }

        //
        for(Report report : reportList) {
            try {
                String filePath = report.getFileName();
                String fileName = FileUtil.getName(filePath);
                String extName = FileUtil.extName(fileName);
                String shortName = fileName.replace(("." + extName), "");

                if (report.getAuditResult() != null && report.getAuditResult() == 1) { // 姝ｇ‘
                    FileUtil.writeString(report.getParams(), okFolder.getAbsolutePath() + "/" + shortName + ".json", StandardCharsets.UTF_8);
                    FileUtil.copy(filePath, okFolder.getAbsolutePath() + "/", true);
                } else { // 閿欒
                    FileUtil.writeString(report.getParams(), failFolder.getAbsolutePath() + "/" + shortName + ".json", StandardCharsets.UTF_8);
                    FileUtil.copy(filePath, failFolder.getAbsolutePath() + "/", true);
                }
            } catch (Exception e) {
                // file not exist
            }
        }

        //
        ZipUtil.zip(folder.getAbsolutePath(), baseDir + zipName);
        return zipName;
    }

    /**
     * 鏍规嵁绠楁硶id锛屽紑濮嬪拰缁撴潫姣鍊兼煡璇㈡€绘暟
     *
     * @param algorithmId
     * @param startMills
     * @param endMills
     * @return
     */
    @Override
    public Integer getAlgorithmCounter(Long algorithmId, long startMills, long endMills) {
        LambdaQueryWrapper<Report> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Report::getAlgorithmId, algorithmId);
        queryWrapper.gt(Report::getCreatedMills, startMills);
        queryWrapper.lt(Report::getCreatedMills, endMills);
        queryWrapper.eq(Report::getDisplay, 0);
        return Math.toIntExact(this.count(queryWrapper));
    }

    /**
     * 寮€濮嬪拰缁撴潫姣鍊兼煡璇㈡€绘暟
     *
     * @param startMills
     * @param endMills
     * @return
     */
    @Override
    public int getCounter(long startMills, long endMills) {
        LambdaQueryWrapper<Report> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.gt(Report::getCreatedMills, startMills);
        queryWrapper.lt(Report::getCreatedMills, endMills);
        queryWrapper.eq(Report::getDisplay, 0);
        return Math.toIntExact(this.count(queryWrapper));
    }

    /**
     * 鍒嗛〉鏌ヨ
     *
     * @param objectPage
     * @param cameraId
     * @param algorithmId
     * @param startMills
     * @param endMills
     * @return
     */
    @Override
    public IPage<Report> listByPage(IPage<Report> objectPage, Long cameraId, Long algorithmId, Integer type, Long startMills, Long endMills) {
        LambdaQueryWrapper<Report> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Report::getDisplay, 0);
        if(cameraId != null) {
            queryWrapper.eq(Report::getCameraId, cameraId);
        }
        if(algorithmId != null) {
            queryWrapper.eq(Report::getAlgorithmId, algorithmId);
        }
        if(startMills != null) {
            queryWrapper.ge(Report::getCreatedMills, startMills);
        }
        if(endMills != null) {
            queryWrapper.lt(Report::getCreatedMills, endMills);
        }
        if(type != null) {
            queryWrapper.eq(Report::getType, type);
        }
        queryWrapper.orderByDesc(Report::getCreatedMills);
        return this.page(objectPage, queryWrapper);
    }

    /**
     * 鏌ヨ鏈€杩?澶╄褰?     *
     * @param nums
     * @return
     */
    @Override
    public List<Report> listNewly(int nums) {
        LambdaQueryWrapper<Report> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Report::getDisplay, 0);
        queryWrapper.orderByDesc(Report::getCreatedMills);
        queryWrapper.last(" limit 0, " + nums + " ");
        return this.list(queryWrapper);
    }

    /**
     * 缁熻鎬绘暟
     *
     * @param startMills
     * @param endMills
     * @param cameraId
     * @param algorithmId
     * @return
     */
    @Override
    public Integer getCount(Long startMills, Long endMills, Long cameraId, Long algorithmId, Integer type) {
        LambdaQueryWrapper<Report> queryWrapper = new LambdaQueryWrapper<>();
        //
        if(startMills != null) {
            queryWrapper.gt(Report::getCreatedMills, startMills);
        }
        //
        if(endMills != null) {
            queryWrapper.lt(Report::getCreatedMills, endMills);
        }
        //
        if(cameraId != null) {
            queryWrapper.eq(Report::getCameraId, cameraId);
        }
        //
        if(algorithmId != null) {
            queryWrapper.eq(Report::getAlgorithmId, algorithmId);
        }
        //
        if(type != null) {
            queryWrapper.eq(Report::getType, type);
        }
        queryWrapper.eq(Report::getDisplay, 0);
        return Math.toIntExact(this.count(queryWrapper));
    }

    /**
     * 缁熻鎬绘暟
     *
     * @param startMills
     * @param endMills
     * @param cameraId
     * @param algorithmId
     * @return
     */
    @Override
    public Integer getMarkCount(Long startMills, Long endMills, Long cameraId, Long algorithmId) {
        LambdaQueryWrapper<Report> queryWrapper = new LambdaQueryWrapper<>();
        //
        if(startMills != null) {
            queryWrapper.gt(Report::getCreatedMills, startMills);
        }
        //
        if(endMills != null) {
            queryWrapper.lt(Report::getCreatedMills, endMills);
        }
        //
        if(cameraId != null) {
            queryWrapper.eq(Report::getCameraId, cameraId);
        }
        //
        if(algorithmId != null) {
            queryWrapper.eq(Report::getAlgorithmId, algorithmId);
        }
        queryWrapper.eq(Report::getDisplay, 0);
        queryWrapper.eq(Report::getAuditState, 1);
        return Math.toIntExact(this.count(queryWrapper));
    }

    /**
     * 鏍规嵁绠楁硶缁熻鏁伴噺
     *
     * @param startMills
     * @param endMills
     * @return
     */
    @Override
    public Map<Long, Integer> getCountByAlgorithm(Long startMills, Long endMills) {
        Map<String, Object> params = new HashMap<>();
        //
        if(startMills != null) {
            params.put("startMills", startMills);
        }
        //
        if(endMills != null) {
            params.put("endMills", endMills);
        }

        //
        List<Map<String, Object>> datas = this.getBaseMapper().selectAlgorithmStatics(params);
        if(datas == null) {
            return new HashMap<>();
        }
        //
        Map<Long, Integer> resMap = new HashMap<>();
        //
        for(Map<String, Object> data : datas) {
            Long algorithmId = Convert.toLong(data.get("algorithm_id"), 0l);
            Integer count = Convert.toInt(data.get("ct"), 0);
            //
            resMap.put(algorithmId, count);
        }
        return resMap;
    }
}
