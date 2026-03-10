package com.yihecode.camera.ai.config;

import com.yihecode.camera.ai.YihecodeServerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import static org.junit.jupiter.api.Assertions.assertFalse;

class WebMvcAutoConfigurationCompatibilityTest {

    @Test
    void applicationShouldNotForceEnableWebMvc() {
        assertFalse(YihecodeServerApplication.class.isAnnotationPresent(EnableWebMvc.class));
    }

    @Test
    void webConfigurationShouldNotExtendWebMvcConfigurationSupport() {
        assertFalse(WebMvcConfigurationSupport.class.isAssignableFrom(WebConfiguration.class));
    }
}