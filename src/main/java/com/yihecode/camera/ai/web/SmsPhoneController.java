package com.yihecode.camera.ai.web;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.core.util.StrUtil;
import com.yihecode.camera.ai.entity.SmsPhone;
import com.yihecode.camera.ai.service.SmsPhoneService;
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
import java.util.List;

/**
 * 短信推送手机号码管理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@SaCheckLogin
@Controller
@RequestMapping({"/smsphone"})
public class SmsPhoneController {

    @Autowired
    private SmsPhoneService smsPhoneService;

    /**
     *
     * @return
     */
    @GetMapping({"", "/"})
    public String index() {
        return "smsphone/index";
    }

    /**
     *
     * @param modelMap
     * @return
     */
    @GetMapping({"/form"})
    public String form(ModelMap modelMap) {
        return "smsphone/form";
    }

    /**
     *
     * @return
     */
    @PostMapping({"/listData"})
    @ResponseBody
    public PageResult listData() {
        List<SmsPhone> smsPhoneList = this.smsPhoneService.list();
        if (smsPhoneList == null) {
            smsPhoneList = new ArrayList<>();
        }
        return PageResultUtils.success(null, smsPhoneList);
    }

    /**
     *
     * @param smsPhone
     * @return
     */
    @PostMapping({"/save"})
    @ResponseBody
    public JsonResult save(SmsPhone smsPhone) {
        if (StrUtil.isBlank(smsPhone.getPhone())) {
            return JsonResultUtils.fail("请输入手机号码");
        }
        smsPhoneService.save(smsPhone);
        //
        smsPhoneService.evictPhoneStr("test");
        return JsonResultUtils.success();
    }

    /**
     *
     * @param id
     * @return
     */
    @PostMapping({"/delete"})
    @ResponseBody
    public JsonResult delete(Long id) {
        this.smsPhoneService.removeById(id);
        //
        smsPhoneService.evictPhoneStr("test");
        return JsonResultUtils.success();
    }
}