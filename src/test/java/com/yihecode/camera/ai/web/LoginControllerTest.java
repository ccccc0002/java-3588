package com.yihecode.camera.ai.web;

import com.yihecode.camera.ai.service.AccountService;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.OperationLogService;
import com.yihecode.camera.ai.service.RoleAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginControllerTest {

    @Mock
    private AccountService accountService;

    @Mock
    private ConfigService configService;

    @Mock
    private RoleAccessService roleAccessService;

    @Mock
    private OperationLogService operationLogService;

    @InjectMocks
    private LoginController loginController;

    @Test
    void loginPageShouldUseDefaultBrandingWhenConfigMissing() {
        when(configService.getByValTag("brand_title")).thenReturn(null);
        when(configService.getByValTag("login_background_url")).thenReturn(null);

        ModelMap modelMap = new ModelMap();
        String view = loginController.login(modelMap);

        assertEquals("login", view);
        assertEquals("AI视频监控管理系统", modelMap.get("brandTitle"));
        assertEquals("/static/admin/images/background.svg", modelMap.get("loginBackgroundUrl"));
    }

    @Test
    void loginPageShouldUseConfiguredBranding() {
        when(configService.getByValTag("brand_title")).thenReturn("Edge Vision");
        when(configService.getByValTag("login_background_url")).thenReturn("/image/stream?fileName=bg.png");

        ModelMap modelMap = new ModelMap();
        String view = loginController.login(modelMap);

        assertEquals("login", view);
        assertEquals("Edge Vision", modelMap.get("brandTitle"));
        assertEquals("/image/stream?fileName=bg.png", modelMap.get("loginBackgroundUrl"));
    }
}
