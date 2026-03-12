package com.yihecode.camera.ai.service;

import com.yihecode.camera.ai.entity.Account;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationLogServiceTest {

    @Mock
    private ConfigService configService;

    @Mock
    private AccountService accountService;

    @Mock
    private RoleAccessService roleAccessService;

    @InjectMocks
    private OperationLogService operationLogService;

    @Test
    void listShouldFilterByRoleAndAction() {
        when(configService.getByValTag("operation_logs")).thenReturn("[{\"timestamp\":1710000000000,\"operator_name\":\"u1\",\"role\":\"ops\",\"action\":\"config:save\",\"success\":1}]");
        List<com.alibaba.fastjson.JSONObject> rows = operationLogService.list("u1", "ops", "config", 1, null, null);
        assertEquals(1, rows.size());
    }

    @Test
    void recordShouldNotThrowWhenNoSessionContext() {
        when(roleAccessService.getRoleByAccountId(any())).thenReturn("ops");
        operationLogService.record("test:action", "target", true, "ok", "");
        List<com.alibaba.fastjson.JSONObject> rows = operationLogService.list(null, null, null, null, null, null);
        assertNotNull(rows);
    }
}
