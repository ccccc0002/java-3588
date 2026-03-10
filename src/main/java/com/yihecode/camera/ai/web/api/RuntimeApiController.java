package com.yihecode.camera.ai.web.api;

import cn.hutool.core.util.StrUtil;
import com.yihecode.camera.ai.service.RuntimeAccessTokenService;
import com.yihecode.camera.ai.service.RuntimeApiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class RuntimeApiController {

    private final RuntimeAccessTokenService runtimeAccessTokenService;
    private final RuntimeApiService runtimeApiService;

    public RuntimeApiController(RuntimeAccessTokenService runtimeAccessTokenService,
                                RuntimeApiService runtimeApiService) {
        this.runtimeAccessTokenService = runtimeAccessTokenService;
        this.runtimeApiService = runtimeApiService;
    }

    @PostMapping("/auth/token")
    public ResponseEntity<Map<String, Object>> issueToken(
            @RequestHeader(value = "X-Bootstrap-Token", required = false) String bootstrapToken,
            @RequestBody(required = false) Map<String, Object> payload) {
        if (!runtimeAccessTokenService.isBootstrapTokenValid(bootstrapToken)) {
            return error(HttpStatus.UNAUTHORIZED, "invalid_token", "invalid bootstrap token");
        }
        Map<String, Object> request = payload == null ? Collections.emptyMap() : payload;
        Map<String, Object> data = runtimeAccessTokenService.issueToken(
                asString(request.get("user_id"), "java-rk3588-bridge"),
                asString(request.get("role"), "admin")
        );
        return success(data);
    }

    @GetMapping("/runtime/health")
    public ResponseEntity<Map<String, Object>> runtimeHealth() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ok");
        data.put("auth_required", true);
        return success(data);
    }

    @GetMapping("/runtime/snapshot")
    public ResponseEntity<Map<String, Object>> runtimeSnapshot(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!runtimeAccessTokenService.isAuthorized(authorization)) {
            return error(HttpStatus.UNAUTHORIZED, "invalid_token", "token invalid or expired");
        }
        return success(runtimeApiService.buildRuntimeSnapshot());
    }

    @PostMapping("/inference/plan")
    public ResponseEntity<Map<String, Object>> inferencePlan(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) Map<String, Object> payload) {
        if (!runtimeAccessTokenService.isAuthorized(authorization)) {
            return error(HttpStatus.UNAUTHORIZED, "invalid_token", "token invalid or expired");
        }
        Map<String, Object> request = payload == null ? Collections.emptyMap() : payload;
        return success(runtimeApiService.buildInferencePlan(asDouble(request.get("budget"), 10.0D)));
    }

    private ResponseEntity<Map<String, Object>> success(Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("data", data == null ? Collections.emptyMap() : data);
        payload.put("error", null);
        payload.put("meta", Collections.emptyMap());
        return ResponseEntity.ok(payload);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", false);
        payload.put("data", null);
        payload.put("error", error);
        payload.put("meta", Collections.emptyMap());
        return ResponseEntity.status(status).body(payload);
    }

    private String asString(Object value, String defaultValue) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return StrUtil.isBlank(text) ? defaultValue : text;
    }

    private Double asDouble(Object value, Double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
