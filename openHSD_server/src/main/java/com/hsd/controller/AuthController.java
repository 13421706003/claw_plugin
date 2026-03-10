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

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        Map<String, Object> result = new HashMap<>();

        if (username == null || password == null) {
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

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        Map<String, Object> result = new HashMap<>();

        if (username == null || username.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "用户名不能为空");
            return ResponseEntity.badRequest().body(result);
        }

        if (password == null || password.length() < 6) {
            result.put("success", false);
            result.put("message", "密码长度不能少于6位");
            return ResponseEntity.badRequest().body(result);
        }

        try {
            userService.register(username, password);
            result.put("success", true);
            result.put("message", "注册成功");
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            log.error("[AuthController] 注册失败：", e);
            result.put("success", false);
            result.put("message", e.getMessage() != null ? e.getMessage() : "注册失败，请稍后重试");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[AuthController] 注册异常：", e);
            result.put("success", false);
            result.put("message", "系统错误，请稍后重试");
            return ResponseEntity.ok(result);
        }
    }
}
