package com.hfut.counselor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hfut.counselor.dto.MessageDto;
import com.hfut.counselor.entity.Message;
import com.hfut.counselor.mapper.MessageMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MessageService extends ServiceImpl<MessageMapper, Message> {
    public Message saveMessage(String conversationId, String role, String content, boolean streaming, boolean streamingEnd) {
        return saveMessage(conversationId, role, content, null, streaming, streamingEnd);
    }

    public Message saveMessage(String conversationId, String role, String content, String attachments, boolean streaming, boolean streamingEnd) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        message.setAttachments(attachments);
        message.setStreaming(streaming);
        message.setStreamingEnd(streamingEnd);
        message.setCreateTime(LocalDateTime.now());
        save(message);
        return message;
    }

    public void completeAssistantMessage(String messageId, String content) {
        Message message = getById(messageId);
        if (message != null) {
            message.setContent(content == null ? "" : content);
            message.setStreaming(false);
            message.setStreamingEnd(true);
            updateById(message);
        }
    }

    public List<MessageDto> listByConversation(String conversationId) {
        return list(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                .orderByAsc(Message::getCreateTime))
                .stream().map(MessageDto::from).collect(Collectors.toList());
    }

    public List<Message> listEntitiesByConversation(String conversationId) {
        return list(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                .orderByAsc(Message::getCreateTime));
    }
}
