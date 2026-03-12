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

        return "index";
    }

    @GetMapping("/blank")
    public String blank() {
        return "blank";
    }
}

