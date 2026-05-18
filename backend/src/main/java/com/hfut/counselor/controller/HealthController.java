package com.hfut.counselor.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("status", "UP");
        result.put("service", "ai-counselor-backend");
        return result;
    }
}
