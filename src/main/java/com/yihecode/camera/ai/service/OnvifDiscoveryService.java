package com.yihecode.camera.ai.service;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class OnvifDiscoveryService {

    private static final int DEFAULT_TIMEOUT_MS = 600;
    private static final int DEFAULT_MAX_HOSTS = 254;
    private static final int MAX_HOSTS = 254;
    private static final int DEFAULT_THREADS = 24;

    public List<Map<String, Object>> scan(String cidr, Integer maxHosts, Integer timeoutMs, String username, String password) {
        String prefix = normalizePrefix(cidr);
        if (StrUtil.isBlank(prefix)) {
            return new ArrayList<>();
        }
        int hostCount = normalizeMaxHosts(maxHosts);
        int timeout = normalizeTimeout(timeoutMs);
        int workers = Math.max(Math.min(DEFAULT_THREADS, hostCount), 1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<Map<String, Object>>> futures = new ArrayList<>();
            for (int host = 1; host <= hostCount; host++) {
                final String ip = prefix + "." + host;
                futures.add(executor.submit(() -> probeDevice(ip, timeout, username, password)));
            }
            List<Map<String, Object>> results = new ArrayList<>();
            for (Future<Map<String, Object>> future : futures) {
                try {
                    Map<String, Object> item = future.get();
                    if (item != null && !item.isEmpty()) {
                        results.add(item);
                    }
                } catch (Exception ignored) {
                    // keep scanning other hosts.
                }
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    protected Map<String, Object> probeDevice(String ip, int timeoutMs, String username, String password) {
        Integer status = probeOnvifStatusCode(ip, timeoutMs);
        if (!isOnvifStatus(status)) {
            return null;
        }
        boolean rtspReachable = isPortOpen(ip, 554, timeoutMs);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("ip", ip);
        item.put("source_type", "onvif");
        item.put("device_service_url", "http://" + ip + "/onvif/device_service");
        item.put("rtsp_url", buildRtspUrl(ip, 554, username, password));
        item.put("rtsp_reachable", rtspReachable);
        item.put("credential_applied", StrUtil.isNotBlank(username));
        return item;
    }

    protected Integer probeOnvifStatusCode(String ip, int timeoutMs) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("http://" + ip + "/onvif/device_service");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestMethod("GET");
            int status = conn.getResponseCode();
            closeQuietly(conn.getInputStream());
            return status;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    protected boolean isPortOpen(String ip, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isOnvifStatus(Integer status) {
        if (status == null) {
            return false;
        }
        return status == 200 || status == 401 || status == 405;
    }

    private String buildRtspUrl(String ip, int port, String username, String password) {
        String user = StrUtil.blankToDefault(username, "").trim();
        String pass = StrUtil.blankToDefault(password, "").trim();
        String auth = "";
        if (StrUtil.isNotBlank(user)) {
            auth = user + (StrUtil.isNotBlank(pass) ? ":" + pass : "") + "@";
        }
        return "rtsp://" + auth + ip + ":" + port + "/h264/ch1/main/av_stream";
    }

    private String normalizePrefix(String cidr) {
        String raw = StrUtil.blankToDefault(cidr, "").trim();
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        String address = raw;
        int slash = raw.indexOf('/');
        if (slash > 0) {
            address = raw.substring(0, slash);
        }
        if (address.endsWith(".*")) {
            address = address.substring(0, address.length() - 2);
        }
        String[] parts = address.split("\\.");
        if (parts.length == 3) {
            return validatePrefix(parts[0], parts[1], parts[2]) ? address : null;
        }
        if (parts.length == 4) {
            return validatePrefix(parts[0], parts[1], parts[2]) ? (parts[0] + "." + parts[1] + "." + parts[2]) : null;
        }
        return null;
    }

    private boolean validatePrefix(String a, String b, String c) {
        return isIpv4Octet(a) && isIpv4Octet(b) && isIpv4Octet(c);
    }

    private boolean isIpv4Octet(String value) {
        if (StrUtil.isBlank(value)) {
            return false;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 && parsed <= 255;
        } catch (Exception ignored) {
            return false;
        }
    }

    private int normalizeMaxHosts(Integer maxHosts) {
        int hosts = maxHosts == null ? DEFAULT_MAX_HOSTS : maxHosts;
        if (hosts <= 0) {
            hosts = DEFAULT_MAX_HOSTS;
        }
        return Math.min(hosts, MAX_HOSTS);
    }

    private int normalizeTimeout(Integer timeoutMs) {
        int timeout = timeoutMs == null ? DEFAULT_TIMEOUT_MS : timeoutMs;
        if (timeout <= 0) {
            timeout = DEFAULT_TIMEOUT_MS;
        }
        return Math.min(timeout, 5000);
    }

    private void closeQuietly(InputStream inputStream) {
        if (inputStream == null) {
            return;
        }
        try {
            inputStream.close();
        } catch (Exception ignored) {
            // ignore
        }
    }
}
