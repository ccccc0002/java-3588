package com.yihecode.camera.ai.web;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.yihecode.camera.ai.entity.Camera;
import com.yihecode.camera.ai.javacv.TakePhoto;
import com.yihecode.camera.ai.service.CameraService;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@SaCheckLogin
@Controller
@RequestMapping({"/testimage"})
public class TestImageController {

    @Autowired
    private CameraService cameraService;

    @Autowired
    private TakePhoto takePhoto;

    @Autowired
    private ConfigService configService;

    @GetMapping({"", "/"})
    public String index(ModelMap modelMap) {
        modelMap.addAttribute("defaultIndexCode", "1");
        return "test_image/index";
    }

    @GetMapping({"/ffmpeg"})
    public String ffmpegPage() {
        return "test_image/ffmpeg";
    }

    @PostMapping({"/get"})
    @ResponseBody
    public JsonResult getImage(String indexCode) {
        Camera camera = resolveCamera(indexCode);
        if (camera == null || StrUtil.isBlank(camera.getRtspUrl())) {
            return JsonResultUtils.fail("鏈壘鍒板彲鐢ㄦ憚鍍忓ご");
        }

        String fileName = takePhoto.take(camera.getRtspUrl(), resolveFfmpegBin());
        if (StrUtil.isBlank(fileName)) {
            return JsonResultUtils.fail("鎶撳浘澶辫触");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("indexCode", defaultIfBlank(indexCode, String.valueOf(camera.getId())));
        data.put("cameraId", camera.getId());
        data.put("cameraName", defaultIfBlank(camera.getName(), "camera-" + camera.getId()));
        data.put("params", defaultIfBlank(camera.getName(), camera.getRtspUrl()));
        data.put("result", "success");
        data.put("time", DateUtil.now());
        data.put("picUrl", "/image/stream?fileName=" + fileName);
        return JsonResultUtils.success(data);
    }

    @PostMapping({"/ffmpeg"})
    @ResponseBody
    public JsonResult ffmpeg(String encoder, String decoder) {
        String ffmpegBin = defaultIfBlank(configService.getByValTag("media_ffmpeg_bin"), "ffmpeg");
        String encoders = runFfmpegCommand(ffmpegBin, "-encoders");
        String decoders = runFfmpegCommand(ffmpegBin, "-decoders");

        Map<String, Object> data = new HashMap<>();
        data.put("ffmpegBin", ffmpegBin);
        data.put("encoderc", matchCodec(encoders, encoder));
        data.put("decoderc", matchCodec(decoders, decoder));
        data.put("encodecs", encoders);
        data.put("decodecs", decoders);
        return JsonResultUtils.success(data);
    }

    Camera resolveCamera(String indexCode) {
        List<Camera> cameras = cameraService.listData();
        if (cameras == null || cameras.isEmpty()) {
            return null;
        }

        String normalized = defaultIfBlank(indexCode, "").trim();
        if (!normalized.isEmpty()) {
            for (Camera camera : cameras) {
                if (camera == null) {
                    continue;
                }
                if (String.valueOf(camera.getId()).equals(normalized)) {
                    return camera;
                }
                if (normalized.equals(defaultIfBlank(camera.getName(), ""))) {
                    return camera;
                }
                if (defaultIfBlank(camera.getName(), "").contains(normalized)) {
                    return camera;
                }
            }
        }

        for (Camera camera : cameras) {
            if (camera != null && camera.getVideoPlay() != null && camera.getVideoPlay() == 1 && StrUtil.isNotBlank(camera.getRtspUrl())) {
                return camera;
            }
        }

        for (Camera camera : cameras) {
            if (camera != null && StrUtil.isNotBlank(camera.getRtspUrl())) {
                return camera;
            }
        }
        return null;
    }

    String runFfmpegCommand(String ffmpegBin, String flag) {
        Process process = null;
        try {
            process = new ProcessBuilder(ffmpegBin, "-hide_banner", flag)
                    .redirectErrorStream(true)
                    .start();
            String output = readProcessOutput(process);
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "ffmpeg command timed out";
            }
            return output;
        } catch (Exception ex) {
            return "ffmpeg command failed: " + ex.getMessage();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private String readProcessOutput(Process process) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return String.join("\n", lines);
    }

    private String matchCodec(String source, String codecName) {
        String normalizedCodec = defaultIfBlank(codecName, "").trim();
        if (normalizedCodec.isEmpty()) {
            return "";
        }
        for (String line : defaultIfBlank(source, "").split("\\R")) {
            if (line.contains(normalizedCodec)) {
                return line.trim();
            }
        }
        return normalizedCodec + " not found";
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StrUtil.isBlank(value) ? defaultValue : value;
    }

    private String resolveFfmpegBin() {
        return defaultIfBlank(configService.getByValTag("media_ffmpeg_bin"), "ffmpeg");
    }
}
