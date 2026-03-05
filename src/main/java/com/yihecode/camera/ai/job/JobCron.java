package com.yihecode.camera.ai.job;

import com.yihecode.camera.ai.web.api.ReportDiscard;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 框架内简单任务调度
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Component
@EnableScheduling
public class JobCron {

    /**
     * 每5分钟清除超过时间的ReportFilter时间记录值
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void job2() {
        try {
            ReportDiscard.getInst().remove();
        } catch (Exception e) {
            System.out.println("ReportFilter时间记录值清除异常：" + e.getMessage());
        }
    }
}
