package com.hfut.counselor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hfut.counselor.dto.ConversationDto;
import com.hfut.counselor.entity.Conversation;
import com.hfut.counselor.exception.NotFoundException;
import com.hfut.counselor.mapper.ConversationMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ConversationService extends ServiceImpl<ConversationMapper, Conversation> {
    public ConversationDto create(String userId, String title) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(StringUtils.hasText(title) ? title : "新会话");
        conversation.setMessageCount(0);
        conversation.setArchived(false);
        conversation.setLastMessageTime(LocalDateTime.now());
        conversation.setCreateTime(LocalDateTime.now());
        conversation.setUpdateTime(LocalDateTime.now());
        save(conversation);
        return ConversationDto.from(conversation);
    }

    public List<ConversationDto> list(String userId) {
        return list(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUserId, userId)
                .eq(Conversation::getArchived, false)
                .orderByDesc(Conversation::getLastMessageTime))
                .stream().map(ConversationDto::from).collect(Collectors.toList());
    }

    public Conversation requireOwned(String id, String userId) {
        Conversation conversation = getOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getId, id)
                .eq(Conversation::getUserId, userId)
                .eq(Conversation::getArchived, false), false);
        if (conversation == null) {
            throw new NotFoundException("Conversation not found");
        }
        return conversation;
    }

    public ConversationDto get(String id, String userId) {
        return ConversationDto.from(requireOwned(id, userId));
    }

    public ConversationDto rename(String id, String userId, String title) {
        Conversation conversation = requireOwned(id, userId);
        conversation.setTitle(StringUtils.hasText(title) ? title : conversation.getTitle());
        conversation.setUpdateTime(LocalDateTime.now());
        updateById(conversation);
        return ConversationDto.from(conversation);
    }

    public void archive(String id, String userId) {
        Conversation conversation = requireOwned(id, userId);
        conversation.setArchived(true);
        conversation.setUpdateTime(LocalDateTime.now());
        updateById(conversation);
    }

    public void touch(String id, int deltaCount) {
        Conversation conversation = getById(id);
        if (conversation != null) {
            conversation.setMessageCount((conversation.getMessageCount() == null ? 0 : conversation.getMessageCount()) + deltaCount);
            conversation.setLastMessageTime(LocalDateTime.now());
            conversation.setUpdateTime(LocalDateTime.now());
            updateById(conversation);
        }
    }
}
