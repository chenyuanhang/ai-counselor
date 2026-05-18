package com.hfut.counselor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class AuthDtos {
    private AuthDtos() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginUrlResponse {
        private String url;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginResponse {
        private String token;
        private UserDto user;
    }

    @Data
    public static class DevLoginRequest {
        private String username;
        private String displayName;
    }
}
