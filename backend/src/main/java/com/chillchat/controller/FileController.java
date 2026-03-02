package com.chillchat.controller;

import com.chillchat.service.OssService;
import com.chillchat.service.RedisRateLimiterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/upload")
public class FileController {

    @Autowired
    private OssService ossService;

    @Autowired
    private RedisRateLimiterService rateLimiterService;

    @PostMapping
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        String clientIp = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        boolean allowed = rateLimiterService.allow("rl:upload:ip:" + clientIp, 30, 60);
        if (!allowed) {
            return ResponseEntity.status(429).body(null);
        }

        try {
            String url = ossService.uploadFile(file);
            Map<String, String> response = new HashMap<>();
            response.put("url", url);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(null);
        }
    }
}
