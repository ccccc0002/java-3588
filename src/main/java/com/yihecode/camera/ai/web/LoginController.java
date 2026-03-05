package com.yihecode.camera.ai.web;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.SecureUtil;
import com.yihecode.camera.ai.entity.Account;
import com.yihecode.camera.ai.service.AccountService;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 登录控制
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Controller
public class LoginController {

    @Autowired
    private AccountService accountService;

    /**
     * 打开登录页面
     * @return
     */
    @GetMapping({"/login"})
    public String login() {
        return "login";
    }

    /**
     * 登录控制
     * @return
     */
    @PostMapping({"/login"})
    @ResponseBody
    public JsonResult doLogin(String account, String password) {
        Account account1 = accountService.getByAccount(account);
        if(account1 == null) {
            return JsonResultUtils.fail("账号或密码错误");
        }
        //
        String md5 = SecureUtil.md5(password);
        if(!md5.equals(account1.getPassword())) {
            return JsonResultUtils.fail("账号或密码错误");
        }
        //
        if(!(account1.getState() != null && account1.getState() == 0)) {
            return JsonResultUtils.fail("账号已失效");
        }
        //
        StpUtil.login(account1.getId());

        return JsonResultUtils.success();
    }

    /**
     * 登出控制
     * @return
     */
    @GetMapping(value = "/logout")
    public String logout() {
        StpUtil.logout();
        StpUtil.getSession(true).logout();
        return "redirect:/login";
    }

    public static void main(String[] args) {
        String md5 = SecureUtil.md5("abc");
        System.out.println(md5);
    }
}
