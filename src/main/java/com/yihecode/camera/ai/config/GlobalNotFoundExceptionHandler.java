package com.yihecode.camera.ai.config;

import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * 404异常处理
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Controller
public class GlobalNotFoundExceptionHandler extends BasicErrorController {

    public GlobalNotFoundExceptionHandler(ServerProperties serverProperties) {
        super(new DefaultErrorAttributes(), serverProperties.getError());
    }

    @Override
    public ResponseEntity<Map<String, Object>> error(HttpServletRequest request) {
        Map<String, Object> resMap = new HashMap<>();
        resMap.put("code", 500);
        resMap.put("msg", "找不到系统资源");
        return new ResponseEntity<>(resMap, HttpStatus.OK);
    }

    @Override
    public ModelAndView errorHtml(HttpServletRequest request, HttpServletResponse response) {
        ModelAndView mav = new ModelAndView("404");
        return mav;
    }
}
