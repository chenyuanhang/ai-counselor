package com.hfut.counselor.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

@Data
public class ChatRequest {
    @NotBlank
    private String conversationId;

    @NotBlank
    private String content;

    private String agentId;
    private List<String> fileIds;
    private List<UpstreamMessage> messages;

    @Data
    public static class UpstreamMessage {
        private String role;
        private String content;
    }
}
