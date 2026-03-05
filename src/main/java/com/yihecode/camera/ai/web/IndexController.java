package com.yihecode.camera.ai.web;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import com.yihecode.camera.ai.entity.Account;
import com.yihecode.camera.ai.service.AccountService;
import com.yihecode.camera.ai.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 系统首页
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Controller
public class IndexController {

    //
    @Autowired
    private ConfigService configService;

    //
    @Autowired
    private AccountService accountService;

    /**
     *
     * @return
     */
    @GetMapping({"", "/"})
    public String index(ModelMap modelMap) {
        String wsUrl = configService.getByValTag("wsUrl");
        modelMap.addAttribute("uid", IdUtil.fastSimpleUUID());
        modelMap.addAttribute("wsUrl", wsUrl);

        //
        modelMap.addAttribute("accountName", "test");

        //
        return "index";
    }

    @GetMapping("/blank")
    public String blank() {
        return "blank";
    }
}
