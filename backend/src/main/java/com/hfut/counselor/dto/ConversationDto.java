package com.hfut.counselor.dto;

import com.hfut.counselor.entity.Conversation;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationDto {
    private String id;
    private String title;
    private Integer messageCount;
    private LocalDateTime lastMessageTime;
    private LocalDateTime createTime;

    public static ConversationDto from(Conversation conversation) {
        ConversationDto dto = new ConversationDto();
        dto.setId(conversation.getId());
        dto.setTitle(conversation.getTitle());
        dto.setMessageCount(conversation.getMessageCount());
        dto.setLastMessageTime(conversation.getLastMessageTime());
        dto.setCreateTime(conversation.getCreateTime());
        return dto;
    }
}
