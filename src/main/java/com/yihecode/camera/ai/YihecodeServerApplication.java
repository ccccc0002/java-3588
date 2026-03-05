package com.yihecode.camera.ai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import springfox.documentation.oas.annotations.EnableOpenApi;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@SpringBootApplication
@EnableSwagger2
@EnableOpenApi
@EnableWebMvc
@MapperScan({"com.yihecode.camera.ai.mapper"})
public class YihecodeServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(YihecodeServerApplication.class, args);
    }

}
