package com.yihecode.camera.ai.javacv;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class TakePhotoTest {

    @TempDir
    Path tempDir;

    @Test
    void buildCaptureCommandShouldUseFfmpegSnapshotArguments() {
        TakePhoto takePhoto = new TakePhoto();
        File output = tempDir.resolve("frame.jpg").toFile();

        List<String> command = takePhoto.buildCaptureCommand("ffmpeg-rockchip", "rtsp://demo/stream", output);

        assertEquals("ffmpeg-rockchip", command.get(0));
        assertTrue(command.contains("-rtsp_transport"));
        assertTrue(command.contains("tcp"));
        assertTrue(command.contains("-frames:v"));
        assertFalse(command.contains("-rw_timeout"));
        assertEquals(output.getAbsolutePath(), command.get(command.size() - 1));
    }

    @Test
    void takeShouldReturnFileNameWhenCaptureSucceeds() throws Exception {
        TakePhoto takePhoto = spy(new TakePhoto());
        ReflectionTestUtils.setField(takePhoto, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(takePhoto, "defaultFfmpegBin", "ffmpeg");

        doAnswer(invocation -> {
            File output = invocation.getArgument(2);
            Files.writeString(output.toPath(), "frame");
            return true;
        }).when(takePhoto).captureSnapshot(eq("ffmpeg-rockchip"), eq("rtsp://demo/stream"), any(File.class));

        String fileName = takePhoto.take("rtsp://demo/stream", "ffmpeg-rockchip");

        assertNotNull(fileName);
        assertTrue(fileName.endsWith(".jpg"));
        assertTrue(Files.exists(tempDir.resolve(fileName)));
    }

    @Test
    void takeShouldReturnNullWhenCaptureFails() {
        TakePhoto takePhoto = spy(new TakePhoto());
        ReflectionTestUtils.setField(takePhoto, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(takePhoto, "defaultFfmpegBin", "ffmpeg");
        doReturn(false).when(takePhoto).captureSnapshot(eq("ffmpeg-rockchip"), eq("rtsp://demo/stream"), any(File.class));

        String fileName = takePhoto.take("rtsp://demo/stream", "ffmpeg-rockchip");

        assertNull(fileName);
    }
}
