package com.yihecode.camera.ai.web;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.util.StrUtil;
import com.yihecode.camera.ai.entity.Config;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.VideoPlayService;
import com.yihecode.camera.ai.utils.*;
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
 * 系统配置管理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@SaCheckLogin
@Controller
@RequestMapping({"/config"})
public class ConfigController {

    @Autowired
    private ConfigService configService;

    @Autowired
    private VideoPlayService videoPlayService;

    /**
     * 打开配置页
     * @return
     */
    @GetMapping({"", "/"})
    public String index() {
        return "config/index";
    }

    /**
     * 打开配置表单
     * @param id
     * @param modelMap
     * @return
     */
    @GetMapping({"/form"})
    public String form(Long id, ModelMap modelMap) {
        if (id == null) {
            return "config/form";
        }
        modelMap.addAttribute("config", this.configService.getById(id));
        return "config/form";
    }

    /**
     * 配置详情
     * @param id
     * @return
     */
    @PostMapping({"/detail"})
    @ResponseBody
    public JsonResult detail(Long id) {
        Config config = configService.getById(id);
        if(config == null) {
            return JsonResultUtils.fail("找不到数据");
        }
        return JsonResultUtils.success(config);
    }

    /**
     * 查询配置列表
     * @return
     */
    @PostMapping({"/listData"})
    @ResponseBody
    public PageResult listData() {
        List<Config> configList = this.configService.list();
        if (configList == null) {
            configList = new ArrayList<>();
        }

        //
        for(Config config : configList) {
            if("wework_url".equals(config.getTag())) {
                config.setVal(StrUtils.hide(config.getVal()));
            }
        }
        return PageResultUtils.success(null, configList);
    }

    /**
     * 保存配置
     * @param config
     * @return
     */
    @PostMapping({"/save"})
    @ResponseBody
    public JsonResult save(Config config) {
        if (StrUtil.isBlank(config.getName())) {
            return JsonResultUtils.fail("请输入配置名称");
        }
        if (StrUtil.isBlank(config.getTag())) {
            return JsonResultUtils.fail("请输入配置标识");
        }
        if (StrUtil.isBlank(config.getVal())) {
            return JsonResultUtils.fail("请输入配置值");
        }
        this.configService.saveOrUpdate(config);

        //
        configService.evictByTag(config.getTag());

        return JsonResultUtils.success();
    }

    /**
     * 删除配置
     * @param id
     * @return
     */
    @PostMapping({"/delete"})
    @ResponseBody
    public JsonResult delete(Long id) {
        Config config = configService.getById(id);
        if(config == null) {
            return JsonResultUtils.fail("数据不存在");
        }
        this.configService.removeById(id);
        this.configService.evictByTag(config.getTag());

        // 删除配置的推流地址
        if("streamType".equals(config.getTag())) {
            videoPlayService.removeAll();
        }

        return JsonResultUtils.success();
    }

    /**
     * 测试
     * @return
     */
    @SaIgnore
    @RequestMapping("/test")
    @ResponseBody
    public JsonResult test() {
        String wsUrl = configService.getByValTag("wsUrl");
        return JsonResultUtils.success(wsUrl);
    }
}