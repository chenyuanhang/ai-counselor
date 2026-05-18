package com.hfut.counselor.controller;

import com.hfut.counselor.dto.ApiResponse;
import com.hfut.counselor.dto.AuthDtos;
import com.hfut.counselor.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @GetMapping("/login")
    public void login(@RequestParam(required = false) String callback, HttpServletResponse response) throws IOException {
        response.sendRedirect(authService.buildLoginUrl(callback));
    }

    @GetMapping("/callback")
    public void callback(@RequestParam(required = false) String ticket,
                         @RequestParam(required = false) String code,
                         @RequestParam(required = false) String state,
                         @RequestParam(required = false) String error,
                         HttpServletResponse response) throws IOException {
        AuthService.CallbackResult result;
        if (error != null) {
            result = AuthService.CallbackResult.failure("/counselor/", error);
        } else if (ticket != null) {
            result = authService.handleCasCallback(ticket, state);
        } else {
            result = authService.handleOidcCallback(code, state);
        }
        response.sendRedirect(authService.buildRedirectUrl(result));
    }

    @PostMapping("/dev-login")
    public ApiResponse<AuthDtos.LoginResponse> devLogin(@RequestBody(required = false) AuthDtos.DevLoginRequest request) {
        String username = request == null ? null : request.getUsername();
        String displayName = request == null ? null : request.getDisplayName();
        return ApiResponse.success(authService.devLogin(username, displayName));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.success("logged out", null);
    }

    @GetMapping("/logout")
    public void logoutRedirect(@RequestParam(required = false) String callback,
                               @RequestParam(required = false) String logout,
                               HttpServletResponse response) throws IOException {
        if (logout == null) {
            response.sendRedirect(authService.buildCasLogoutUrl(callback));
            return;
        }
        response.sendRedirect(authService.buildLogoutRedirectUrl(callback));
    }

    @GetMapping(value = "/slo", produces = "application/javascript;charset=UTF-8")
    public String singleLogout(@RequestParam(required = false) String callback) {
        return authService.buildSloResponse(callback);
    }
}
