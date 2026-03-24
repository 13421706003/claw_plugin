package com.hsd.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 启动时打印当前生效的 Spring Profile，便于确认是否加载 application-dev.yaml 等。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupProfileLogger implements ApplicationRunner {

    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            log.warn(
                    "[OpenHSD] 当前无 active profile，仅使用 application.yaml；若期望加载 application-dev.yaml，请检查 spring.profiles.active 是否被覆盖。");
        } else {
            log.info("[OpenHSD] 当前已激活 Spring Profile: {}", String.join(", ", active));
        }
        String envProfiles = System.getenv("SPRING_PROFILES_ACTIVE");
        if (envProfiles != null && !envProfiles.isBlank()) {
            log.info("[OpenHSD] 环境变量 SPRING_PROFILES_ACTIVE={}", envProfiles);
        } else {
            log.info("[OpenHSD] 环境变量 SPRING_PROFILES_ACTIVE 未设置；active 由 application.yaml 中 spring.profiles.active 解析（占位符默认 dev）。");
        }
    }
}
