package com.hsd.controller;

import com.hsd.service.DashScopeAsrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 语音识别：代理阿里云百炼（DashScope）录音文件识别，API Key 仅在服务端配置。
 *
 * @see <a href="https://www.alibabacloud.com/help/zh/model-studio/real-time-speech-recognition">实时语音识别</a>
 */
@Slf4j
@RestController
@RequestMapping("/api/speech/bailian")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class SpeechController {

    private final DashScopeAsrService dashScopeAsrService;

    /**
     * 上传音频，同步返回识别全文（一次请求完成，无需轮询）。
     *
     * @param file       音频文件（wav/mp3/m4a/aac 等，与百炼模型支持的 format 一致）
     * @param durationMs 可选，保留与旧客户端兼容，服务端可不使用
     */
    @PostMapping("/transcribe")
    public ResponseEntity<Map<String, Object>> transcribe(
            @RequestAttribute("userId") Long userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "durationMs", required = false) Long durationMs) {

        Map<String, Object> result = new HashMap<>();
        try {
            if (file == null || file.isEmpty()) {
                result.put("success", false);
                result.put("message", "请上传音频文件");
                return ResponseEntity.badRequest().body(result);
            }
            byte[] bytes = file.getBytes();
            String original = file.getOriginalFilename();
            String text =
                    dashScopeAsrService.transcribeFile(
                            bytes, original != null ? original : "audio.m4a");
            result.put("success", true);
            result.put("text", text);
            result.put("done", true);
            result.put("failed", false);
            log.info(
                    "[Speech] userId={} bailian transcribe ok size={} chars={}",
                    userId,
                    bytes.length,
                    text.length());
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            result.put("success", false);
            result.put("done", true);
            result.put("failed", true);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[Speech] transcribe error", e);
            result.put("success", false);
            result.put("done", true);
            result.put("failed", true);
            result.put("message", "识别失败：" + e.getMessage());
            return ResponseEntity.ok(result);
        }
    }
}
