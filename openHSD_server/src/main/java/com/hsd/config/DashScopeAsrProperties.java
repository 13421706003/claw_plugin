package com.hsd.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云百炼（DashScope）语音识别配置。
 * <a href="https://www.alibabacloud.com/help/zh/model-studio/real-time-speech-recognition">实时语音识别文档</a>
 */
@Data
@ConfigurationProperties(prefix = "dashscope.asr")
public class DashScopeAsrProperties {

    /** 是否启用 */
    private boolean enabled = true;

    /**
     * 百炼 API Key（sk- 开头）。建议用环境变量 DASHSCOPE_API_KEY，勿提交到仓库。
     */
    private String apiKey = "";

    /**
     * 地域：cn 北京（中国内地），intl 新加坡（国际）。需与控制台创建 API Key 的地域一致。
     */
    private String region = "cn";

    /** 模型，如 paraformer-realtime-v2、fun-asr-realtime */
    private String model = "paraformer-realtime-v2";

    /** 未从扩展名推断出格式时的默认 format（pcm/wav/aac 等） */
    private String defaultFormat = "aac";

    /** 未指定采样率时使用的默认值（部分模型需要） */
    private int defaultSampleRate = 44100;

    public String resolvedApiKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.strip();
        }
        String env = System.getenv("DASHSCOPE_API_KEY");
        return env != null ? env.strip() : "";
    }
}
