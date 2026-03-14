package com.yihecode.camera.ai.web;

import com.yihecode.camera.ai.entity.SmsPhone;
import com.yihecode.camera.ai.service.OperationLogService;
import com.yihecode.camera.ai.service.RoleAccessService;
import com.yihecode.camera.ai.service.SmsPhoneService;
import com.yihecode.camera.ai.utils.JsonResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.web.bind.annotation.RequestMapping;

@ExtendWith(MockitoExtension.class)
class SmsPhoneControllerTest {

    @Mock
    private SmsPhoneService smsPhoneService;

    @Mock
    private RoleAccessService roleAccessService;

    @Mock
    private OperationLogService operationLogService;

    @InjectMocks
    private SmsPhoneController smsPhoneController;

    @BeforeEach
    void setUp() {
        lenient().when(roleAccessService.canWriteSystem(any())).thenReturn(true);
    }

    @Test
    void saveShouldFailWhenPermissionDenied() {
        when(roleAccessService.canWriteSystem(any())).thenReturn(false);
        SmsPhone smsPhone = new SmsPhone();
        smsPhone.setPhone("13800000000");

        JsonResult result = smsPhoneController.save(smsPhone);

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(smsPhoneService, never()).save(any());
    }

    @Test
    void deleteShouldFailWhenPermissionDenied() {
        when(roleAccessService.canWriteSystem(any())).thenReturn(false);

        JsonResult result = smsPhoneController.delete(1L);

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(smsPhoneService, never()).removeById(1L);
    }

    @Test
    void requestMappingShouldContainPushChannelAlias() {
        RequestMapping mapping = SmsPhoneController.class.getAnnotation(RequestMapping.class);
        assertTrue(mapping != null);
        assertTrue(java.util.Arrays.asList(mapping.value()).contains("/smsphone"));
        assertTrue(java.util.Arrays.asList(mapping.value()).contains("/push/channel"));
    }

    @Test
    void indexShouldReturnTemplate() {
        assertEquals("smsphone/index", smsPhoneController.index());
    }
}
