package com.chillchat.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chillchat.entity.GroupMember;
import com.chillchat.entity.Message;
import com.chillchat.mapper.FriendMapper;
import com.chillchat.mapper.GroupMemberMapper;
import com.chillchat.mapper.MessageMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private FriendMapper friendMapper;

    @Autowired
    private GroupMemberMapper groupMemberMapper;

    @GetMapping("/history")
    public ResponseEntity<?> getHistory(
            @RequestParam Long targetId,
            @RequestParam Boolean isGroup,
            @RequestParam(defaultValue = "-1") Long beforeId,
            @RequestParam(defaultValue = "30") int pageSize,
            HttpServletRequest request) {

        Long currentUserId = (Long) request.getAttribute("currentUserId");

        // S-5 修复：校验当前用户是否有权限查看该会话记录
        if (Boolean.TRUE.equals(isGroup)) {
            QueryWrapper<GroupMember> qw = new QueryWrapper<>();
            qw.eq("group_id", targetId).eq("user_id", currentUserId);
            if (groupMemberMapper.selectCount(qw) == 0) {
                return ResponseEntity.status(403).body("Not a member of this group");
            }
        } else {
            if (friendMapper.countFriendship(currentUserId, targetId) == 0) {
                return ResponseEntity.status(403).body("Not authorized to view this conversation");
            }
        }

        List<Message> messages = messageMapper.selectPagedHistory(currentUserId, targetId, isGroup, beforeId, pageSize);
        return ResponseEntity.ok(messages);
    }
}
