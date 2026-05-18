package com.hfut.counselor.controller;

import com.hfut.counselor.dto.ApiResponse;
import com.hfut.counselor.dto.UserDto;
import com.hfut.counselor.entity.User;
import com.hfut.counselor.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {
    @GetMapping("/me")
    public ApiResponse<UserDto> me(@CurrentUser User user) {
        return ApiResponse.success(UserDto.from(user));
    }
}
