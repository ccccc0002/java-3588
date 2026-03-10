package com.yihecode.camera.ai.javacv;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Capture a snapshot from an RTSP stream using an external ffmpeg binary.
 */
@Slf4j
@Component
public class TakePhoto {

    @Value("${uploadDir}")
    private String uploadDir;

    @Value("${media.ffmpeg.bin:ffmpeg}")
    private String defaultFfmpegBin;

    @Value("${takePhotoTimeoutSeconds:25}")
    private long takePhotoTimeoutSeconds;

    public String take(String rtspUrl) {
        return take(rtspUrl, defaultFfmpegBin);
    }

    public String take(String rtspUrl, String ffmpegBin) {
        if (StrUtil.isBlank(rtspUrl)) {
            return null;
        }

        File uploadDirectory = new File(uploadDir);
        if (!uploadDirectory.exists() && !uploadDirectory.mkdirs()) {
            log.warn("failed to create upload directory: {}", uploadDirectory.getAbsolutePath());
            return null;
        }

        String fileName = IdUtil.fastSimpleUUID() + ".jpg";
        File outputFile = new File(uploadDirectory, fileName);
        boolean captured = captureSnapshot(resolveFfmpegBin(ffmpegBin), rtspUrl, outputFile);
        if (!captured || !outputFile.isFile() || outputFile.length() <= 0) {
            deleteQuietly(outputFile);
            return null;
        }
        return fileName;
    }

    boolean captureSnapshot(String ffmpegBin, String rtspUrl, File outputFile) {
        Process process = null;
        CompletableFuture<String> outputFuture = null;
        try {
            process = startProcess(buildCaptureCommand(ffmpegBin, rtspUrl, outputFile));
            Process currentProcess = process;
            outputFuture = CompletableFuture.supplyAsync(() -> safeReadProcessOutput(currentProcess));
            if (!process.waitFor(takePhotoTimeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("ffmpeg snapshot timed out for stream {}", rtspUrl);
                return false;
            }
            int exitCode = process.exitValue();
            String output = awaitProcessOutput(outputFuture);
            if (exitCode != 0) {
                log.warn("ffmpeg snapshot failed for stream {}, exitCode={}, output={}", rtspUrl, exitCode, output);
                return false;
            }
            return true;
        } catch (Exception ex) {
            log.warn("ffmpeg snapshot command failed for stream {}", rtspUrl, ex);
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
            if (outputFuture != null && !outputFuture.isDone()) {
                outputFuture.cancel(true);
            }
        }
    }

    List<String> buildCaptureCommand(String ffmpegBin, String rtspUrl, File outputFile) {
        List<String> command = new ArrayList<>();
        command.add(resolveFfmpegBin(ffmpegBin));
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("error");
        command.add("-y");
        command.add("-rtsp_transport");
        command.add("tcp");
        command.add("-i");
        command.add(rtspUrl);
        command.add("-an");
        command.add("-sn");
        command.add("-frames:v");
        command.add("1");
        command.add("-q:v");
        command.add("2");
        command.add(outputFile.getAbsolutePath());
        return command;
    }

    Process startProcess(List<String> command) throws IOException {
        return new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
    }

    String readProcessOutput(Process process) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return String.join("\n", lines);
    }

    private String safeReadProcessOutput(Process process) {
        try {
            return readProcessOutput(process);
        } catch (IOException ex) {
            return "failed to read ffmpeg output: " + ex.getMessage();
        }
    }

    private String awaitProcessOutput(CompletableFuture<String> outputFuture) {
        if (outputFuture == null) {
            return "";
        }
        try {
            return outputFuture.get(2, TimeUnit.SECONDS);
        } catch (Exception ex) {
            return "ffmpeg output unavailable: " + ex.getMessage();
        }
    }

    private String resolveFfmpegBin(String ffmpegBin) {
        return StrUtil.isBlank(ffmpegBin) ? defaultFfmpegBin : ffmpegBin;
    }

    private void deleteQuietly(File outputFile) {
        if (outputFile != null && outputFile.exists() && !outputFile.delete()) {
            log.warn("failed to delete incomplete snapshot file: {}", outputFile.getAbsolutePath());
        }
    }
}
