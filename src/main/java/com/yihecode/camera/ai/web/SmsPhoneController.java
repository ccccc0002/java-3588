package com.yihecode.camera.ai.web;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.yihecode.camera.ai.entity.SmsPhone;
import com.yihecode.camera.ai.service.OperationLogService;
import com.yihecode.camera.ai.service.RoleAccessService;
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

@SaCheckLogin
@Controller
@RequestMapping({"/smsphone"})
public class SmsPhoneController {

    @Autowired
    private SmsPhoneService smsPhoneService;

    @Autowired
    private RoleAccessService roleAccessService;

    @Autowired
    private OperationLogService operationLogService;

    @GetMapping({"", "/"})
    public String index() {
        return "smsphone/index";
    }

    @GetMapping({"/form"})
    public String form(ModelMap modelMap) {
        return "smsphone/form";
    }

    @PostMapping({"/listData"})
    @ResponseBody
    public PageResult listData() {
        List<SmsPhone> smsPhoneList = this.smsPhoneService.list();
        if (smsPhoneList == null) {
            smsPhoneList = new ArrayList<>();
        }
        return PageResultUtils.success(null, smsPhoneList);
    }

    @PostMapping({"/save"})
    @ResponseBody
    public JsonResult save(SmsPhone smsPhone) {
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            operationLogService.record("smsphone:save", "phone=" + (smsPhone == null ? "" : smsPhone.getPhone()), false, "permission denied", "");
            return JsonResultUtils.fail("permission denied");
        }
        if (smsPhone == null || StrUtil.isBlank(smsPhone.getPhone())) {
            return JsonResultUtils.fail("phone is required");
        }
        smsPhoneService.save(smsPhone);
        smsPhoneService.evictPhoneStr("test");
        operationLogService.record("smsphone:save", "phone=" + smsPhone.getPhone(), true, "sms phone saved", "");
        return JsonResultUtils.success();
    }

    @PostMapping({"/delete"})
    @ResponseBody
    public JsonResult delete(Long id) {
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            operationLogService.record("smsphone:delete", "id=" + id, false, "permission denied", "");
            return JsonResultUtils.fail("permission denied");
        }
        this.smsPhoneService.removeById(id);
        smsPhoneService.evictPhoneStr("test");
        operationLogService.record("smsphone:delete", "id=" + id, true, "sms phone deleted", "");
        return JsonResultUtils.success();
    }

    private Long currentAccountId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }
}
