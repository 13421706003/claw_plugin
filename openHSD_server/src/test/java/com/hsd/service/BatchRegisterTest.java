package com.hsd.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 批量注册测试账户
 * 
 * 注册50个测试账户（testuser1-50），密码统一为123456
 * 将userId和token保存到Redis（key: hsd:userId, value: token）
 */
@Slf4j
@SpringBootTest
public class BatchRegisterTest {

    @Autowired
    private UserService userService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    public void batchRegister() {
        int successCount = 0;
        int failCount = 0;
        String password = "123456";

        log.info("========== 开始批量注册测试账户 ==========");

        for (int i = 20; i <= 40; i++) {
            String username = "testuser" + i;
            
            try {
                // 注册账户
                userService.register(username, password);
                log.info("[{}] 注册成功：username={}", i, username);

                // 登录获取token和userId
                Map<String, Object> loginResult = userService.login(username, password);
                String token = (String) loginResult.get("token");
                String userId = (String) loginResult.get("userId");

                // 保存到Redis（key: hsd:userId, value: token）
                String redisKey = "hsd:" + userId;
                redisTemplate.opsForValue().set(redisKey, token, 7, TimeUnit.DAYS);

                log.info("[{}] 登录成功 - userId: {}, token: {}", i, userId, token);
                log.info("[{}] 已保存到Redis - key: {}, value: {}", i, redisKey, token);
                
                successCount++;
            } catch (Exception e) {
                log.error("[{}] 注册失败 - username: {}, 错误: {}", i, username, e.getMessage());
                failCount++;
            }
        }

        log.info("========== 批量注册完成 ==========");
        log.info("成功数量: {}", successCount);
        log.info("失败数量: {}", failCount);
    }
}
