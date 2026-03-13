package com.yihecode.camera.ai.web;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import com.yihecode.camera.ai.entity.Account;
import com.yihecode.camera.ai.service.AccountService;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.RoleAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {

    private static final String TAG_BRAND_TITLE = "brand_title";
    private static final String TAG_BRAND_LOGO_URL = "brand_logo_url";
    private static final String DEFAULT_BRAND_TITLE = "AI视频监控管理";
    private static final String DEFAULT_BRAND_LOGO_URL = "/static/admin/images/logo.png";

    @Autowired
    private ConfigService configService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private RoleAccessService roleAccessService;

    @SaCheckLogin
    @GetMapping({"", "/"})
    public String index(ModelMap modelMap) {
        String wsUrl = configService.getByValTag("wsUrl");
        modelMap.addAttribute("uid", IdUtil.fastSimpleUUID());
        modelMap.addAttribute("wsUrl", wsUrl);

        Long accountId = StpUtil.getLoginIdAsLong();
        Account account = accountService.getById(accountId);
        String accountName = account == null ? "unknown" : account.getName();
        String role = roleAccessService.getRoleByAccountId(accountId);
        modelMap.addAttribute("accountName", accountName);
        modelMap.addAttribute("accountRole", role);
        modelMap.addAttribute("brandTitle", getConfigOrDefault(TAG_BRAND_TITLE, DEFAULT_BRAND_TITLE));
        modelMap.addAttribute("brandLogoUrl", getConfigOrDefault(TAG_BRAND_LOGO_URL, DEFAULT_BRAND_LOGO_URL));

        return "index";
    }

    @GetMapping("/blank")
    public String blank() {
        return "blank";
    }

    private String getConfigOrDefault(String tag, String defaultValue) {
        String value = configService.getByValTag(tag);
        if (value == null) {
            return defaultValue;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? defaultValue : normalized;
    }
}
