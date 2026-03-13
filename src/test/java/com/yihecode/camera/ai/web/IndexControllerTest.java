package com.yihecode.camera.ai.web;

import com.yihecode.camera.ai.entity.Account;
import com.yihecode.camera.ai.service.AccountService;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.RoleAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
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

        IndexController controller = spy(indexController);
        doReturn(1L).when(controller).currentAccountId();

        ModelMap modelMap = new ModelMap();
        String view = controller.index(modelMap);

        assertEquals("index", view);
        assertEquals("/static/admin/images/logo.png", modelMap.get("brandLogoUrl"));
        assertTrue(String.valueOf(modelMap.get("brandTitle")).trim().length() > 0);
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

        IndexController controller = spy(indexController);
        doReturn(2L).when(controller).currentAccountId();

        ModelMap modelMap = new ModelMap();
        String view = controller.index(modelMap);

        assertEquals("index", view);
        assertEquals("Edge Vision", modelMap.get("brandTitle"));
        assertEquals("/image/stream?fileName=logo.png", modelMap.get("brandLogoUrl"));
        assertEquals("ops", modelMap.get("accountRole"));
    }
}
