package com.yihecode.camera.ai.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ModelTestCaptureService {

    private static final long DEFAULT_TIMEOUT_SECONDS = 20L;

    public String capture(String modelDir, String rtspUrl, String ffmpegBin) {
        if (StrUtil.isBlank(modelDir) || StrUtil.isBlank(rtspUrl)) {
            return null;
        }
        String executable = StrUtil.isBlank(ffmpegBin) ? "ffmpeg" : ffmpegBin.trim();
        String dateDir = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MMdd"));
        File targetDir = new File(modelDir, dateDir);
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return null;
        }

        String fileName = IdUtil.fastSimpleUUID() + ".jpg";
        File outputFile = new File(targetDir, fileName);
        List<String> command = buildCaptureCommand(executable, rtspUrl, outputFile);
        Process process = null;
        try {
            process = startProcess(command);
            if (!process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                deleteIfExists(outputFile);
                return null;
            }
            if (process.exitValue() != 0 || !outputFile.exists() || outputFile.length() <= 0) {
                deleteIfExists(outputFile);
                return null;
            }
            return dateDir + "/" + fileName;
        } catch (Exception e) {
            deleteIfExists(outputFile);
            return null;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    List<String> buildCaptureCommand(String ffmpegBin, String rtspUrl, File outputFile) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegBin);
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

    Process startProcess(List<String> command) throws Exception {
        return new ProcessBuilder(command).redirectErrorStream(true).start();
    }

    private void deleteIfExists(File file) {
        if (file != null && file.exists()) {
            // ignore deletion failure for temporary file
            file.delete();
        }
    }
}
