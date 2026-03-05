package com.yihecode.camera.ai.web;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.yihecode.camera.ai.entity.Account;
import com.yihecode.camera.ai.service.AccountService;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import com.yihecode.camera.ai.utils.PageResult;
import com.yihecode.camera.ai.utils.PageResultUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import springfox.documentation.annotations.ApiIgnore;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 账号管理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Api(tags = "账号管理")
@SaCheckLogin
@Controller
@RequestMapping({"/account"})
public class AccountController {

    @Autowired
    private AccountService accountService;

    /**
     * 打开账号列表页面
     * @return
     */
    @ApiIgnore
    @GetMapping({"", "/"})
    public String index() {
        return "account/index";
    }

    /**
     * 打开账号表单页面
     * @param id
     * @param modelMap
     * @return
     */
    @ApiIgnore
    @GetMapping({"/form"})
    public String form(Long id, ModelMap modelMap) {
        if (id == null) {
            return "account/form";
        }
        modelMap.addAttribute("account", this.accountService.getById(id));
        return "account/form";
    }

    /**
     * 打开密码修改页面
     * @return
     */
    @ApiIgnore
    @GetMapping({"/password"})
    public String formPass() {
        return "account/password";
    }

    /**
     * 查询数据列表
     * @return
     */
    @ApiOperation(value = "查询数据列表")
    @PostMapping({"/listData"})
    @ResponseBody
    public PageResult listData() {
        List<Account> accountList = this.accountService.list();
        if (accountList == null) {
            accountList = new ArrayList<>();
        }
        return PageResultUtils.success(null, accountList);
    }

    /**
     * 保存数据
     * @param account
     * @return
     */
    @ApiOperation(value = "保存数据")
    @PostMapping({"/save"})
    @ResponseBody
    public JsonResult save(Account account) {
        if (StrUtil.isBlank(account.getName())) {
            return JsonResultUtils.fail("请输入员工姓名");
        }
        if (StrUtil.isBlank(account.getAccount())) {
            return JsonResultUtils.fail("请输入登录账号");
        }
        if (account.getId() != null) {
            account.setPassword(null);
        } else if (StrUtil.isBlank(account.getPassword())) {
            return JsonResultUtils.fail("请输入初始密码");
        } else {
            account.setPassword(SecureUtil.md5(account.getPassword()));
            account.setCreatedAt(new Date());
        }
        if (account.getState() == null) {
            return JsonResultUtils.fail("请选择账号状态");
        }
        account.setUpdatedAt(new Date());

        //

        if(account.getId() == null) {
            Account oldAccount = accountService.getByAccount(account.getAccount());
            if(oldAccount != null) {
                return JsonResultUtils.fail("账号已存在，请选择其他账号");
            }
            this.accountService.save(account);
        } else {
            Account oldAccount = accountService.getByAccount(account.getAccount());
            if(oldAccount != null && !oldAccount.getId().equals(account.getId())) {
                return JsonResultUtils.fail("账号已存在，请选择其他账号");
            }
            this.accountService.saveOrUpdate(account);
        }
        return JsonResultUtils.success();
    }

    /**
     * 删除数据
     * @param id
     * @return
     */
    @ApiOperation(value = "删除数据")
    @ApiImplicitParam(name = "id", value = "id")
    @PostMapping({"/delete"})
    @ResponseBody
    public JsonResult delete(Long id) {
        this.accountService.removeById(id);
        return JsonResultUtils.success();
    }

    /**
     * 修改密码
     * @return
     */
    @ApiOperation(value = "修改密码")
    @ApiImplicitParam(name = "password", value = "密码")
    @PostMapping({"/password"})
    @ResponseBody
    public JsonResult updatePassword(String password) {
        if(StrUtil.isBlank(password)) {
            return JsonResultUtils.fail("请输入新密码");
        }

        Long id = StpUtil.getLoginIdAsLong();
        String md5 = SecureUtil.md5(password);
        Account account = new Account();
        account.setId(id);
        account.setPassword(md5);
        this.accountService.saveOrUpdate(account);
        return JsonResultUtils.success();
    }
}