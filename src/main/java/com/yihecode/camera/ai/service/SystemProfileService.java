package com.yihecode.camera.ai.service;

import org.springframework.stereotype.Service;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemProfileService {

    public String getDeviceId() {
        List<String> macList = collectMacAddresses();
        String seed = String.join("|", macList);
        if (seed.isEmpty()) {
            seed = System.getProperty("os.name", "") + "|" + System.getProperty("os.arch", "");
        }
        return sha256(seed).substring(0, 32);
    }

    public List<Map<String, Object>> listNetworkInterfaces() {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return results;
            }
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface == null || !networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                Map<String, Object> row = new HashMap<>();
                row.put("name", networkInterface.getName());
                row.put("display_name", networkInterface.getDisplayName());
                row.put("mac", formatMac(networkInterface.getHardwareAddress()));
                row.put("ipv4", firstIpv4(networkInterface));
                results.add(row);
            }
        } catch (Exception ignored) {
            // Keep response resilient for low-level network API failures.
        }
        results.sort((a, b) -> String.valueOf(a.get("name")).compareTo(String.valueOf(b.get("name"))));
        return results;
    }

    private List<String> collectMacAddresses() {
        List<String> macs = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return macs;
            }
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface == null || !networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                String mac = formatMac(networkInterface.getHardwareAddress());
                if (!mac.isEmpty()) {
                    macs.add(mac);
                }
            }
        } catch (Exception ignored) {
            // Keep fallback deterministic.
        }
        Collections.sort(macs);
        return macs;
    }

    private String firstIpv4(NetworkInterface networkInterface) {
        try {
            Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
                InetAddress address = inetAddresses.nextElement();
                if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                    return address.getHostAddress();
                }
            }
        } catch (Exception ignored) {
            // Ignore and return empty string.
        }
        return "";
    }

    private String formatMac(byte[] hardwareAddress) {
        if (hardwareAddress == null || hardwareAddress.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hardwareAddress.length; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(String.format("%02X", hardwareAddress[i]));
        }
        return sb.toString();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}

