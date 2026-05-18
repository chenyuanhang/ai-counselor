package com.hfut.counselor.dto;

import com.hfut.counselor.entity.Message;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageDto {
    private String id;
    private String conversationId;
    private String role;
    private String content;
    private String attachments;
    private Boolean streaming;
    private Boolean streamingEnd;
    private LocalDateTime createTime;

    public static MessageDto from(Message message) {
        MessageDto dto = new MessageDto();
        dto.setId(message.getId());
        dto.setConversationId(message.getConversationId());
        dto.setRole(message.getRole());
        dto.setContent(message.getContent());
        dto.setAttachments(message.getAttachments());
        dto.setStreaming(message.getStreaming());
        dto.setStreamingEnd(message.getStreamingEnd());
        dto.setCreateTime(message.getCreateTime());
        return dto;
    }
}
