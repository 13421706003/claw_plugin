package com.hsd.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 测试类
 * 
 * 运行方式：
 *   mvn test -Dtest=JwtUtilTest
 * 
 * 或在 IDE 中右键运行
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    // 与 application.yaml 中配置保持一致
    private static final String TEST_SECRET = "openhsd-gateway-jwt-secret-key-2026-make-it-long-enough-256bit";
    private static final String TEST_ISSUER = "openhsd";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // 手动注入配置值（不启动 Spring 容器）
        ReflectionTestUtils.setField(jwtUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "issuer", TEST_ISSUER);
    }

    @Test
    void testGenerateAndParseToken() {
        // 生成 Token
        Long userId = 1L;
        String username = "testuser";
        String token = jwtUtil.generateToken(userId, username);

        System.out.println("========================================");
        System.out.println("生成的 Token:");
        System.out.println(token);
        System.out.println("========================================");

        // 验证 Token 有效
        assertTrue(jwtUtil.isValid(token), "Token 应该有效");

        // 解析 Token
        Claims claims = jwtUtil.parseToken(token);
        assertNotNull(claims, "Claims 不应为 null");

        System.out.println("解析结果:");
        System.out.println("  Subject (userId): " + claims.getSubject());
        System.out.println("  Issuer: " + claims.getIssuer());
        System.out.println("  Username: " + claims.get("username", String.class));
        System.out.println("  IssuedAt: " + claims.getIssuedAt());

        assertEquals(String.valueOf(userId), claims.getSubject());
        assertEquals(username, claims.get("username", String.class));
    }

    @Test
    void testGetUserId() {
        Long userId = 123L;
        String token = jwtUtil.generateToken(userId, "testuser");

        Long extractedUserId = jwtUtil.getUserId(token);
        assertEquals(userId, extractedUserId);
        System.out.println("从 Token 提取的 userId: " + extractedUserId);
    }

    @Test
    void testGetUsername() {
        String username = "zhangsan";
        String token = jwtUtil.generateToken(1L, username);

        String extractedUsername = jwtUtil.getUsername(token);
        assertEquals(username, extractedUsername);
        System.out.println("从 Token 提取的 username: " + extractedUsername);
    }

    @Test
    void testInvalidToken() {
        String invalidToken = "invalid.token.here";
        
        assertFalse(jwtUtil.isValid(invalidToken), "无效 Token 应该返回 false");
        assertNull(jwtUtil.parseToken(invalidToken), "无效 Token 解析应该返回 null");
        assertNull(jwtUtil.getUserId(invalidToken), "无效 Token 提取 userId 应该返回 null");
        assertNull(jwtUtil.getUsername(invalidToken), "无效 Token 提取 username 应该返回 null");
        
        System.out.println("无效 Token 测试通过");
    }

    @Test
    void testTamperedToken() {
        // 生成有效 Token
        String token = jwtUtil.generateToken(1L, "testuser");
        
        // 篡改 Token（修改最后一个字符）
        String tamperedToken = token.substring(0, token.length() - 1) + "X";
        
        assertFalse(jwtUtil.isValid(tamperedToken), "篡改的 Token 应该无效");
        assertNull(jwtUtil.parseToken(tamperedToken), "篡改的 Token 解析应该返回 null");
        
        System.out.println("篡改 Token 测试通过");
    }

    @Test
    void testWrongSecretKey() {
        // 用正确的密钥生成 Token
        String token = jwtUtil.generateToken(1L, "testuser");

        // 创建另一个 JwtUtil 实例，使用不同的密钥
        JwtUtil wrongKeyUtil = new JwtUtil();
        ReflectionTestUtils.setField(wrongKeyUtil, "secret", "wrong-secret-key-wrong-secret-key-wrong");
        ReflectionTestUtils.setField(wrongKeyUtil, "issuer", TEST_ISSUER);

        // 用错误密钥验证应该失败
        assertFalse(wrongKeyUtil.isValid(token), "使用错误密钥验证应该失败");
        System.out.println("错误密钥测试通过");
    }

    @Test
    void testWrongIssuer() {
        // 用正确的 issuer 生成 Token
        String token = jwtUtil.generateToken(1L, "testuser");

        // 创建另一个 JwtUtil 实例，使用不同的 issuer
        JwtUtil wrongIssuerUtil = new JwtUtil();
        ReflectionTestUtils.setField(wrongIssuerUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(wrongIssuerUtil, "issuer", "wrong-issuer");

        // 用错误 issuer 验证应该失败
        assertFalse(wrongIssuerUtil.isValid(token), "使用错误 issuer 验证应该失败");
        System.out.println("错误 issuer 测试通过");
    }

    @Test
    void testPrintTokenForManualTesting() {
        // 这个测试用于手动生成一个 Token 供前端测试
        Long userId = 1L;
        String username = "admin";
        String token = jwtUtil.generateToken(userId, username);

        System.out.println("\n========================================");
        System.out.println("手动测试用 Token:");
        System.out.println("userId: " + userId);
        System.out.println("username: " + username);
        System.out.println("token: " + token);
        System.out.println("========================================\n");
    }

    /**
     * 【校验你的 Token】
     * 
     * 使用方法：
     * 1. 把下面的 YOUR_TOKEN_HERE 替换成你的实际 token
     * 2. 运行这个测试方法：mvn test -Dtest=JwtUtilTest#testVerifyMyToken
     */
    @Test
    void testVerifyMyToken() {
        // ===== 在这里填入你要校验的 Token =====
        String myToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJvcGVuaHNkIiwic3ViIjoiMTUiLCJ1c2VybmFtZSI6InRlc3R1c2VyIiwiaWF0IjoxNzczNzE5Mzc4fQ.7TtVU-ZEIjg6xgmXpg83GyowEF0qEiaoHzPsD_vta6M\n";
        // =====================================

        System.out.println("\n========================================");
        System.out.println("校验 Token:");
        System.out.println("Token: " + myToken);
        System.out.println("========================================");

        // 1. 检查 Token 格式是否有效
        boolean isValid = jwtUtil.isValid(myToken);
        System.out.println("Token 有效: " + isValid);

        if (!isValid) {
            System.out.println("Token 无效，请检查：");
            System.out.println("  - Token 是否被篡改");
            System.out.println("  - 密钥是否匹配");
            System.out.println("  - issuer 是否匹配");
            return;
        }

        // 2. 解析 Token 详情
        Claims claims = jwtUtil.parseToken(myToken);
        if (claims != null) {
            System.out.println("\nToken 解析结果:");
            System.out.println("  userId:   " + claims.getSubject());
            System.out.println("  username: " + claims.get("username", String.class));
            System.out.println("  issuer:   " + claims.getIssuer());
            System.out.println("  签发时间: " + claims.getIssuedAt());
        }

        // 3. 直接提取信息
        Long userId = jwtUtil.getUserId(myToken);
        String username = jwtUtil.getUsername(myToken);
        System.out.println("\n提取的信息:");
        System.out.println("  userId:   " + userId);
        System.out.println("  username: " + username);
        System.out.println("========================================\n");

        assertTrue(isValid, "Token 应该有效");
    }
}
