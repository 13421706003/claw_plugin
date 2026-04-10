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

/**
 * 用户服务实现类
 * 
 * 提供用户登录、注册等核心业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    /**
     * 用户登录
     * 
     * @param username 用户名
     * @param password 密码（明文）
     * @return 登录结果，包含 token、userId、username
     * @throws RuntimeException 用户名或密码错误时抛出异常
     */
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

    /**
     * 用户注册
     * 
     * @param username 用户名
     * @param password 密码（明文，长度不少于6位）
     * @throws RuntimeException 用户名为空、密码过短或用户名已存在时抛出异常
     */
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

    /**
     * 修改用户名
     * 
     * @param userId 用户ID
     * @param newUsername 新用户名
     * @throws RuntimeException 新用户名为空或已被其他用户占用时抛出异常
     */
    @Override
    public void updateUsername(Long userId, String newUsername) {
        if (newUsername == null || newUsername.trim().isEmpty()) {
            throw new RuntimeException("新用户名不能为空");
        }

        // 检查新用户名是否已被其他用户占用
        User existing = userMapper.findByUsername(newUsername);
        if (existing != null && !existing.getId().equals(userId)) {
            throw new RuntimeException("用户名已被占用");
        }

        userMapper.updateUsername(userId, newUsername);
        log.info("[UserServiceImpl] 用户名修改成功：userId={}，newUsername={}", userId, newUsername);
    }

    /**
     * 修改密码
     * 
     * @param userId 用户ID
     * @param oldPassword 旧密码（明文）
     * @param newPassword 新密码（明文，长度不少于6位）
     * @throws RuntimeException 旧密码错误或新密码过短时抛出异常
     */
    @Override
    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new RuntimeException("新密码长度不能少于6位");
        }

        // 验证旧密码
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        String oldPasswordHash = md5(oldPassword);
        if (!oldPasswordHash.equals(user.getPasswordHash())) {
            throw new RuntimeException("旧密码错误");
        }

        // 更新密码
        String newPasswordHash = md5(newPassword);
        userMapper.updatePassword(userId, newPasswordHash);
        log.info("[UserServiceImpl] 密码修改成功：userId={}", userId);
    }

    /**
     * MD5 加密
     * 
     * @param input 原始字符串
     * @return MD5 哈希值
     */
    private String md5(String input) {
        return DigestUtils.md5DigestAsHex(input.getBytes(StandardCharsets.UTF_8));
    }
}
