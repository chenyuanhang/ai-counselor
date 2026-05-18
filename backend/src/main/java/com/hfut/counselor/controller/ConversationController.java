package com.hfut.counselor.controller;

import com.hfut.counselor.dto.ApiResponse;
import com.hfut.counselor.dto.ConversationDto;
import com.hfut.counselor.entity.User;
import com.hfut.counselor.security.CurrentUser;
import com.hfut.counselor.service.ConversationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {
    private final ConversationService conversationService;

    @PostMapping
    public ApiResponse<ConversationDto> create(@CurrentUser User user, @RequestBody(required = false) ConversationRequest request) {
        String title = request == null ? null : request.getTitle();
        return ApiResponse.success(conversationService.create(user.getId(), title));
    }

    @GetMapping
    public ApiResponse<List<ConversationDto>> list(@CurrentUser User user) {
        return ApiResponse.success(conversationService.list(user.getId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<ConversationDto> get(@CurrentUser User user, @PathVariable String id) {
        return ApiResponse.success(conversationService.get(id, user.getId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<ConversationDto> rename(@CurrentUser User user, @PathVariable String id,
                                               @RequestBody ConversationRequest request) {
        return ApiResponse.success(conversationService.rename(id, user.getId(), request == null ? null : request.getTitle()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@CurrentUser User user, @PathVariable String id) {
        conversationService.archive(id, user.getId());
        return ApiResponse.success("deleted", null);
    }

    @Data
    public static class ConversationRequest {
        private String title;
    }
}
