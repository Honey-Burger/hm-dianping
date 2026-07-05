package com.hmdp.utils;

import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class LoginInterceptor implements HandlerInterceptor {//拦截器，用于验证用户登录
    /***
     * 这里为第二个拦截器
     * 拦截器只用于验证用户登录
     ***/
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        //1.判断是否需要拦截（ThreadLocal中是否有用户）
        if (UserHolder.getUser() == null) {
            //2.如果没有，则拦截
            response.setStatus(401);
            return false;
        }
        return true;
    }
}
