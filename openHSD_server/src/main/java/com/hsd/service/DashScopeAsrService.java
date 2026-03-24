package com.hsd.service;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.utils.Constants;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.hsd.config.DashScopeAsrProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/**
 * 阿里云百炼录音文件识别：使用 DashScope SDK {@link Recognition#call}（与官方 Paraformer 文件示例一致）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashScopeAsrService {

    private static final String WS_CN = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";
    private static final String WS_INTL = "wss://dashscope-intl.aliyuncs.com/api-ws/v1/inference";

    private final DashScopeAsrProperties props;

    @PostConstruct
    void applyWebsocketBaseUrl() {
        String r = props.getRegion() == null ? "cn" : props.getRegion().strip().toLowerCase(Locale.ROOT);
        if ("intl".equals(r) || "sg".equals(r) || "singapore".equals(r)) {
            Constants.baseWebsocketApiUrl = WS_INTL;
            log.info("[DashScopeAsr] WebSocket 接入：国际（新加坡）");
        } else {
            Constants.baseWebsocketApiUrl = WS_CN;
            log.info("[DashScopeAsr] WebSocket 接入：中国内地（北京）");
        }
    }

    public void requireEnabled() {
        if (!props.isEnabled()) {
            throw new IllegalStateException("百炼语音识别未启用（dashscope.asr.enabled=false）");
        }
        if (props.resolvedApiKey().isBlank()) {
            throw new IllegalStateException("未配置百炼 API Key：dashscope.asr.api-key 或环境变量 DASHSCOPE_API_KEY");
        }
    }

    /**
     * 将上传的音频写入临时文件并调用百炼识别，返回全文文本。
     */
    public String transcribeFile(byte[] audioBytes, String originalFilename) {
        requireEnabled();
        String name = originalFilename == null || originalFilename.isBlank() ? "audio.m4a" : originalFilename;
        String format = inferFormat(name);
        int sampleRate = props.getDefaultSampleRate();

        Path temp = null;
        try {
            temp = Files.createTempFile("ohsd_dashscope_", suffix(name));
            Files.write(temp, audioBytes, StandardOpenOption.TRUNCATE_EXISTING);
            File file = temp.toFile();

            String model =
                    props.getModel() == null || props.getModel().isBlank()
                            ? "paraformer-realtime-v2"
                            : props.getModel().strip();
            var b =
                    RecognitionParam.builder()
                            .model(model)
                            .apiKey(props.resolvedApiKey())
                            .format(format)
                            .sampleRate(sampleRate);
            if (model.contains("paraformer")) {
                b.parameter("language_hints", new String[] {"zh", "en"});
            }
            RecognitionParam param = b.build();
            Recognition recognizer = new Recognition();
            String raw = recognizer.call(param, file);
            String text = extractTranscribedText(raw);
            log.info("[DashScopeAsr] transcribe ok file={} format={} chars={}", name, format, text.length());
            return text;
        } catch (Exception e) {
            log.error("[DashScopeAsr] transcribe fail file={}", name, e);
            throw new IllegalStateException("百炼识别失败：" + e.getMessage(), e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }
    }

    /**
     * {@link Recognition#call} 返回的是 JSON（含 {@code sentences} 数组），需拼接 {@code text}；
     * 无识别结果时常为 {@code {"sentences":[]}}，不能再原样交给前端。
     */
    static String extractTranscribedText(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.strip();
        if (!s.startsWith("{") && !s.startsWith("[")) {
            return s;
        }
        try {
            JSONObject root = JSON.parseObject(s);
            if (root == null) {
                return "";
            }
            JSONArray sentences = root.getJSONArray("sentences");
            if (sentences != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < sentences.size(); i++) {
                    JSONObject sent = sentences.getJSONObject(i);
                    if (sent == null) {
                        continue;
                    }
                    String t = sent.getString("text");
                    if (t != null && !t.isEmpty()) {
                        if (sb.length() > 0) {
                            sb.append(' ');
                        }
                        sb.append(t);
                    }
                }
                return sb.toString().strip();
            }
            String direct = root.getString("text");
            if (direct != null && !direct.isBlank()) {
                return direct.strip();
            }
            return "";
        } catch (Exception e) {
            return s;
        }
    }

    private String inferFormat(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".wav")) {
            return "wav";
        }
        if (lower.endsWith(".mp3")) {
            return "mp3";
        }
        if (lower.endsWith(".pcm") || lower.endsWith(".raw")) {
            return "pcm";
        }
        if (lower.endsWith(".aac")) {
            return "aac";
        }
        if (lower.endsWith(".m4a") || lower.endsWith(".mp4")) {
            // 常见为 AAC-LC 封装于 MP4/M4A
            return "aac";
        }
        if (lower.endsWith(".opus")) {
            return "opus";
        }
        if (lower.endsWith(".amr")) {
            return "amr";
        }
        String d = props.getDefaultFormat();
        return d == null || d.isBlank() ? "aac" : d.strip();
    }

    private static String suffix(String fileName) {
        int i = fileName.lastIndexOf('.');
        return i >= 0 ? fileName.substring(i) : ".tmp";
    }
}
