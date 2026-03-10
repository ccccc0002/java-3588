package com.yihecode.camera.ai.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

@Service
public class RuntimeAccessTokenService {

    private static final long DEFAULT_TOKEN_TTL_MS = 3600_000L;

    private final ConfigService configService;
    private final LongSupplier nowMsSupplier;
    private final ConcurrentHashMap<String, TokenRecord> issuedTokens = new ConcurrentHashMap<>();

    @Autowired
    public RuntimeAccessTokenService(ConfigService configService) {
        this(configService, System::currentTimeMillis);
    }

    RuntimeAccessTokenService(ConfigService configService, LongSupplier nowMsSupplier) {
        this.configService = configService;
        this.nowMsSupplier = nowMsSupplier == null ? System::currentTimeMillis : nowMsSupplier;
    }

    public String resolveBootstrapToken() {
        String systemProperty = trimToEmpty(System.getProperty("runtime.bootstrap.token"));
        if (StrUtil.isNotBlank(systemProperty)) {
            return systemProperty;
        }
        String environmentValue = trimToEmpty(System.getenv("RUNTIME_BOOTSTRAP_TOKEN"));
        if (StrUtil.isNotBlank(environmentValue)) {
            return environmentValue;
        }
        return trimToEmpty(configService.getByValTag("runtime_bootstrap_token"));
    }

    public boolean isBootstrapTokenValid(String suppliedToken) {
        String expectedToken = resolveBootstrapToken();
        return StrUtil.isNotBlank(expectedToken) && StrUtil.equals(expectedToken, trimToEmpty(suppliedToken));
    }

    public Map<String, Object> issueToken(String userId, String role) {
        cleanupExpiredTokens();
        long issuedAtMs = nowMsSupplier.getAsLong();
        long ttlMs = resolveTokenTtlMs();
        String token = IdUtil.fastSimpleUUID();
        TokenRecord record = new TokenRecord(token, trimToEmpty(userId), trimToEmpty(role), issuedAtMs, issuedAtMs + ttlMs);
        issuedTokens.put(token, record);

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("issued_at_ms", issuedAtMs);
        data.put("expires_at_ms", record.expiresAtMs);
        data.put("user_id", record.userId);
        data.put("role", record.role);
        return data;
    }

    public boolean isAuthorized(String authorizationHeader) {
        cleanupExpiredTokens();
        String token = extractBearerToken(authorizationHeader);
        if (StrUtil.isBlank(token)) {
            return false;
        }
        TokenRecord record = issuedTokens.get(token);
        if (record == null) {
            return false;
        }
        if (record.expiresAtMs <= nowMsSupplier.getAsLong()) {
            issuedTokens.remove(token);
            return false;
        }
        return true;
    }

    private String extractBearerToken(String authorizationHeader) {
        String value = trimToEmpty(authorizationHeader);
        if (!StrUtil.startWithIgnoreCase(value, "Bearer ")) {
            return "";
        }
        return trimToEmpty(value.substring(7));
    }

    private long resolveTokenTtlMs() {
        String configured = trimToEmpty(configService.getByValTag("runtime_token_ttl_sec"));
        if (StrUtil.isBlank(configured)) {
            return DEFAULT_TOKEN_TTL_MS;
        }
        try {
            long seconds = Long.parseLong(configured);
            return seconds > 0 ? seconds * 1000L : DEFAULT_TOKEN_TTL_MS;
        } catch (NumberFormatException ex) {
            return DEFAULT_TOKEN_TTL_MS;
        }
    }

    private void cleanupExpiredTokens() {
        long nowMs = nowMsSupplier.getAsLong();
        Iterator<Map.Entry<String, TokenRecord>> iterator = issuedTokens.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, TokenRecord> entry = iterator.next();
            if (entry.getValue().expiresAtMs <= nowMs) {
                iterator.remove();
            }
        }
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class TokenRecord {
        private final String token;
        private final String userId;
        private final String role;
        private final long issuedAtMs;
        private final long expiresAtMs;

        private TokenRecord(String token, String userId, String role, long issuedAtMs, long expiresAtMs) {
            this.token = token;
            this.userId = userId;
            this.role = role;
            this.issuedAtMs = issuedAtMs;
            this.expiresAtMs = expiresAtMs;
        }
    }
}
