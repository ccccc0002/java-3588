package com.yihecode.camera.ai.web;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.alibaba.fastjson.JSONObject;
import com.yihecode.camera.ai.service.OperationLogService;
import com.yihecode.camera.ai.utils.PageResult;
import com.yihecode.camera.ai.utils.PageResultUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SaCheckLogin
@Controller
@RequestMapping({"/operationlog"})
public class OperationLogController {

    @Autowired
    private OperationLogService operationLogService;

    @GetMapping({"", "/"})
    public String index(ModelMap modelMap) {
        return "operationlog/index";
    }

    @PostMapping({"/listPage"})
    @ResponseBody
    public PageResult listPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer limit,
            String operatorName,
            String role,
            String action,
            Integer success,
            String startText,
            String endText
    ) {
        List<JSONObject> all = operationLogService.list(operatorName, role, action, success, startText, endText);
        if (all == null) {
            all = new ArrayList<>();
        }
        Collections.reverse(all);
        int total = all.size();
        int from = Math.max((page - 1) * limit, 0);
        int to = Math.min(from + limit, total);
        List<JSONObject> rows = from >= to ? new ArrayList<>() : all.subList(from, to);
        return PageResultUtils.success((long) total, rows);
    }
}

