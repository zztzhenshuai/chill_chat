package com.chillchat.controller;

import com.chillchat.entity.Friend;
import com.chillchat.entity.FriendRequest;
import com.chillchat.service.FriendService;
import com.chillchat.service.RedisRateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    @Autowired
    private FriendService friendService;

    @Autowired
    private RedisRateLimiterService rateLimiterService;

    @GetMapping
    public List<Friend> getFriends(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return friendService.getFriends(userId);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteFriend(@RequestParam Long friendId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return friendService.deleteFriend(userId, friendId);
    }

    @PostMapping("/request")
    public ResponseEntity<?> sendRequest(@RequestParam Long friendId,
                                         @RequestParam(required = false) String reason,
                                         HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        boolean allowed = rateLimiterService.allow("rl:friend:request:user:" + userId, 20, 60);
        if (!allowed) {
            return ResponseEntity.status(429).body("Too many friend requests, try later");
        }
        return friendService.sendRequest(userId, friendId, reason);
    }

    @GetMapping("/requests")
    public List<FriendRequest> getRequests(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return friendService.getPendingRequests(userId);
    }

    @PostMapping("/respond")
    public ResponseEntity<?> respondRequest(@RequestParam Long requestId,
                                            @RequestParam String status,
                                            HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("currentUserId");
        return friendService.respondToRequest(requestId, status, currentUserId);
    }
}
