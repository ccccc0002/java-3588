package com.yihecode.camera.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

/**
 * 描述：swagger配置
 * <p>
 * 日期: 2023/6/1
 * @author 465769438@qq.com
 */
@Configuration
public class SwaggerConfig {

    /**
     * 创建API应用
     * apiInfo() 增加API相关信息
     * 通过select()函数返回一个ApiSelectorBuilder实例,用来控制哪些接口暴露给Swagger来展现，
     * 本例采用指定扫描的包路径来定义指定要建立API的目录。
     * swagger测试地址：http://127.0.0.1:8080/swagger-ui/index.html#/
     * @return
     */
    @Bean
    public Docket createRestApi() {
        return new Docket(DocumentationType.OAS_30)
                .apiInfo(apiInfo())
                .enable(true)
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.yihecode.camera.ai.web"))
                .paths(PathSelectors.any())
                .build();
    }

    /**
     * 创建该API的基本信息
     * 访问地址：http://项目实际地址/swagger-ui.html
     * @return
     */
    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("AI视频监控管理")
                .description("AI视频监控管理平台")
                .contact(new Contact("zhoumingxing", "", "465769438@qq.com"))
                .version("1.0")
                .build();
    }

}
