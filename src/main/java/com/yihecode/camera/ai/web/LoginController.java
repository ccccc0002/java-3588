package com.yihecode.camera.ai.web;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.yihecode.camera.ai.entity.Account;
import com.yihecode.camera.ai.service.AccountService;
import com.yihecode.camera.ai.service.OperationLogService;
import com.yihecode.camera.ai.service.RoleAccessService;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class LoginController {

    @Autowired
    private AccountService accountService;

    @Autowired
    private RoleAccessService roleAccessService;

    @Autowired
    private OperationLogService operationLogService;

    @GetMapping({"/login"})
    public String login() {
        return "login";
    }

    @PostMapping({"/login"})
    @ResponseBody
    public JsonResult doLogin(String account, String password) {
        if (StrUtil.isBlank(account) || StrUtil.isBlank(password)) {
            return JsonResultUtils.fail("account and password are required");
        }
        Account found = accountService.getByAccount(account);
        if (found == null) {
            return JsonResultUtils.fail("account or password invalid");
        }
        String md5 = SecureUtil.md5(password);
        if (!md5.equals(found.getPassword())) {
            return JsonResultUtils.fail("account or password invalid");
        }
        if (!(found.getState() != null && found.getState() == 0)) {
            return JsonResultUtils.fail("account is disabled");
        }

        StpUtil.login(found.getId());
        String role = roleAccessService.getRoleByAccountId(found.getId());
        StpUtil.getSession().set("account_name", found.getName());
        StpUtil.getSession().set("account", found.getAccount());
        StpUtil.getSession().set("role", role);
        operationLogService.record("login:success", "account:" + found.getAccount(), true, "login success", "");
        return JsonResultUtils.success();
    }

    @GetMapping(value = "/logout")
    public String logout() {
        operationLogService.record("login:logout", "account", true, "logout", "");
        StpUtil.logout();
        StpUtil.getSession(true).logout();
        return "redirect:/login";
    }
}

