package com.yihecode.camera.ai.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yihecode.camera.ai.entity.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class RoleAccessService {

    public static final String ROLE_SUPER_ADMIN = "super_admin";
    public static final String ROLE_OPS = "ops";
    public static final String ROLE_READ_ONLY = "read_only";

    private static final String TAG_RBAC_ACCOUNT_ROLES = "rbac_account_roles";

    @Autowired
    private ConfigService configService;

    public String getRoleByAccountId(Long accountId) {
        if (accountId == null) {
            return ROLE_READ_ONLY;
        }
        Map<String, String> roles = loadRoleMap();
        String role = roles.get(String.valueOf(accountId));
        if (StrUtil.isBlank(role)) {
            return accountId == 1L ? ROLE_SUPER_ADMIN : ROLE_OPS;
        }
        return normalizeRole(role);
    }

    public void saveRole(Long accountId, String role) {
        if (accountId == null) {
            return;
        }
        Map<String, String> roles = loadRoleMap();
        roles.put(String.valueOf(accountId), normalizeRole(role));
        persistRoleMap(roles);
    }

    public void removeRole(Long accountId) {
        if (accountId == null) {
            return;
        }
        Map<String, String> roles = loadRoleMap();
        roles.remove(String.valueOf(accountId));
        persistRoleMap(roles);
    }

    public boolean canManageAccounts(Long accountId) {
        return ROLE_SUPER_ADMIN.equals(getRoleByAccountId(accountId));
    }

    public boolean canWriteSystem(Long accountId) {
        String role = getRoleByAccountId(accountId);
        return ROLE_SUPER_ADMIN.equals(role) || ROLE_OPS.equals(role);
    }

    public boolean canSyncWarehouse(Long accountId) {
        return canWriteSystem(accountId);
    }

    public boolean canManageStream(Long accountId) {
        return canWriteSystem(accountId);
    }

    public boolean canManagePushTargets(Long accountId) {
        return canWriteSystem(accountId);
    }

    public Set<String> listActionPermissions(String role) {
        String normalized = normalizeRole(role);
        if (ROLE_SUPER_ADMIN.equals(normalized)) {
            Set<String> perms = new HashSet<>();
            perms.add("*");
            return perms;
        }
        if (ROLE_OPS.equals(normalized)) {
            Set<String> perms = new HashSet<>();
            perms.add("system:write");
            perms.add("warehouse:sync");
            perms.add("operationlog:view");
            perms.add("stream:manage");
            return perms;
        }
        return new HashSet<>(Collections.singletonList("operationlog:view"));
    }

    public Map<String, Object> buildPermissionMatrix(Long accountId) {
        String role = getRoleByAccountId(accountId);
        Map<String, Object> matrix = new HashMap<>();
        matrix.put("role", role);
        matrix.put("can_manage_accounts", ROLE_SUPER_ADMIN.equals(role));
        matrix.put("can_write_system", ROLE_SUPER_ADMIN.equals(role) || ROLE_OPS.equals(role));
        matrix.put("can_sync_warehouse", ROLE_SUPER_ADMIN.equals(role) || ROLE_OPS.equals(role));
        matrix.put("can_manage_stream", ROLE_SUPER_ADMIN.equals(role) || ROLE_OPS.equals(role));
        matrix.put("can_manage_push_targets", ROLE_SUPER_ADMIN.equals(role) || ROLE_OPS.equals(role));
        matrix.put("can_view_operation_log", true);
        matrix.put("permissions", listActionPermissions(role));
        return matrix;
    }

    private Map<String, String> loadRoleMap() {
        String raw = configService.getByValTag(TAG_RBAC_ACCOUNT_ROLES);
        if (StrUtil.isBlank(raw)) {
            return new HashMap<>();
        }
        try {
            JSONObject json = JSON.parseObject(raw);
            Map<String, String> result = new HashMap<>();
            if (json == null) {
                return result;
            }
            for (String key : json.keySet()) {
                result.put(key, normalizeRole(json.getString(key)));
            }
            return result;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private void persistRoleMap(Map<String, String> roles) {
        Config config = new Config();
        config.setTag(TAG_RBAC_ACCOUNT_ROLES);
        config.setName("RBAC Account Roles");
        config.setVal(JSON.toJSONString(roles));
        LambdaQueryWrapper<Config> query = new LambdaQueryWrapper<>();
        query.eq(Config::getTag, TAG_RBAC_ACCOUNT_ROLES);
        configService.saveOrUpdate(config, query);
        configService.evictByTag(TAG_RBAC_ACCOUNT_ROLES);
    }

    private String normalizeRole(String role) {
        if (StrUtil.isBlank(role)) {
            return ROLE_OPS;
        }
        String val = role.trim().toLowerCase();
        if (ROLE_SUPER_ADMIN.equals(val) || ROLE_OPS.equals(val) || ROLE_READ_ONLY.equals(val)) {
            return val;
        }
        return ROLE_OPS;
    }
}
