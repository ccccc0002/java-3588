package com.yihecode.camera.ai.job;

import com.yihecode.camera.ai.service.ActiveCameraInferenceSchedulerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobCronTest {

    @Mock
    private ActiveCameraInferenceSchedulerService activeCameraInferenceSchedulerService;

    @InjectMocks
    private JobCron jobCron;

    @Test
    void jobInferenceDispatch_shouldDelegateToSchedulerService() {
        jobCron.jobInferenceDispatch();

        verify(activeCameraInferenceSchedulerService, times(1)).dispatchActiveCameras();
    }

    @Test
    void jobInferenceDispatch_shouldSwallowSchedulerExceptions() {
        doThrow(new IllegalStateException("boom")).when(activeCameraInferenceSchedulerService).dispatchActiveCameras();

        jobCron.jobInferenceDispatch();

        verify(activeCameraInferenceSchedulerService, times(1)).dispatchActiveCameras();
    }
}
