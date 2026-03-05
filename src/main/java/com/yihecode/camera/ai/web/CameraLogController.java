package com.yihecode.camera.ai.web;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yihecode.camera.ai.entity.CameraLog;
import com.yihecode.camera.ai.service.CameraLogService;
import com.yihecode.camera.ai.utils.PageResult;
import com.yihecode.camera.ai.utils.PageResultUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 摄像头取图日志，适配中化定制需求
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@SaCheckLogin
@Controller
@RequestMapping({"/cameralog"})
public class CameraLogController {

    @Autowired
    private CameraLogService cameraLogService;

    /**
     * 打开日志页面
     * @return
     */
    @GetMapping({"", "/"})
    public String index(String indexCode, ModelMap modelMap) {
        modelMap.addAttribute("indexCode", indexCode);
        return "cameralog/index";
    }

    /**
     * 查询数据列表
     * @return
     */
    @PostMapping({"/listPage"})
    @ResponseBody
    public PageResult listData(@RequestParam(defaultValue = "1") Integer page,
                               @RequestParam(defaultValue = "10") Integer limit,
                               @RequestParam(defaultValue = "") String indexCode) {
        IPage<CameraLog> pageObj = new Page<>(page, limit);
        IPage<CameraLog> pageResult = cameraLogService.listPage(pageObj, indexCode);
        return PageResultUtils.success(pageResult.getTotal(), pageResult.getRecords());
    }

}