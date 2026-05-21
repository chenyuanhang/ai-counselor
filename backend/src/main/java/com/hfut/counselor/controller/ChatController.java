package com.hfut.counselor.controller;

import com.hfut.counselor.dto.ApiResponse;
import com.hfut.counselor.dto.ChatFileDto;
import com.hfut.counselor.dto.ChatRequest;
import com.hfut.counselor.entity.User;
import com.hfut.counselor.security.CurrentUser;
import com.hfut.counselor.service.ChatFileService;
import com.hfut.counselor.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.validation.Valid;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;
    private final ChatFileService chatFileService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@CurrentUser User user, @Valid @RequestBody ChatRequest request) {
        return chatService.stream(user.getId(), request);
    }

    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<ChatFileDto>> uploadFiles(@CurrentUser User user, @RequestParam("files") MultipartFile[] files) {
        return ApiResponse.success(chatFileService.uploadAndExtract(user.getId(), files));
    }
}
