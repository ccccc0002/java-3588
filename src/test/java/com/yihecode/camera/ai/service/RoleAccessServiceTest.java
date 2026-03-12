package com.yihecode.camera.ai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleAccessServiceTest {

    @Mock
    private ConfigService configService;

    @InjectMocks
    private RoleAccessService roleAccessService;

    @Test
    void getRoleByAccountIdShouldFallbackById() {
        when(configService.getByValTag("rbac_account_roles")).thenReturn("");

        assertEquals(RoleAccessService.ROLE_SUPER_ADMIN, roleAccessService.getRoleByAccountId(1L));
        assertEquals(RoleAccessService.ROLE_OPS, roleAccessService.getRoleByAccountId(2L));
    }

    @Test
    void getRoleByAccountIdShouldReadConfigMap() {
        when(configService.getByValTag("rbac_account_roles")).thenReturn("{\"2\":\"read_only\"}");
        assertEquals(RoleAccessService.ROLE_READ_ONLY, roleAccessService.getRoleByAccountId(2L));
    }

    @Test
    void buildPermissionMatrixShouldReturnExpectedFlags() {
        when(configService.getByValTag("rbac_account_roles")).thenReturn("{\"3\":\"ops\"}");
        Map<String, Object> matrix = roleAccessService.buildPermissionMatrix(3L);
        assertEquals("ops", matrix.get("role"));
        assertEquals(false, matrix.get("can_manage_accounts"));
        assertEquals(true, matrix.get("can_write_system"));
        assertTrue(((java.util.Set<String>) matrix.get("permissions")).contains("warehouse:sync"));
    }
}

