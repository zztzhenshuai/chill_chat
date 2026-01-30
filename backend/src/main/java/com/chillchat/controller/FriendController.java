package com.chillchat.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chillchat.entity.Friend;
import com.chillchat.entity.User;
import com.chillchat.mapper.FriendMapper;
import com.chillchat.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.chillchat.mapper.FriendRequestMapper;
import com.chillchat.entity.FriendRequest;
import com.chillchat.netty.handler.TextWebSocketFrameHandler;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    @Autowired
    private FriendMapper friendMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FriendRequestMapper friendRequestMapper;

    @GetMapping
    public List<Friend> getFriends(@RequestParam Long userId) {
        List<Friend> friends = friendMapper.selectFriendsWithInfo(userId);
        for (Friend f : friends) {
             if ("ChillBot".equals(f.getFriendName())) {
                f.setIsOnline(true);
            } else {
                boolean isOnline = TextWebSocketFrameHandler.userChannelMap.containsKey(f.getFriendId());
                f.setIsOnline(isOnline);
            }
        }
        return friends;
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteFriend(@RequestParam Long userId, @RequestParam Long friendId) {
        QueryWrapper<Friend> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("friend_id", friendId)
             .or()
             .eq("user_id", friendId).eq("friend_id", userId);
        
        friendMapper.delete(query);
        return ResponseEntity.ok("Friend deleted");
    }

    @PostMapping("/request")
    public ResponseEntity<?> sendRequest(@RequestParam Long userId, 
                                       @RequestParam Long friendId, 
                                       @RequestParam(required = false) String reason) {
        if (userId.equals(friendId)) return ResponseEntity.badRequest().body("Cannot add yourself");

        // Check if already friends
        QueryWrapper<Friend> qFriend = new QueryWrapper<>();
        qFriend.eq("user_id", userId).eq("friend_id", friendId);
        if (friendMapper.selectCount(qFriend) > 0) return ResponseEntity.badRequest().body("Already friends");

        // Check if pending request exists
        QueryWrapper<FriendRequest> qReq = new QueryWrapper<>();
        qReq.eq("requester_id", userId).eq("receiver_id", friendId).eq("status", "PENDING");
        if (friendRequestMapper.selectCount(qReq) > 0) return ResponseEntity.badRequest().body("Request already sent");

        FriendRequest req = new FriendRequest();
        req.setRequesterId(userId);
        req.setReceiverId(friendId);
        req.setStatus("PENDING");
        req.setReason(reason);
        req.setCreateTime(LocalDateTime.now());
        friendRequestMapper.insert(req);

        return ResponseEntity.ok("Request sent");
    }

    @GetMapping("/requests")
    public List<FriendRequest> getRequests(@RequestParam Long userId) {
        return friendRequestMapper.selectPendingRequests(userId);
    }

    @PostMapping("/respond")
    public ResponseEntity<?> respondRequest(@RequestParam Long requestId, @RequestParam String status) {
        FriendRequest req = friendRequestMapper.selectById(requestId);
        if (req == null) return ResponseEntity.badRequest().body("Request not found");
        
        if (!"PENDING".equals(req.getStatus())) return ResponseEntity.badRequest().body("Request already handled");

        if ("ACCEPTED".equals(status)) {
            // Add Friend Relationship
            Friend f1 = new Friend(); f1.setUserId(req.getRequesterId()); f1.setFriendId(req.getReceiverId());
            friendMapper.insert(f1);

            Friend f2 = new Friend(); f2.setUserId(req.getReceiverId()); f2.setFriendId(req.getRequesterId());
            friendMapper.insert(f2);
        }

        req.setStatus(status);
        friendRequestMapper.updateById(req);
        
        return ResponseEntity.ok("Processed");
    }
}
