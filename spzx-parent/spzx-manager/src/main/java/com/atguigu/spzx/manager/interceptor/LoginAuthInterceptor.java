package com.atguigu.spzx.manager.interceptor;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.atguigu.spzx.model.entity.system.SysUser;
import com.atguigu.spzx.model.vo.common.Result;
import com.atguigu.spzx.model.vo.common.ResultCodeEnum;
import com.atguigu.spzx.utils.AuthContextUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.TimeUnit;

/**
 * 拦截器
 */
@Component
public class LoginAuthInterceptor implements HandlerInterceptor {

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.1 当前请求方式为options 预检请求，直接放行
        if ("OPTIONS".equals(request.getMethod())){
            return true;
        }
        //1.2 从请求头中获取token
        String token = request.getHeader("token");
        //1.3 token为空，返回错误提示
        if (StrUtil.isEmpty(token)){
            responseNoLoginInfo(response);
            return false;
        }
        //1.4 如果token不为空，查询redis
        Object o = redisTemplate.opsForValue().get("user:login" + token);
        if (o == null){
            responseNoLoginInfo(response);
            return false;
        }
        if (o instanceof String){
            //1.5 查询不到返回错误提示，查询到将用户信息放到ThreadLocal中
            String sysUserJson = (String) o;
            SysUser sysUser = JSON.parseObject(sysUserJson, SysUser.class);
            AuthContextUtil.set(sysUser);
        }
        //1.6 更新redis key的过期时间
        redisTemplate.expire("user:login" + token,30, TimeUnit.MINUTES);
        //1.7 放行
        return true;

    }

    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //ThreadLocal删除
        AuthContextUtil.remove();
    }

    //响应208状态码给前端
    private void responseNoLoginInfo(HttpServletResponse response) {
        Result<Object> result = Result.build(null, ResultCodeEnum.LOGIN_AUTH);
        PrintWriter writer = null;
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/html; charset=utf-8");
        try {
            writer = response.getWriter();
            writer.print(JSON.toJSONString(result));
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) writer.close();
        }
    }

}
