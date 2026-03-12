package com.yihecode.camera.ai.web;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.yihecode.camera.ai.entity.Account;
import com.yihecode.camera.ai.service.AccountService;
import com.yihecode.camera.ai.service.OperationLogService;
import com.yihecode.camera.ai.service.RoleAccessService;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import com.yihecode.camera.ai.utils.PageResult;
import com.yihecode.camera.ai.utils.PageResultUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SaCheckLogin
@Controller
@RequestMapping({"/account"})
public class AccountController {

    @Autowired
    private AccountService accountService;

    @Autowired
    private RoleAccessService roleAccessService;

    @Autowired
    private OperationLogService operationLogService;

    @GetMapping({"", "/"})
    public String index() {
        return "account/index";
    }

    @GetMapping({"/form"})
    public String form(Long id, ModelMap modelMap) {
        if (id != null) {
            modelMap.addAttribute("account", this.accountService.getById(id));
            modelMap.addAttribute("accountRole", roleAccessService.getRoleByAccountId(id));
        } else {
            modelMap.addAttribute("accountRole", RoleAccessService.ROLE_OPS);
        }
        modelMap.addAttribute("currentPermissions", roleAccessService.buildPermissionMatrix(currentAccountId()));
        return "account/form";
    }

    @GetMapping({"/password"})
    public String formPass() {
        return "account/password";
    }

    @PostMapping({"/listData"})
    @ResponseBody
    public PageResult listData() {
        List<Account> accountList = this.accountService.list();
        if (accountList == null) {
            accountList = new ArrayList<>();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Account account : accountList) {
            if (account == null) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("id", account.getId());
            row.put("name", account.getName());
            row.put("account", account.getAccount());
            row.put("state", account.getState());
            row.put("createdAt", account.getCreatedAt());
            row.put("updatedAt", account.getUpdatedAt());
            String role = roleAccessService.getRoleByAccountId(account.getId());
            row.put("role", role);
            row.put("roleLabel", roleLabel(role));
            rows.add(row);
        }
        return PageResultUtils.success(null, rows);
    }

    @PostMapping({"/permissions"})
    @ResponseBody
    public JsonResult permissions() {
        return JsonResultUtils.success(roleAccessService.buildPermissionMatrix(currentAccountId()));
    }

    @PostMapping({"/save"})
    @ResponseBody
    public JsonResult save(Account account, String role) {
        Long currentId = currentAccountId();
        if (!roleAccessService.canManageAccounts(currentId)) {
            return JsonResultUtils.fail("permission denied");
        }
        if (account == null) {
            return JsonResultUtils.fail("invalid account");
        }
        if (StrUtil.isBlank(account.getName())) {
            return JsonResultUtils.fail("name is required");
        }
        if (StrUtil.isBlank(account.getAccount())) {
            return JsonResultUtils.fail("account is required");
        }
        if (account.getId() != null) {
            account.setPassword(null);
        } else if (StrUtil.isBlank(account.getPassword())) {
            return JsonResultUtils.fail("password is required");
        } else {
            account.setPassword(SecureUtil.md5(account.getPassword()));
            account.setCreatedAt(new Date());
        }
        if (account.getState() == null) {
            return JsonResultUtils.fail("state is required");
        }
        account.setUpdatedAt(new Date());

        boolean isCreate = account.getId() == null;
        Account oldAccount = accountService.getByAccount(account.getAccount());
        if (isCreate) {
            if (oldAccount != null) {
                return JsonResultUtils.fail("account already exists");
            }
            this.accountService.save(account);
        } else {
            if (oldAccount != null && !oldAccount.getId().equals(account.getId())) {
                return JsonResultUtils.fail("account already exists");
            }
            this.accountService.saveOrUpdate(account);
        }

        roleAccessService.saveRole(account.getId(), role);
        operationLogService.record(
                isCreate ? "account:create" : "account:update",
                "account:" + account.getAccount(),
                true,
                "account saved",
                "role=" + role
        );
        return JsonResultUtils.success();
    }

    @PostMapping({"/delete"})
    @ResponseBody
    public JsonResult delete(Long id) {
        Long currentId = currentAccountId();
        if (!roleAccessService.canManageAccounts(currentId)) {
            return JsonResultUtils.fail("permission denied");
        }
        if (id == null) {
            return JsonResultUtils.fail("id is required");
        }
        if (id.equals(currentId)) {
            return JsonResultUtils.fail("cannot delete current login account");
        }
        Account target = accountService.getById(id);
        if (target == null) {
            return JsonResultUtils.fail("account not found");
        }
        this.accountService.removeById(id);
        roleAccessService.removeRole(id);
        operationLogService.record("account:delete", "account:" + target.getAccount(), true, "account deleted", "");
        return JsonResultUtils.success();
    }

    @PostMapping({"/password"})
    @ResponseBody
    public JsonResult updatePassword(String password) {
        if (StrUtil.isBlank(password)) {
            return JsonResultUtils.fail("new password is required");
        }
        Long id = currentAccountId();
        Account account = new Account();
        account.setId(id);
        account.setPassword(SecureUtil.md5(password));
        accountService.saveOrUpdate(account);
        operationLogService.record("account:password", "account:id=" + id, true, "password changed", "");
        return JsonResultUtils.success();
    }

    private Long currentAccountId() {
        return StpUtil.getLoginIdAsLong();
    }

    private String roleLabel(String role) {
        if (RoleAccessService.ROLE_SUPER_ADMIN.equals(role)) {
            return "超级管理员";
        }
        if (RoleAccessService.ROLE_OPS.equals(role)) {
            return "运维人员";
        }
        return "只读用户";
    }
}
