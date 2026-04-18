package com.anxin.travel.agent.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 语音服务
 * 提供语音识别（ASR）和文字转语音（TTS）功能
 */
@Slf4j
@Service
public class VoiceService {

    @Value("${tongyi.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 语音转文字（ASR）
     * 使用通义千问 Paraformer 模型
     * 
     * @param audioBase64 Base64 编码的音频数据
     * @param dialectType 方言类型（mandarin/cantonese/sichuan等）
     * @return 识别结果
     */
    public Map<String, Object> voiceToText(String audioBase64, String dialectType) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("【语音识别】开始识别，方言类型：{}，音频大小：{} bytes", 
                dialectType, audioBase64 != null ? audioBase64.length() : 0);
            
            if (audioBase64 == null || audioBase64.isEmpty()) {
                throw new IllegalArgumentException("音频数据不能为空");
            }
            
            // 去除 data URI 前缀（如果有）
            String cleanBase64 = audioBase64;
            if (audioBase64.startsWith("data:")) {
                int commaIndex = audioBase64.indexOf(',');
                if (commaIndex > 0) {
                    cleanBase64 = audioBase64.substring(commaIndex + 1);
                }
            }
            
            // 调用通义千问语音识别 API
            String url = "https://dashscope.aliyuncs.com/api/v1/services/audio/asr/transcription";
            
            JSONObject body = new JSONObject();
            body.put("model", "paraformer-v2");  // 通义千问语音识别模型
            
            JSONObject input = new JSONObject();
            input.put("audio", "data:audio/wav;base64," + cleanBase64);
            
            // 方言映射到通义千问支持的语言代码
            String languageCode = mapDialectToLanguageCode(dialectType);
            input.put("language", languageCode);
            
            body.put("input", input);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("X-DashScope-DataInspection", "disable");  // 禁用数据检查
            
            HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);
            
            String resp = restTemplate.postForObject(url, entity, String.class);
            log.info("【语音识别】API 响应：{}", resp);
            
            // 解析响应
            JSONObject json = JSON.parseObject(resp);
            
            // 检查是否有错误
            if (json.containsKey("code")) {
                String errorCode = json.getString("code");
                String errorMsg = json.getString("message");
                log.error("【语音识别】API 返回错误：code={}, message={}", errorCode, errorMsg);
                throw new RuntimeException("语音识别失败：" + errorMsg);
            }
            
            // 提取识别结果
            JSONObject output = json.getJSONObject("output");
            if (output == null || !output.containsKey("text")) {
                throw new RuntimeException("语音识别失败：响应格式异常");
            }
            
            String recognizedText = output.getString("text");
            
            result.put("text", recognizedText);
            result.put("dialectType", dialectType);
            result.put("confidence", 0.95);  // 通义千问不返回置信度，使用默认值
            result.put("duration", 0L);  // 音频时长需要前端传递或另行计算
            
            log.info("✅ 语音识别成功：{}", recognizedText);
            return result;
            
        } catch (IllegalArgumentException e) {
            log.warn("【语音识别】参数校验失败：{}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("【语音识别】识别失败", e);
            throw new RuntimeException("语音识别失败：" + e.getMessage());
        }
    }

    /**
     * 文字转语音（TTS）
     * 使用通义千问 CosyVoice 模型
     * 
     * @param text 要转换的文字
     * @param voiceType 音色类型（xiaoyun/longan/longcheng等）
     * @param speed 语速（0-100，默认 50）
     * @param volume 音量（0-100，默认 50）
     * @return 音频数据
     */
    public Map<String, Object> textToSpeech(String text, String voiceType, Integer speed, Integer volume) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("【文字转语音】开始转换，文字长度：{}，音色：{}", text != null ? text.length() : 0, voiceType);
            
            if (text == null || text.trim().isEmpty()) {
                throw new IllegalArgumentException("文字不能为空");
            }
            
            // 默认参数
            if (voiceType == null || voiceType.isEmpty()) {
                voiceType = "xiaoyun";  // 默认音色：小云
            }
            if (speed == null) {
                speed = 50;
            }
            if (volume == null) {
                volume = 50;
            }
            
            // 调用通义千问 TTS API
            String url = "https://dashscope.aliyuncs.com/api/v1/services/audio/tts/speech-synthesis";
            
            JSONObject body = new JSONObject();
            body.put("model", "cosyvoice-v1");  // 通义千问 TTS 模型
            
            JSONObject input = new JSONObject();
            input.put("text", text);
            
            // 音色映射
            String modelVoice = mapVoiceTypeToModelVoice(voiceType);
            input.put("voice", modelVoice);
            
            body.put("input", input);
            
            // 参数配置
            JSONObject parameters = new JSONObject();
            parameters.put("sample_rate", 16000);  // 采样率
            parameters.put("format", "mp3");  // 音频格式
            parameters.put("volume", volume);  // 音量
            parameters.put("speed", speed);  // 语速
            
            body.put("parameters", parameters);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            headers.setAccept(java.util.Collections.singletonList(MediaType.parseMediaType("audio/mpeg")));
            
            HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);
            
            // TTS API 返回的是二进制音频数据
            ResponseEntity<byte[]> response = restTemplate.exchange(
                url, 
                HttpMethod.POST, 
                entity, 
                byte[].class
            );
            
            byte[] audioData = response.getBody();
            if (audioData == null || audioData.length == 0) {
                throw new RuntimeException("TTS 失败：返回空音频数据");
            }
            
            // 转换为 Base64
            String audioBase64 = Base64.getEncoder().encodeToString(audioData);
            
            result.put("audioBase64", "data:audio/mp3;base64," + audioBase64);
            result.put("duration", (long) (audioData.length * 0.05));  // 粗略估算时长（毫秒）
            result.put("format", "mp3");
            
            log.info("✅ 文字转语音成功，音频大小：{} bytes", audioData.length);
            return result;
            
        } catch (IllegalArgumentException e) {
            log.warn("【文字转语音】参数校验失败：{}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("【文字转语音】转换失败", e);
            throw new RuntimeException("文字转语音失败：" + e.getMessage());
        }
    }

    /**
     * 将方言类型映射到通义千问支持的语言代码
     */
    private String mapDialectToLanguageCode(String dialectType) {
        if (dialectType == null) {
            return "zh";
        }
        
        switch (dialectType.toLowerCase()) {
            case "mandarin":
            case "auto":
                return "zh";  // 中文普通话
            case "cantonese":
                return "yue";  // 粤语
            case "sichuan":
            case "lmz":
                return "zh";  // 四川话暂用中文
            case "henan":
                return "zh";  // 河南话暂用中文
            case "hunan":
                return "zh";  // 湖南话暂用中文
            case "fujian":
            case "minnan":
                return "zh";  // 闽南语暂用中文
            default:
                return "zh";
        }
    }

    /**
     * 将音色类型映射到通义千问模型音色
     */
    private String mapVoiceTypeToModelVoice(String voiceType) {
        switch (voiceType.toLowerCase()) {
            case "xiaoyun":
                return "longxiaochun";  // 小云（甜美女声）
            case "longan":
                return "longan";  // 龙安（成熟男声）
            case "longcheng":
                return "longcheng";  // 龙城（青年男声）
            case "longhua":
                return "longhua";  // 龙华（甜美女声）
            case "longxia":
                return "longxia";  // 龙夏（活泼女声）
            default:
                return "longxiaochun";  // 默认小云
        }
    }
}
