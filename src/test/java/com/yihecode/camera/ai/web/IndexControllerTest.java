package com.yihecode.camera.ai.web;

import cn.dev33.satoken.stp.StpUtil;
import com.yihecode.camera.ai.entity.Account;
import com.yihecode.camera.ai.service.AccountService;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.RoleAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndexControllerTest {

    @Mock
    private ConfigService configService;

    @Mock
    private AccountService accountService;

    @Mock
    private RoleAccessService roleAccessService;

    @InjectMocks
    private IndexController indexController;

    @Test
    void indexShouldInjectBrandingDefaultsWhenConfigMissing() {
        when(configService.getByValTag("wsUrl")).thenReturn("ws://127.0.0.1:18083/ws");
        when(configService.getByValTag("brand_title")).thenReturn(null);
        when(configService.getByValTag("brand_logo_url")).thenReturn(null);

        Account account = new Account();
        account.setName("admin");
        when(accountService.getById(1L)).thenReturn(account);
        when(roleAccessService.getRoleByAccountId(1L)).thenReturn("super_admin");

        ModelMap modelMap = new ModelMap();
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            String view = indexController.index(modelMap);

            assertEquals("index", view);
            assertEquals("AI视频监控管理", modelMap.get("brandTitle"));
            assertEquals("/static/admin/images/logo.png", modelMap.get("brandLogoUrl"));
        }
    }

    @Test
    void indexShouldInjectConfiguredBrandingValues() {
        when(configService.getByValTag("wsUrl")).thenReturn("ws://127.0.0.1:18083/ws");
        when(configService.getByValTag("brand_title")).thenReturn("Edge Vision");
        when(configService.getByValTag("brand_logo_url")).thenReturn("/image/stream?fileName=logo.png");

        Account account = new Account();
        account.setName("ops");
        when(accountService.getById(2L)).thenReturn(account);
        when(roleAccessService.getRoleByAccountId(2L)).thenReturn("ops");

        ModelMap modelMap = new ModelMap();
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);

            String view = indexController.index(modelMap);

            assertEquals("index", view);
            assertEquals("Edge Vision", modelMap.get("brandTitle"));
            assertEquals("/image/stream?fileName=logo.png", modelMap.get("brandLogoUrl"));
            assertEquals("ops", modelMap.get("accountRole"));
        }
    }
}
