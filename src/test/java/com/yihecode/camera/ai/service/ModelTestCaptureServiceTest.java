package com.yihecode.camera.ai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelTestCaptureServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void buildCaptureCommand_shouldContainRtspAndOutput() {
        ModelTestCaptureService service = new ModelTestCaptureService();
        File output = tempDir.resolve("out.jpg").toFile();

        List<String> command = service.buildCaptureCommand("ffmpeg", "rtsp://127.0.0.1/test", output);

        assertEquals("ffmpeg", command.get(0));
        assertTrue(command.contains("-rtsp_transport"));
        assertTrue(command.contains("tcp"));
        assertTrue(command.contains("rtsp://127.0.0.1/test"));
        assertEquals(output.getAbsolutePath(), command.get(command.size() - 1));
    }

    @Test
    void capture_shouldReturnRelativeFileWhenProcessSuccess() {
        ModelTestCaptureService service = new ModelTestCaptureService() {
            @Override
            Process startProcess(List<String> command) throws Exception {
                String output = command.get(command.size() - 1);
                Files.write(Path.of(output), "img".getBytes(StandardCharsets.UTF_8));
                return new FakeProcess(0);
            }
        };

        String relative = service.capture(tempDir.toString(), "rtsp://127.0.0.1/test", "ffmpeg");

        assertNotNull(relative);
        assertTrue(relative.endsWith(".jpg"));
        assertTrue(tempDir.resolve(relative).toFile().exists());
    }

    @Test
    void capture_shouldReturnNullWhenInputInvalid() {
        ModelTestCaptureService service = new ModelTestCaptureService();

        assertNull(service.capture("", "rtsp://127.0.0.1/test", "ffmpeg"));
        assertNull(service.capture(tempDir.toString(), "", "ffmpeg"));
    }

    private static class FakeProcess extends Process {
        private final int exitCode;

        FakeProcess(int exitCode) {
            this.exitCode = exitCode;
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            return exitCode;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public void destroy() {
        }

        @Override
        public Process destroyForcibly() {
            return this;
        }

        @Override
        public boolean isAlive() {
            return false;
        }
    }
}
