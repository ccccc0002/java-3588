package com.yihecode.camera.ai.job;

import com.yihecode.camera.ai.service.ActiveCameraInferenceSchedulerService;
import com.yihecode.camera.ai.web.api.ReportDiscard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 妗嗘灦鍐呯畝鍗曚换鍔¤皟搴? * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Slf4j
@Component
@EnableScheduling
public class JobCron {

    @Autowired(required = false)
    private ActiveCameraInferenceSchedulerService activeCameraInferenceSchedulerService;

    /**
     * 姣?绉掓墽琛屼竴娆℃椿鍔ㄦ憚鍍忓ご鎺ㄧ悊璋冨害
     */
    @Scheduled(fixedDelayString = "${inference.scheduler.fixed-delay-ms:5000}", initialDelayString = "${inference.scheduler.initial-delay-ms:15000}")
    public void jobInferenceDispatch() {
        try {
            if (activeCameraInferenceSchedulerService != null) {
                activeCameraInferenceSchedulerService.dispatchActiveCameras();
            }
        } catch (Exception e) {
            log.warn("active camera inference scheduler failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 姣?鍒嗛挓娓呴櫎瓒呰繃鏃堕棿鐨凴eportFilter鏃堕棿璁板綍鍊?     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void job2() {
        try {
            ReportDiscard.getInst().remove();
        } catch (Exception e) {
            log.warn("report discard cleanup failed: {}", e.getMessage(), e);
        }
    }
}
