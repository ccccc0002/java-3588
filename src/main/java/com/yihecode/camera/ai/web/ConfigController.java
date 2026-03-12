package com.yihecode.camera.ai.web;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yihecode.camera.ai.entity.Camera;
import com.yihecode.camera.ai.entity.Config;
import com.yihecode.camera.ai.service.CameraService;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.OperationLogService;
import com.yihecode.camera.ai.service.RoleAccessService;
import com.yihecode.camera.ai.service.SystemProfileService;
import com.yihecode.camera.ai.service.VideoPlayService;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import com.yihecode.camera.ai.utils.PageResult;
import com.yihecode.camera.ai.utils.PageResultUtils;
import com.yihecode.camera.ai.utils.StrUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SaCheckLogin
@Controller
@RequestMapping({"/config"})
public class ConfigController {

    private static final String TAG_LICENSE_KEY = "license_key";
    private static final String TAG_LICENSE_MAX_CHANNELS = "license_max_channels";
    private static final String TAG_LICENSE_EXPIRE_AT = "license_expire_at";
    private static final String TAG_LICENSE_TENANT = "license_tenant";
    private static final String TAG_NETWORK_CONFIGS = "network_configurations";

    @Autowired
    private ConfigService configService;

    @Autowired
    private VideoPlayService videoPlayService;

    @Autowired
    private CameraService cameraService;

    @Autowired
    private SystemProfileService systemProfileService;

    @Autowired
    private RoleAccessService roleAccessService;

    @Autowired
    private OperationLogService operationLogService;

    @GetMapping({"", "/"})
    public String index() {
        return "config/index";
    }

    @GetMapping({"/form"})
    public String form(Long id, ModelMap modelMap) {
        if (id == null) {
            return "config/form";
        }
        modelMap.addAttribute("config", this.configService.getById(id));
        return "config/form";
    }

    @GetMapping({"/license"})
    public String licensePage() {
        return "config/license";
    }

    @GetMapping({"/network"})
    public String networkPage() {
        return "config/network";
    }

    @PostMapping({"/detail"})
    @ResponseBody
    public JsonResult detail(Long id) {
        Config config = configService.getById(id);
        if (config == null) {
            return JsonResultUtils.fail("Config not found");
        }
        return JsonResultUtils.success(config);
    }

    @PostMapping({"/listData"})
    @ResponseBody
    public PageResult listData() {
        List<Config> configList = this.configService.list();
        if (configList == null) {
            configList = new ArrayList<>();
        }

        for (Config config : configList) {
            if ("wework_url".equals(config.getTag())) {
                config.setVal(StrUtils.hide(config.getVal()));
            }
        }
        return PageResultUtils.success(null, configList);
    }

