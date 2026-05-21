package com.hfut.counselor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfut.counselor.dto.ChatRequest;
import com.hfut.counselor.entity.Conversation;
import com.hfut.counselor.entity.Message;
import com.hfut.counselor.service.ChatFileService.ChatFileRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    private final MessageService messageService;
    private final ConversationService conversationService;
    private final ChatFileService chatFileService;
    private final ObjectMapper objectMapper;

    private final WebClient webClient = WebClient.builder().build();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Value("${app.agent.base-url}")
    private String agentBaseUrl;

    @Value("${app.agent.agent-id}")
    private String defaultAgentId;

    @Value("${app.agent.timeout-seconds:300}")
    private long timeoutSeconds;

    public SseEmitter stream(String userId, ChatRequest request) {
        Conversation conversation = conversationService.requireOwned(request.getConversationId(), userId);
        conversationService.applyFirstMessageTitle(conversation, request.getContent());
        List<ChatFileRecord> files = chatFileService.requireOwnedFiles(userId, request.getFileIds());
        String attachmentJson = chatFileService.toAttachmentJson(files);
        Message userMessage = messageService.saveMessage(request.getConversationId(), "user", request.getContent(),
                attachmentJson, false, true);
        Message assistantMessage = messageService.saveMessage(request.getConversationId(), "assistant", "", true, false);
        conversationService.touch(request.getConversationId(), 2);

        SseEmitter emitter = new SseEmitter(timeoutSeconds * 1000L);
        executor.submit(new Runnable() {
            @Override
            public void run() {
                StringBuilder assistantContent = new StringBuilder();
                try {
                    List<Map<String, String>> messages = buildUpstreamMessages(request, files);
                    Map<String, Object> body = new HashMap<String, Object>();
                    body.put("messages", messages);

                    String url = trimRight(agentBaseUrl) + "/feign/chat/completions/"
                            + (request.getAgentId() == null || request.getAgentId().trim().isEmpty()
                            ? defaultAgentId : request.getAgentId());

                    webClient.post()
                            .uri(url)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.TEXT_EVENT_STREAM)
                            .bodyValue(body)
                            .retrieve()
                            .bodyToFlux(String.class)
                            .timeout(Duration.ofSeconds(timeoutSeconds))
                            .doOnNext(line -> handleUpstreamLine(line, emitter, assistantContent))
                            .blockLast();

                    messageService.completeAssistantMessage(assistantMessage.getId(), assistantContent.toString());
                    sendJson(emitter, "done", mapOf("is_end", true, "conversationId", request.getConversationId(),
                            "messageId", assistantMessage.getId()));
                    emitter.complete();
                } catch (Exception e) {
                    log.error("Chat stream failed, conversation={}, userMessage={}", request.getConversationId(), userMessage.getId(), e);
                    messageService.completeAssistantMessage(assistantMessage.getId(), assistantContent.toString());
                    try {
                        sendJson(emitter, "error", mapOf("error", e.getMessage()));
                        emitter.completeWithError(e);
                    } catch (Exception ignored) {
                    }
                }
            }
        });
        return emitter;
    }

    private List<Map<String, String>> buildUpstreamMessages(ChatRequest request, List<ChatFileRecord> files) {
        List<Map<String, String>> result = new ArrayList<Map<String, String>>();
        if (request.getMessages() != null) {
            for (ChatRequest.UpstreamMessage message : request.getMessages()) {
                if (message != null && message.getRole() != null && message.getContent() != null) {
                    result.add(mapOf("role", message.getRole(), "content", message.getContent()));
                }
            }
        }
        String content = buildUserContentWithFiles(request.getContent(), files);
        if (result.isEmpty() || !content.equals(result.get(result.size() - 1).get("content"))) {
            if (!result.isEmpty() && request.getContent().equals(result.get(result.size() - 1).get("content"))) {
                result.remove(result.size() - 1);
            }
            result.add(mapOf("role", "user", "content", content));
        }
        return result;
    }

    private String buildUserContentWithFiles(String content, List<ChatFileRecord> files) {
        String filePrompt = chatFileService.buildFilePrompt(files);
        if (filePrompt.isEmpty()) {
            return content;
        }
        return "请结合以下文件内容回答用户问题。\n\n" + filePrompt
                + "用户问题：\n" + (content == null ? "" : content);
    }

    private void handleUpstreamLine(String line, SseEmitter emitter, StringBuilder assistantContent) {
        if (line == null) {
            return;
        }
        String[] parts = line.split("\\r?\\n");
        for (String raw : parts) {
            String text = raw.trim();
            if (text.startsWith("data:")) {
                text = text.substring(5).trim();
            }
            if (text.isEmpty() || "[DONE]".equals(text)) {
                continue;
            }
            try {
                JsonNode node = objectMapper.readTree(text);
                if (node.hasNonNull("error")) {
                    sendJson(emitter, "error", mapOf("error", node.get("error").asText()));
                    continue;
                }
                String content = node.path("content").asText("");
                if (!content.isEmpty()) {
                    assistantContent.append(content);
                    sendJson(emitter, "message", mapOf("content", content, "is_end", false));
                }
            } catch (Exception e) {
                log.warn("Ignore malformed SSE line: {}", text);
            }
        }
    }

    private void sendJson(SseEmitter emitter, String event, Map<String, Object> value) throws java.io.IOException {
        emitter.send(SseEmitter.event().name(event).data(objectMapper.writeValueAsString(value)));
    }

    private String trimRight(String value) {
        if (value == null) {
            return "";
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private Map<String, String> mapOf(String k1, String v1, String k2, String v2) {
        Map<String, String> map = new HashMap<String, String>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    private Map<String, Object> mapOf(String k1, Object v1) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(k1, v1);
        return map;
    }

    private Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    private Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        return map;
    }
}
