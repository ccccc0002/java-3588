package com.yihecode.camera.ai.web;

import com.yihecode.camera.ai.entity.Account;
import com.yihecode.camera.ai.service.AccountService;
import com.yihecode.camera.ai.service.OperationLogService;
import com.yihecode.camera.ai.service.RoleAccessService;
import com.yihecode.camera.ai.utils.JsonResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private AccountService accountService;

    @Mock
    private RoleAccessService roleAccessService;

    @Mock
    private OperationLogService operationLogService;

    @InjectMocks
    private AccountController accountController;

    @BeforeEach
    void setUp() {
        lenient().when(roleAccessService.canManageAccounts(any())).thenReturn(true);
    }

    @Test
    void saveShouldFailWhenPermissionDenied() {
        when(roleAccessService.canManageAccounts(any())).thenReturn(false);
        Account account = new Account();
        account.setAccount("demo-user");

        JsonResult result = accountController.save(account, RoleAccessService.ROLE_OPS);

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(accountService, never()).save(any(Account.class));
        verify(accountService, never()).saveOrUpdate(any(Account.class));
        verify(operationLogService).record(eq("account:save"), eq("account:demo-user"), eq(false), eq("permission denied"), eq(""));
    }

    @Test
    void deleteShouldFailWhenPermissionDenied() {
        when(roleAccessService.canManageAccounts(any())).thenReturn(false);

        JsonResult result = accountController.delete(3L);

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(accountService, never()).getById(any());
        verify(operationLogService).record(eq("account:delete"), eq("accountId=3"), eq(false), eq("permission denied"), eq(""));
    }
}