    @PostMapping({"/save"})
    @ResponseBody
    public JsonResult save(Config config) {
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            return JsonResultUtils.fail("permission denied");
        }
        if (StrUtil.isBlank(config.getName())) {
            return JsonResultUtils.fail("Config name is required");
        }
        if (StrUtil.isBlank(config.getTag())) {
            return JsonResultUtils.fail("Config tag is required");
        }
        if (StrUtil.isBlank(config.getVal())) {
            return JsonResultUtils.fail("Config value is required");
        }
        this.configService.saveOrUpdate(config);
        configService.evictByTag(config.getTag());
        operationLogService.record("config:save", "tag=" + config.getTag(), true, "config updated", "");
        return JsonResultUtils.success();
    }

    @PostMapping({"/delete"})
    @ResponseBody
    public JsonResult delete(Long id) {
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            return JsonResultUtils.fail("permission denied");
        }
        Config config = configService.getById(id);
        if (config == null) {
            return JsonResultUtils.fail("Config not found");
        }
        this.configService.removeById(id);
        this.configService.evictByTag(config.getTag());

        if ("streamType".equals(config.getTag())) {
            videoPlayService.removeAll();
        }

        operationLogService.record("config:delete", "tag=" + config.getTag(), true, "config deleted", "");
        return JsonResultUtils.success();
    }

    @SaIgnore
    @RequestMapping("/test")
    @ResponseBody
    public JsonResult test() {
        String wsUrl = configService.getByValTag("wsUrl");
        return JsonResultUtils.success(wsUrl);
    }

    @RequestMapping(value = "/license/info", method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public JsonResult licenseInfo() {
        List<Camera> cameras = cameraService.listData();
        int currentCameraCount = cameras == null ? 0 : cameras.size();

        String licenseKey = defaultString(configService.getByValTag(TAG_LICENSE_KEY));
        int maxChannels = parseInt(configService.getByValTag(TAG_LICENSE_MAX_CHANNELS), 0);
        String expireAt = defaultString(configService.getByValTag(TAG_LICENSE_EXPIRE_AT));
        String tenant = defaultString(configService.getByValTag(TAG_LICENSE_TENANT));

        boolean expired = isExpired(expireAt);
        boolean overLimit = maxChannels > 0 && currentCameraCount > maxChannels;
        boolean valid = StrUtil.isNotBlank(licenseKey) && !expired && !overLimit;

        Map<String, Object> data = new HashMap<>();
        data.put("device_id", systemProfileService.getDeviceId());
        data.put("license_key", licenseKey);
        data.put("max_channels", maxChannels);
        data.put("expire_at", expireAt);
        data.put("tenant", tenant);
        data.put("current_camera_count", currentCameraCount);
        data.put("valid", valid);
        data.put("expired", expired);
        data.put("over_limit", overLimit);
        return JsonResultUtils.success(data);
    }

    @PostMapping("/license/save")
    @ResponseBody
    public JsonResult saveLicense(
            String licenseKey,
            String license_key,
            Integer maxChannels,
            Integer max_channels,
            String expireAt,
            String expire_at,
            String tenant
    ) {
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            return JsonResultUtils.fail("permission denied");
        }
        String finalLicenseKey = StrUtil.isBlank(licenseKey) ? license_key : licenseKey;
        Integer finalMaxChannels = maxChannels == null ? max_channels : maxChannels;
        String finalExpireAt = StrUtil.isBlank(expireAt) ? expire_at : expireAt;

        if (StrUtil.isBlank(finalLicenseKey)) {
            return JsonResultUtils.fail("license_key is required");
        }
        if (finalMaxChannels == null || finalMaxChannels <= 0) {
            return JsonResultUtils.fail("max_channels must be greater than 0");
        }
        if (!isDateOrBlank(finalExpireAt)) {
            return JsonResultUtils.fail("expire_at must use yyyy-MM-dd format");
        }

        upsertConfig(TAG_LICENSE_KEY, "License Key", finalLicenseKey);
        upsertConfig(TAG_LICENSE_MAX_CHANNELS, "License Max Channels", String.valueOf(finalMaxChannels));
        upsertConfig(TAG_LICENSE_EXPIRE_AT, "License Expire At", defaultString(finalExpireAt));
        upsertConfig(TAG_LICENSE_TENANT, "License Tenant", defaultString(tenant));
        operationLogService.record("license:save", "license", true, "license updated", "max_channels=" + finalMaxChannels);
        return JsonResultUtils.success();
    }

    @RequestMapping(value = "/network/interfaces", method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public JsonResult listNetworkInterfaces() {
        return JsonResultUtils.success(systemProfileService.listNetworkInterfaces());
    }

    @RequestMapping(value = "/network/saved", method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public JsonResult listSavedNetworkConfigs() {
        return JsonResultUtils.success(loadNetworkConfigs());
    }

    @PostMapping("/network/save")
    @ResponseBody
    public JsonResult saveNetworkConfig(String interfaceName, String interface_name, String ip, String gateway, String dns) {
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            return JsonResultUtils.fail("permission denied");
        }
        String finalInterfaceName = StrUtil.isBlank(interfaceName) ? interface_name : interfaceName;
        if (StrUtil.isBlank(finalInterfaceName)) {
            return JsonResultUtils.fail("interface_name is required");
        }
        if (!isIpv4LikeOrBlank(ip)) {
            return JsonResultUtils.fail("ip must be a valid IPv4 address");
        }
        if (!isIpv4LikeOrBlank(gateway)) {
            return JsonResultUtils.fail("gateway must be a valid IPv4 address");
        }
        if (!isDnsListValid(dns)) {
            return JsonResultUtils.fail("dns must be a comma separated IPv4 list");
        }

        List<JSONObject> current = loadNetworkConfigs();
        List<JSONObject> updated = new ArrayList<>();
        for (JSONObject row : current) {
            if (!finalInterfaceName.equals(row.getString("interface_name"))) {
                updated.add(row);
            }
        }

        JSONObject next = new JSONObject();
        next.put("interface_name", finalInterfaceName);
        next.put("ip", defaultString(ip));
        next.put("gateway", defaultString(gateway));
        next.put("dns", defaultString(dns));
        updated.add(next);

        upsertConfig(TAG_NETWORK_CONFIGS, "Network Configurations", JSON.toJSONString(updated));
        operationLogService.record("network:save", "interface=" + finalInterfaceName, true, "network config saved", "");
        return JsonResultUtils.success(updated);
    }

    @PostMapping("/network/delete")
    @ResponseBody
    public JsonResult deleteNetworkConfig(String interfaceName, String interface_name) {
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            return JsonResultUtils.fail("permission denied");
        }
        String finalInterfaceName = StrUtil.isBlank(interfaceName) ? interface_name : interfaceName;
        if (StrUtil.isBlank(finalInterfaceName)) {
            return JsonResultUtils.fail("interface_name is required");
        }
        List<JSONObject> current = loadNetworkConfigs();
        List<JSONObject> updated = new ArrayList<>();
        for (JSONObject row : current) {
            if (!finalInterfaceName.equals(row.getString("interface_name"))) {
                updated.add(row);
            }
        }
        upsertConfig(TAG_NETWORK_CONFIGS, "Network Configurations", JSON.toJSONString(updated));
        operationLogService.record("network:delete", "interface=" + finalInterfaceName, true, "network config deleted", "");
        return JsonResultUtils.success(updated);
    }

    private Long currentAccountId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    private List<JSONObject> loadNetworkConfigs() {
        String raw = configService.getByValTag(TAG_NETWORK_CONFIGS);
        if (StrUtil.isBlank(raw)) {
            return new ArrayList<>();
        }
        try {
            JSONArray array = JSON.parseArray(raw);
            List<JSONObject> results = new ArrayList<>();
            if (array == null) {
                return results;
            }
            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (obj != null) {
                    results.add(obj);
                }
            }
            return results;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void upsertConfig(String tag, String name, String value) {
        Config config = new Config();
        config.setTag(tag);
        config.setName(name);
        config.setVal(defaultString(value));
        LambdaQueryWrapper<Config> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Config::getTag, tag);
        configService.saveOrUpdate(config, queryWrapper);
        configService.evictByTag(tag);
    }

    private int parseInt(String val, int defaultVal) {
        if (StrUtil.isBlank(val)) {
            return defaultVal;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private String defaultString(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isIpv4LikeOrBlank(String value) {
        if (StrUtil.isBlank(value)) {
            return true;
        }
        String[] segments = value.trim().split("\\.");
        if (segments.length != 4) {
            return false;
        }
        for (String segment : segments) {
            if (StrUtil.isBlank(segment)) {
                return false;
            }
            try {
                int part = Integer.parseInt(segment);
                if (part < 0 || part > 255) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private boolean isDnsListValid(String dns) {
        if (StrUtil.isBlank(dns)) {
            return true;
        }
        String[] servers = dns.split(",");
        for (String server : servers) {
            if (!isIpv4LikeOrBlank(server == null ? "" : server.trim())) {
                return false;
            }
        }
        return true;
    }

    private boolean isExpired(String expireAt) {
        if (StrUtil.isBlank(expireAt)) {
            return false;
        }
        try {
            LocalDate date = LocalDate.parse(expireAt.trim());
            return date.atTime(23, 59, 59).isBefore(LocalDateTime.now());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isDateOrBlank(String value) {
        if (StrUtil.isBlank(value)) {
            return true;
        }
        try {
            LocalDate.parse(value.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
