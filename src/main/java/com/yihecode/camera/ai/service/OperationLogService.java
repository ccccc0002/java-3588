package com.yihecode.camera.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yihecode.camera.ai.entity.Account;
import com.yihecode.camera.ai.entity.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OperationLogService {

    private static final String TAG_OPERATION_LOGS = "operation_logs";
    private static final int MAX_LOGS = 2000;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private ConfigService configService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private RoleAccessService roleAccessService;

    public void record(String action, String target, boolean success, String message, String detail) {
        try {
            JSONObject log = new JSONObject();
            Long accountId = resolveCurrentAccountId();
            Account account = accountId == null ? null : accountService.getById(accountId);
            String role = roleAccessService.getRoleByAccountId(accountId);

            log.put("id", UUID.randomUUID().toString().replace("-", ""));
            log.put("timestamp", System.currentTimeMillis());
            log.put("time_text", LocalDateTime.now().format(DATE_TIME_FORMATTER));
            log.put("operator_id", accountId);
            log.put("operator_name", account == null ? "unknown" : account.getName());
            log.put("account", account == null ? "" : account.getAccount());
            log.put("role", role);
            log.put("action", defaultString(action));
            log.put("target", defaultString(target));
            log.put("success", success ? 1 : 0);
            log.put("message", defaultString(message));
            log.put("detail", defaultString(detail));

            List<JSONObject> current = loadLogs();
            current.add(log);
            if (current.size() > MAX_LOGS) {
                current = new ArrayList<>(current.subList(current.size() - MAX_LOGS, current.size()));
            }
            persistLogs(current);
        } catch (Exception ignored) {
            // Logging should never break business flow.
        }
    }

    public List<JSONObject> list(String operatorName, String role, String action, Integer success, String startText, String endText) {
        long startMs = parseDateTime(startText, Long.MIN_VALUE);
        long endMs = parseDateTime(endText, Long.MAX_VALUE);
        List<JSONObject> all = loadLogs();
        List<JSONObject> filtered = new ArrayList<>();
        for (JSONObject row : all) {
            if (row == null) {
                continue;
            }
            long ts = row.getLongValue("timestamp");
            if (ts < startMs || ts > endMs) {
                continue;
            }
            if (StrUtil.isNotBlank(operatorName) && !StrUtil.containsIgnoreCase(row.getString("operator_name"), operatorName.trim())) {
                continue;
            }
            if (StrUtil.isNotBlank(role) && !role.trim().equalsIgnoreCase(row.getString("role"))) {
                continue;
            }
            if (StrUtil.isNotBlank(action) && !StrUtil.containsIgnoreCase(row.getString("action"), action.trim())) {
                continue;
            }
            if (success != null && success != row.getIntValue("success")) {
                continue;
            }
            filtered.add(row);
        }
        return filtered;
    }

    private List<JSONObject> loadLogs() {
        String raw = configService.getByValTag(TAG_OPERATION_LOGS);
        if (StrUtil.isBlank(raw)) {
            return new ArrayList<>();
        }
        try {
            JSONArray array = JSON.parseArray(raw);
            List<JSONObject> result = new ArrayList<>();
            if (array == null) {
                return result;
            }
            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (obj != null) {
                    result.add(obj);
                }
            }
            return result;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void persistLogs(List<JSONObject> logs) {
        Config config = new Config();
        config.setTag(TAG_OPERATION_LOGS);
        config.setName("Operation Logs");
        config.setVal(JSON.toJSONString(logs));
        LambdaQueryWrapper<Config> query = new LambdaQueryWrapper<>();
        query.eq(Config::getTag, TAG_OPERATION_LOGS);
        configService.saveOrUpdate(config, query);
        configService.evictByTag(TAG_OPERATION_LOGS);
    }

    private Long resolveCurrentAccountId() {
        try {
            if (!StpUtil.isLogin()) {
                return null;
            }
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    private long parseDateTime(String text, long defaultValue) {
        if (StrUtil.isBlank(text)) {
            return defaultValue;
        }
        try {
            LocalDateTime dateTime = LocalDateTime.parse(text.trim(), DATE_TIME_FORMATTER);
            return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String defaultString(String value) {
        return value == null ? "" : value.trim();
    }
}

