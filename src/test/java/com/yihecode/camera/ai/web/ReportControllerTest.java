package com.yihecode.camera.ai.web;

import com.yihecode.camera.ai.entity.Report;
import com.yihecode.camera.ai.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private ReportService reportService;

    @InjectMocks
    private ReportController reportController;

    @TempDir
    Path tempDir;

    @Test
    void getImageAsByteArray_shouldResolveRelativeReportImageFromUploadDir() throws Exception {
        Path imagePath = tempDir.resolve("relative-preview.jpg");
        Files.write(imagePath, "preview-image".getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(reportController, "uploadDir", tempDir.toFile().getAbsolutePath() + File.separator);

        Report report = new Report();
        report.setId(7L);
        report.setFileName("relative-preview.jpg");
        when(reportService.getById(7L)).thenReturn(report);

        MockHttpServletResponse response = new MockHttpServletResponse();
        reportController.getImageAsByteArray(7L, response);

        assertEquals("image/jpeg", response.getContentType());
        assertArrayEquals("preview-image".getBytes(StandardCharsets.UTF_8), response.getContentAsByteArray());
    }
}
