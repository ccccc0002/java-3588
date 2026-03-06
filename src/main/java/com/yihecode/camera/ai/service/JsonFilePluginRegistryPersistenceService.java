package com.yihecode.camera.ai.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class JsonFilePluginRegistryPersistenceService implements PluginRegistryPersistenceService {

    private static final String DEFAULT_PLUGIN_REGISTRY_FILE = "ops/java-3588-recorder/plugin-registry.json";

    @Autowired(required = false)
    private ConfigService configService;

    @Override
    public List<PluginRegistryRecord> loadAll() {
        Path file = resolveFilePath();
        if (file == null || !Files.exists(file)) {
            return Collections.emptyList();
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (StrUtil.isBlank(text)) {
                return Collections.emptyList();
            }
            JSONArray array = JSON.parseArray(text);
            if (array == null) {
                return Collections.emptyList();
            }
            List<PluginRegistryRecord> records = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                PluginRegistryRecord record = array.getObject(i, PluginRegistryRecord.class);
                if (record != null && StrUtil.isNotBlank(record.getRegistrationId())) {
                    records.add(record);
                }
            }
            return records;
        } catch (IOException ex) {
            return Collections.emptyList();
        }
    }

    @Override
    public void saveAll(List<PluginRegistryRecord> records) {
        Path file = resolveFilePath();
        if (file == null) {
            return;
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String json = JSON.toJSONString(records == null ? Collections.emptyList() : records, true);
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to persist plugin registry snapshot", ex);
        }
    }

    private Path resolveFilePath() {
        String path = configService == null ? null : configService.getByValTag("plugin_registry_file");
        String normalized = StrUtil.isBlank(path) ? DEFAULT_PLUGIN_REGISTRY_FILE : path.trim();
        return Paths.get(normalized);
    }
}
