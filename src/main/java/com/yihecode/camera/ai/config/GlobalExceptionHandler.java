package com.yihecode.camera.ai.config;

import cn.dev33.satoken.exception.NotLoginException;
import com.yihecode.camera.ai.exception.BizException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 全局异常处理
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 异常处理
     * @param request
     * @param response
     * @param e
     * @return
     */
    @ExceptionHandler(value = Exception.class)
    public ModelAndView errorHandler(HttpServletRequest request, HttpServletResponse response, Exception e) {
        //
        if(e instanceof NotLoginException) {
            boolean isAjax = this.isAjax(request);
            if(isAjax) {
                ModelAndView mav = new ModelAndView(new MappingJackson2JsonView());
                mav.addObject("code", 500);
                mav.addObject("msg", "登录失效，请重新登录");
                return mav;
            }
            //
            ModelAndView mav = new ModelAndView("redirect:/login");
            return mav;
        }

        //
        String errorMsg = "系统异常，请稍后重试.";
        if(e instanceof BindException) {
            errorMsg = "参数错误";
        } else if(e instanceof BizException) {
            errorMsg = e.getMessage();
        } else {
            e.printStackTrace();
        }

        boolean isAjax = this.isAjax(request);
        if(isAjax) {
            ModelAndView mav = new ModelAndView(new MappingJackson2JsonView());
//            mav.addObject("code", response.getStatus());
            mav.addObject("code", 500);
            mav.addObject("msg", errorMsg);
            return mav;
        } else {
            ModelAndView mav = new ModelAndView("500");
            mav.addObject("error", errorMsg);
            return mav;
        }
    }



    /*
     * 判断ajax请求
     * @param request
     * @return
     */
    private boolean isAjax(HttpServletRequest request){
        return (request.getHeader("X-Requested-With") != null  && "XMLHttpRequest".equals(request.getHeader("X-Requested-With"))   ) ;
    }

}
