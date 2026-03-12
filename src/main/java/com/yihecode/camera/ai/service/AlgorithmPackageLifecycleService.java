package com.yihecode.camera.ai.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yihecode.camera.ai.entity.Algorithm;
import com.yihecode.camera.ai.entity.CameraAlgorithm;
import com.yihecode.camera.ai.plugin.PluginManifest;
import com.yihecode.camera.ai.plugin.PluginManifestValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class AlgorithmPackageLifecycleService {

    private static final String DEFAULT_STORAGE_DIR = "runtime/algorithm-packages";

    @Autowired
    private AlgorithmService algorithmService;

    @Autowired
    private CameraAlgorithmService cameraAlgorithmService;

    @Autowired(required = false)
    private PluginRegistrationService pluginRegistrationService;

    @Autowired
    private ConfigService configService;

    public Map<String, Object> importPackage(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("algorithm package file is required");
        }
        String originName = StrUtil.blankToDefault(file.getOriginalFilename(), "package.zip");
        if (!originName.toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("algorithm package must be a .zip file");
        }

        String traceId = UUID.randomUUID().toString();
        Path workspace = Files.createTempDirectory("algo-package-import-");
        try {
            Path zipFile = workspace.resolve("upload.zip");
            file.transferTo(zipFile.toFile());
            Path extractedRoot = workspace.resolve("extracted");
            Files.createDirectories(extractedRoot);
            unzipSafely(zipFile, extractedRoot);

            Path manifestPath = findManifest(extractedRoot);
            if (manifestPath == null) {
                throw new IllegalArgumentException("manifest.json not found in algorithm package");
            }

            JSONObject manifestJson = JSON.parseObject(Files.readString(manifestPath, StandardCharsets.UTF_8));
            PluginManifest manifest = parseManifest(manifestJson);
            List<String> errors = PluginManifestValidator.validate(manifest);
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException("manifest validation failed: " + String.join("; ", errors));
            }

            Path pluginRoot = manifestPath.getParent();
            JSONObject pluginConfig = readPluginConfig(pluginRoot.resolve("config").resolve("plugin.json"));

            Path storageRoot = resolveStorageRoot();
            Files.createDirectories(storageRoot);
            Path pluginTargetDir = storageRoot.resolve(manifest.getPluginId()).normalize();
            deleteRecursively(pluginTargetDir);
            copyDirectory(pluginRoot, pluginTargetDir);

            Algorithm algorithm = upsertAlgorithm(manifest, pluginConfig, originName, pluginTargetDir);
            Map<String, Object> registration = registerPlugin(traceId, manifest);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("trace_id", traceId);
            data.put("plugin_id", manifest.getPluginId());
            data.put("version", manifest.getVersion());
            data.put("runtime", manifest.getRuntime());
            data.put("algorithm_id", algorithm.getId());
            data.put("algorithm_name", algorithm.getName());
            data.put("algorithm_name_en", algorithm.getNameEn());
            data.put("storage_dir", pluginTargetDir.toString());
            data.put("registration", registration);
            return data;
        } finally {
            deleteRecursively(workspace);
        }
    }

    public Map<String, Object> forceDelete(Long algorithmId) {
        if (algorithmId == null) {
            throw new IllegalArgumentException("algorithm id is required");
        }
        Algorithm algorithm = algorithmService.getById(algorithmId);
        if (algorithm == null) {
            throw new IllegalArgumentException("algorithm not found");
        }

        List<CameraAlgorithm> bindings = cameraAlgorithmService.listByAlgorithm(algorithmId);
        int unbindCount = bindings == null ? 0 : bindings.size();
        if (unbindCount > 0) {
            cameraAlgorithmService.remove(new LambdaQueryWrapper<CameraAlgorithm>().eq(CameraAlgorithm::getAlgorithmId, algorithmId));
        }

        boolean removed = algorithmService.removeById(algorithmId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("algorithm_id", algorithmId);
        data.put("algorithm_name", algorithm.getName());
        data.put("algorithm_name_en", algorithm.getNameEn());
        data.put("unbind_count", unbindCount);
        data.put("removed", removed);
        return data;
    }

    public Map<String, Object> updateMetadata(Long algorithmId,
                                              String name,
                                              String description,
                                              String labelAliasesZh) {
        if (algorithmId == null) {
            throw new IllegalArgumentException("algorithm id is required");
        }
        Algorithm algorithm = algorithmService.getById(algorithmId);
        if (algorithm == null) {
            throw new IllegalArgumentException("algorithm not found");
        }

        JSONObject params = parseParams(algorithm.getParams());

        String normalizedName = trimToNull(name);
        if (normalizedName != null) {
            algorithm.setName(normalizedName);
        }

        if (description != null) {
            String normalizedDescription = trimToNull(description);
            if (normalizedDescription == null) {
                params.remove("description");
            } else {
                params.put("description", normalizedDescription);
            }
        }

        if (labelAliasesZh != null) {
            String normalizedAliases = trimToNull(labelAliasesZh);
            if (normalizedAliases == null) {
                params.remove("label_aliases_zh");
            } else {
                JSONObject labelMap;
                try {
                    labelMap = JSON.parseObject(normalizedAliases);
                } catch (Exception ex) {
                    throw new IllegalArgumentException("label aliases must be a valid json object");
                }
                if (labelMap == null) {
                    throw new IllegalArgumentException("label aliases must be a valid json object");
                }
                params.put("label_aliases_zh", labelMap);
            }
        }

        algorithm.setParams(JSON.toJSONString(params));
        algorithm.setUpdatedAt(new Date());
        algorithmService.saveOrUpdate(algorithm);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("algorithm_id", algorithm.getId());
        data.put("algorithm_name", algorithm.getName());
        data.put("description", params.getString("description"));
        data.put("label_aliases_zh", params.get("label_aliases_zh"));
        return data;
    }

    private Algorithm upsertAlgorithm(PluginManifest manifest,
                                      JSONObject pluginConfig,
                                      String packageName,
                                      Path pluginTargetDir) {
        List<Algorithm> existing = algorithmService.listNameEn(manifest.getPluginId());
        Algorithm algorithm = (existing == null || existing.isEmpty()) ? new Algorithm() : existing.get(0);
        if (algorithm.getCreatedAt() == null) {
            algorithm.setCreatedAt(new Date());
        }
        if (algorithm.getFrequency() == null) {
            algorithm.setFrequency(1000);
        }
        if (algorithm.getIntervalTime() == null) {
            algorithm.setIntervalTime(100);
        }
        if (algorithm.getStaticsFlag() == null) {
            algorithm.setStaticsFlag(0);
        }

        String displayName = pluginConfig == null ? null : trimToNull(pluginConfig.getString("display_name"));
        algorithm.setName(StrUtil.blankToDefault(displayName, manifest.getPluginId()));
        algorithm.setNameEn(manifest.getPluginId());
        algorithm.setModelPath(pluginTargetDir.toString());
        algorithm.setFileName(packageName);
        algorithm.setParams(buildAlgorithmParams(manifest, pluginConfig, packageName, pluginTargetDir));
        algorithm.setUpdatedAt(new Date());
        algorithmService.saveOrUpdate(algorithm);
        return algorithm;
    }

    private String buildAlgorithmParams(PluginManifest manifest,
                                        JSONObject pluginConfig,
                                        String packageName,
                                        Path pluginTargetDir) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("plugin_id", manifest.getPluginId());
        params.put("plugin_version", manifest.getVersion());
        params.put("plugin_runtime", manifest.getRuntime());
        params.put("plugin_capabilities", manifest.getCapabilities() == null ? new ArrayList<>() : manifest.getCapabilities());
        params.put("package_name", packageName);
        params.put("package_dir", pluginTargetDir.toString());
        params.put("imported_at", System.currentTimeMillis());

        if (pluginConfig != null) {
            copyIfPresent(pluginConfig, params, "obj_threshold");
            copyIfPresent(pluginConfig, params, "nms_threshold");
            copyIfPresent(pluginConfig, params, "inference_time_ms");
            copyIfPresent(pluginConfig, params, "alert_labels");
            copyIfPresent(pluginConfig, params, "label_aliases_zh");
            copyIfPresent(pluginConfig, params, "class_names");
            copyIfPresent(pluginConfig, params, "enabled_labels");
        }
        return JSON.toJSONString(params);
    }

    private void copyIfPresent(JSONObject source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null) {
            target.put(key, value);
        }
    }

    private Map<String, Object> registerPlugin(String traceId, PluginManifest manifest) {
        Map<String, Object> data = new HashMap<>();
        if (pluginRegistrationService == null) {
            data.put("accepted", false);
            data.put("registration_status", "plugin_registration_service_unavailable");
            return data;
        }

        PluginManifest registerManifest = new PluginManifest();
        registerManifest.setPluginId(manifest.getPluginId());
        registerManifest.setVersion(manifest.getVersion());
        registerManifest.setRuntime(manifest.getRuntime());
        registerManifest.setCapabilities(manifest.getCapabilities());

        String inferUrl = trimToNull(manifest.getInferUrl());
        String base = trimToNull(configService.getByValTag("infer_service_url"));
        if (inferUrl == null && base != null) {
            inferUrl = base.endsWith("/") ? (base + "v1/infer") : (base + "/v1/infer");
        }
        registerManifest.setInferUrl(inferUrl);

        String healthUrl = base == null ? null : (base.endsWith("/") ? (base + "health") : (base + "/health"));
        return pluginRegistrationService.register(traceId, registerManifest, healthUrl);
    }

    private Path resolveStorageRoot() {
        String configured = trimToNull(configService.getByValTag("algorithm_package_dir"));
        Path root = configured == null ? Path.of(DEFAULT_STORAGE_DIR) : Path.of(configured);
        if (!root.isAbsolute()) {
            root = root.toAbsolutePath();
        }
        return root.normalize();
    }

    private JSONObject readPluginConfig(Path configPath) {
        if (configPath == null || !Files.exists(configPath)) {
            return null;
        }
        try {
            return JSON.parseObject(Files.readString(configPath, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return null;
        }
    }

    private PluginManifest parseManifest(JSONObject manifestJson) {
        PluginManifest manifest = new PluginManifest();
        manifest.setPluginId(trimToNull(manifestJson.getString("plugin_id")));
        manifest.setVersion(trimToNull(manifestJson.getString("version")));
        manifest.setRuntime(trimToNull(manifestJson.getString("runtime")));
        manifest.setInferUrl(trimToNull(manifestJson.getString("infer_url")));

        List<String> capabilities = new ArrayList<>();
        JSONArray array = manifestJson.getJSONArray("capabilities");
        if (array != null) {
            for (int i = 0; i < array.size(); i++) {
                String item = trimToNull(array.getString(i));
                if (item != null) {
                    capabilities.add(item);
                }
            }
        }
        manifest.setCapabilities(capabilities);
        return manifest;
    }

    private JSONObject parseParams(String paramsJson) {
        if (StrUtil.isBlank(paramsJson)) {
            return new JSONObject();
        }
        try {
            JSONObject params = JSON.parseObject(paramsJson);
            return params == null ? new JSONObject() : params;
        } catch (Exception ex) {
            return new JSONObject();
        }
    }

    private Path findManifest(Path extractedRoot) throws IOException {
        try (var stream = Files.walk(extractedRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> "manifest.json".equalsIgnoreCase(path.getFileName().toString()))
                    .findFirst()
                    .orElse(null);
        }
    }

    private void unzipSafely(Path zipFile, Path targetDir) throws IOException {
        try (InputStream inputStream = Files.newInputStream(zipFile);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path output = targetDir.resolve(entry.getName()).normalize();
                if (!output.startsWith(targetDir)) {
                    throw new IOException("invalid zip entry path: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                    continue;
                }
                Path parent = output.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(zipInputStream, output, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (var stream = Files.walk(source)) {
            for (Path src : (Iterable<Path>) stream::iterator) {
                Path relative = source.relativize(src);
                Path dst = target.resolve(relative).normalize();
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dst);
                } else {
                    Path parent = dst.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private String trimToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }
}
