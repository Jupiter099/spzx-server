package com.atguigu.spzx.manager.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.atguigu.spzx.common.exception.GuiGuException;
import com.atguigu.spzx.manager.mapper.SysUserMapper;
import com.atguigu.spzx.manager.service.SysUserService;
import com.atguigu.spzx.model.dto.system.LoginDto;
import com.atguigu.spzx.model.entity.system.SysUser;
import com.atguigu.spzx.model.vo.common.ResultCodeEnum;
import com.atguigu.spzx.model.vo.system.LoginVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class SysUserServiceImpl implements SysUserService {

    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public LoginVo login(LoginDto loginDto) {
        //读取验证码
        Object o = redisTemplate.opsForValue().get("user:validate" + loginDto.getCodeKey());
        //校验验证码
        if (o instanceof String) {
            String redisCode = (String) o;
            if (!StrUtil.equalsAnyIgnoreCase(redisCode, loginDto.getCaptcha())) {
                throw new GuiGuException(ResultCodeEnum.VALIDATECODE_ERROR);
            }
        } else {
            throw new GuiGuException(ResultCodeEnum.VALIDATECODE_ERROR);
        }
        //1. 获取提交用户名
        String userName = loginDto.getUserName();
        //2. 查询数据库
        SysUser sysUser = sysUserMapper.selectByUserName(userName);
        //3. 查不到
        if (sysUser == null) {
            throw new GuiGuException(ResultCodeEnum.LOGIN_ERROR);
        }
        //4. 查到
        String databasePassword = sysUser.getPassword();
        String inputPassword =
                DigestUtils.md5DigestAsHex(loginDto.getPassword().getBytes());
        //5. 验证密码
        if (!databasePassword.equals(inputPassword)) {
            throw new GuiGuException(ResultCodeEnum.LOGIN_ERROR);
        }
        //6. 登录成功则生成token
        String token = UUID.randomUUID().toString().replace("-", "");
        //7. 存放到redis
        redisTemplate.opsForValue().set("user:login" + token, JSON.toJSONString(sysUser), 7, TimeUnit.DAYS);

        LoginVo loginVo = new LoginVo();
        loginVo.setToken(token);
        //删除redis中图片验证码
        redisTemplate.delete("user:validate" + loginDto.getCodeKey());
        return loginVo;
    }

    @Override
    public SysUser getUserInfo(String token) {
        Object o = redisTemplate.opsForValue().get("user:login" + token);
        if (o instanceof String){
            String userJson = (String) o;
            SysUser sysUser = JSON.parseObject(userJson, SysUser.class);
            return sysUser;
        }else {
            throw new GuiGuException(ResultCodeEnum.DATA_ERROR);
        }
    }

    @Override
    public void logout(String token) {
        redisTemplate.delete("user:login" + token);
    }
}
