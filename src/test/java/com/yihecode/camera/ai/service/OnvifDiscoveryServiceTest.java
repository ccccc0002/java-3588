package com.yihecode.camera.ai.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnvifDiscoveryServiceTest {

    @Test
    void scanShouldReturnEmptyWhenCidrInvalid() {
        StubOnvifDiscoveryService service = new StubOnvifDiscoveryService();

        List<Map<String, Object>> items = service.scan("invalid-cidr", 16, 600, "admin", "123");

        assertTrue(items.isEmpty());
        assertTrue(service.probedIps.isEmpty());
    }

    @Test
    void scanShouldProbeConfiguredHostRangeAndCollectItems() {
        StubOnvifDiscoveryService service = new StubOnvifDiscoveryService();

        List<Map<String, Object>> items = service.scan("192.168.1.0/24", 6, 600, "admin", "123456");

        assertEquals(6, service.probedIps.size());
        assertEquals(2, items.size());
        assertTrue(service.probedIps.contains("192.168.1.2"));
        assertTrue(service.probedIps.contains("192.168.1.5"));
    }

    private static final class StubOnvifDiscoveryService extends OnvifDiscoveryService {
        private final List<String> probedIps = Collections.synchronizedList(new ArrayList<>());

        @Override
        protected Map<String, Object> probeDevice(String ip, int timeoutMs, String username, String password) {
            probedIps.add(ip);
            if ("192.168.1.2".equals(ip) || "192.168.1.5".equals(ip)) {
                return Map.of("ip", ip, "source_type", "onvif");
            }
            return null;
        }
    }
}
