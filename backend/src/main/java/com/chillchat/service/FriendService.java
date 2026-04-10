package com.chillchat.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chillchat.entity.Friend;
import com.chillchat.entity.FriendRequest;
import com.chillchat.mapper.FriendMapper;
import com.chillchat.mapper.FriendRequestMapper;
import com.chillchat.netty.handler.TextWebSocketFrameHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FriendService {

    @Autowired
    private FriendMapper friendMapper;

    @Autowired
    private FriendRequestMapper friendRequestMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public List<Friend> getFriends(Long userId) {
        List<Friend> friends = friendMapper.selectFriendsWithInfo(userId);
        for (Friend f : friends) {
            if ("ChillBot".equals(f.getFriendName())) {
                f.setIsOnline(true);
            } else {
                boolean isOnline;
                try {
                    Boolean inRedis = stringRedisTemplate.hasKey("online:user:" + f.getFriendId());
                    isOnline = Boolean.TRUE.equals(inRedis);
                } catch (Exception ex) {
                    isOnline = TextWebSocketFrameHandler.userChannelMap.containsKey(f.getFriendId());
                }
                f.setIsOnline(isOnline);
            }
        }
        return friends;
    }

    public ResponseEntity<?> deleteFriend(Long userId, Long friendId) {
        QueryWrapper<Friend> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("friend_id", friendId)
             .or()
             .eq("user_id", friendId).eq("friend_id", userId);
        friendMapper.delete(query);
        return ResponseEntity.ok("Friend deleted");
    }

    public ResponseEntity<?> sendRequest(Long userId, Long friendId, String reason) {
        if (userId.equals(friendId)) return ResponseEntity.badRequest().body("Cannot add yourself");

        QueryWrapper<Friend> qFriend = new QueryWrapper<>();
        qFriend.eq("user_id", userId).eq("friend_id", friendId);
        if (friendMapper.selectCount(qFriend) > 0) return ResponseEntity.badRequest().body("Already friends");

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

    public List<FriendRequest> getPendingRequests(Long userId) {
        return friendRequestMapper.selectPendingRequests(userId);
    }

    /**
     * S-3 修复：验证 currentUserId 必须是该好友申请的接收方，防止 IDOR 攻击。
     */
    public ResponseEntity<?> respondToRequest(Long requestId, String status, Long currentUserId) {
        FriendRequest req = friendRequestMapper.selectById(requestId);
        if (req == null) return ResponseEntity.badRequest().body("Request not found");

        // 仅允许申请的接收者处理该请求
        if (!req.getReceiverId().equals(currentUserId)) {
            return ResponseEntity.status(403).body("Not authorized to respond to this request");
        }

        if (!"PENDING".equals(req.getStatus())) return ResponseEntity.badRequest().body("Request already handled");

        if ("ACCEPTED".equals(status)) {
            Friend f1 = new Friend();
            f1.setUserId(req.getRequesterId());
            f1.setFriendId(req.getReceiverId());
            friendMapper.insert(f1);

            Friend f2 = new Friend();
            f2.setUserId(req.getReceiverId());
            f2.setFriendId(req.getRequesterId());
            friendMapper.insert(f2);
        }

        req.setStatus(status);
        friendRequestMapper.updateById(req);
        return ResponseEntity.ok("Processed");
    }
}
