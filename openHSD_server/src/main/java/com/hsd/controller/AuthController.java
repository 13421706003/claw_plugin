package com.hsd.controller;

import com.hsd.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AuthController {

    private final UserService userService;

    /**
     * 登录接口
     * POST /api/auth/login
     * Body: { "username": "xxx", "password": "xxx" }
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        Map<String, Object> result = new HashMap<>();

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            result.put("success", false);
            result.put("message", "用户名和密码不能为空");
            return ResponseEntity.badRequest().body(result);
        }

        try {
            Map<String, Object> data = userService.login(username, password);
            result.put("success", true);
            result.put("message", "登录成功");
            result.putAll(data);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }
}
