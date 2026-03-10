package com.hsd.service.impl;

import com.hsd.entity.User;
import com.hsd.mapper.UserMapper;
import com.hsd.service.UserService;
import com.hsd.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    @Override
    public Map<String, Object> login(String username, String password) {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        String inputHash = md5(password);
        if (!inputHash.equals(user.getPasswordHash())) {
            throw new RuntimeException("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        log.info("[UserServiceImpl] 用户登录成功：userId={}，username={}", user.getId(), user.getUsername());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", String.valueOf(user.getId()));
        result.put("username", user.getUsername());
        return result;
    }

    @Override
    public void register(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new RuntimeException("用户名不能为空");
        }
        if (password == null || password.length() < 6) {
            throw new RuntimeException("密码长度不能少于6位");
        }

        User existing = userMapper.findByUsername(username);
        if (existing != null) {
            throw new RuntimeException("用户名已存在");
        }

        String passwordHash = md5(password);
        userMapper.insert(username, passwordHash);
        log.info("[UserServiceImpl] 用户注册成功：username={}", username);
    }

    private String md5(String input) {
        return DigestUtils.md5DigestAsHex(input.getBytes(StandardCharsets.UTF_8));
    }
}
