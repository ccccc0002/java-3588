package com.yihecode.camera.ai.web;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yihecode.camera.ai.entity.Config;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.OperationLogService;
import com.yihecode.camera.ai.service.RoleAccessService;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;

@SaCheckLogin
@Controller
@RequestMapping({"/push"})
public class PushController {

    private static final String TAG_VOICE_PUSH_ENABLED = "voice_push_enabled";
    private static final String TAG_VOICE_PUSH_PROVIDER = "voice_push_provider";
    private static final String TAG_VOICE_PUSH_URL = "voice_push_url";
    private static final String TAG_VOICE_PUSH_BEARER = "voice_push_bearer";
    private static final String TAG_VOICE_PUSH_NUMBERS = "voice_push_numbers";
    private static final String TAG_SMS_ENABLE = "smsEnable";
    private static final String TAG_SMS_PROVIDER = "sms_provider";
    private static final String TAG_SMS_API_URL = "sms_api_url";
    private static final String TAG_SMS_API_KEY = "sms_api_key";
    private static final String TAG_SMS_TPL_ID = "sms_tpl_id";

    @Autowired
    private ConfigService configService;

    @Autowired
    private RoleAccessService roleAccessService;

    @Autowired
    private OperationLogService operationLogService;

    @GetMapping("/voice")
    public String voicePushPage() {
        return "push/voice";
    }

    @PostMapping("/voice/detail")
    @ResponseBody
    public JsonResult voicePushDetail() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", "1".equals(defaultString(configService.getByValTag(TAG_VOICE_PUSH_ENABLED))));
        data.put("provider", defaultString(configService.getByValTag(TAG_VOICE_PUSH_PROVIDER)));
        data.put("url", defaultString(configService.getByValTag(TAG_VOICE_PUSH_URL)));
        data.put("bearer", defaultString(configService.getByValTag(TAG_VOICE_PUSH_BEARER)));
        data.put("numbers", defaultString(configService.getByValTag(TAG_VOICE_PUSH_NUMBERS)));
        return JsonResultUtils.success(data);
    }

    @PostMapping("/voice/save")
    @ResponseBody
    public JsonResult saveVoicePushConfig(Boolean enabled,
                                          String provider,
                                          String url,
                                          String bearer,
                                          String numbers) {
        if (!roleAccessService.canManagePushTargets(currentAccountId())) {
            operationLogService.record("push:voice:save", "voice", false, "permission denied", "");
            return JsonResultUtils.fail("permission denied");
        }
        if (StrUtil.isNotBlank(url) && !(url.startsWith("http://") || url.startsWith("https://"))) {
            return JsonResultUtils.fail("url must start with http:// or https://");
        }

        upsertConfig(TAG_VOICE_PUSH_ENABLED, "Voice Push Enabled", Boolean.TRUE.equals(enabled) ? "1" : "0");
        upsertConfig(TAG_VOICE_PUSH_PROVIDER, "Voice Push Provider", defaultString(provider));
        upsertConfig(TAG_VOICE_PUSH_URL, "Voice Push URL", defaultString(url));
        upsertConfig(TAG_VOICE_PUSH_BEARER, "Voice Push Bearer", defaultString(bearer));
        upsertConfig(TAG_VOICE_PUSH_NUMBERS, "Voice Push Numbers", defaultString(numbers));

        operationLogService.record("push:voice:save", "voice", true, "voice push config saved", "enabled=" + Boolean.TRUE.equals(enabled));
        return JsonResultUtils.success();
    }

    @GetMapping("/sms-config")
    public String smsPushPage() {
        return "push/sms_config";
    }

    @PostMapping("/sms-config/detail")
    @ResponseBody
    public JsonResult smsPushDetail() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", "true".equalsIgnoreCase(defaultString(configService.getByValTag(TAG_SMS_ENABLE)))
                || "1".equals(defaultString(configService.getByValTag(TAG_SMS_ENABLE))));
        data.put("provider", defaultString(configService.getByValTag(TAG_SMS_PROVIDER)));
        data.put("api_url", defaultString(configService.getByValTag(TAG_SMS_API_URL)));
        data.put("api_key", defaultString(configService.getByValTag(TAG_SMS_API_KEY)));
        data.put("tpl_id", defaultString(configService.getByValTag(TAG_SMS_TPL_ID)));
        return JsonResultUtils.success(data);
    }

    @PostMapping("/sms-config/save")
    @ResponseBody
    public JsonResult saveSmsPushConfig(Boolean enabled,
                                        String provider,
                                        String apiUrl,
                                        String api_url,
                                        String apiKey,
                                        String api_key,
                                        String tplId,
                                        String tpl_id) {
        if (!roleAccessService.canManagePushTargets(currentAccountId())) {
            operationLogService.record("push:sms:save", "sms", false, "permission denied", "");
            return JsonResultUtils.fail("permission denied");
        }
        String finalApiUrl = StrUtil.isBlank(apiUrl) ? api_url : apiUrl;
        String finalApiKey = StrUtil.isBlank(apiKey) ? api_key : apiKey;
        String finalTplId = StrUtil.isBlank(tplId) ? tpl_id : tplId;
        if (StrUtil.isNotBlank(finalApiUrl) && !(finalApiUrl.startsWith("http://") || finalApiUrl.startsWith("https://"))) {
            return JsonResultUtils.fail("api_url must start with http:// or https://");
        }
        if (Boolean.TRUE.equals(enabled)) {
            if (StrUtil.isBlank(finalApiKey)) {
                return JsonResultUtils.fail("api_key is required when sms enabled");
            }
            if (StrUtil.isBlank(finalTplId)) {
                return JsonResultUtils.fail("tpl_id is required when sms enabled");
            }
        }

        upsertConfig(TAG_SMS_ENABLE, "SMS Enable", Boolean.TRUE.equals(enabled) ? "true" : "false");
        upsertConfig(TAG_SMS_PROVIDER, "SMS Provider", defaultString(provider));
        upsertConfig(TAG_SMS_API_URL, "SMS API URL", defaultString(finalApiUrl));
        upsertConfig(TAG_SMS_API_KEY, "SMS API Key", defaultString(finalApiKey));
        upsertConfig(TAG_SMS_TPL_ID, "SMS Template ID", defaultString(finalTplId));
        operationLogService.record("push:sms:save", "sms", true, "sms config saved", "enabled=" + Boolean.TRUE.equals(enabled));
        return JsonResultUtils.success();
    }

    private void upsertConfig(String tag, String name, String value) {
        Config config = configService.getOne(new LambdaQueryWrapper<Config>().eq(Config::getTag, tag), false);
        if (config == null) {
            config = new Config();
            config.setTag(tag);
            config.setName(name);
        }
        config.setVal(defaultString(value));
        configService.saveOrUpdate(config);
        configService.evictByTag(tag);
    }

    private String defaultString(String value) {
        return value == null ? "" : value.trim();
    }

    private Long currentAccountId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }
}
