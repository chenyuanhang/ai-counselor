package com.hfut.counselor.controller;

import com.hfut.counselor.dto.ApiResponse;
import com.hfut.counselor.dto.MessageDto;
import com.hfut.counselor.entity.User;
import com.hfut.counselor.security.CurrentUser;
import com.hfut.counselor.service.ConversationService;
import com.hfut.counselor.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {
    private final ConversationService conversationService;
    private final MessageService messageService;

    @GetMapping
    public ApiResponse<List<MessageDto>> list(@CurrentUser User user, @RequestParam String conversationId) {
        conversationService.requireOwned(conversationId, user.getId());
        return ApiResponse.success(messageService.listByConversation(conversationId));
    }
}
