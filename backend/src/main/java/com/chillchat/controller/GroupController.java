package com.chillchat.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chillchat.entity.ChatGroup;
import com.chillchat.entity.GroupMember;
import com.chillchat.entity.User;
import com.chillchat.mapper.ChatGroupMapper;
import com.chillchat.mapper.GroupMemberMapper;
import com.chillchat.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private static final Logger log = LoggerFactory.getLogger(GroupController.class);

    @Autowired
    private ChatGroupMapper chatGroupMapper;

    @Autowired
    private GroupMemberMapper groupMemberMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private String groupMembersKey(Long groupId) {
        return "group:members:" + groupId;
    }

    private void invalidateGroupCache(Long groupId) {
        stringRedisTemplate.delete(groupMembersKey(groupId));
    }

    // ─── Create Group ─────────────────────────────────────────────────────────

    @PostMapping("/create")
    @Transactional
    public ResponseEntity<?> createGroup(@RequestParam String groupName,
                                         @RequestBody List<Long> memberIds,
                                         HttpServletRequest request) {
        Long ownerId = (Long) request.getAttribute("currentUserId");
        try {
            ChatGroup group = new ChatGroup();
            group.setName(groupName);
            group.setOwnerId(ownerId);
            group.setCreateTime(new Date());
            group.setAvatar("https://api.dicebear.com/7.x/initials/svg?seed=" + groupName);
            chatGroupMapper.insert(group);

            // Add owner
            GroupMember owner = new GroupMember();
            owner.setGroupId(group.getId());
            owner.setUserId(ownerId);
            owner.setRole("owner");
            owner.setJoinTime(new Date());
            groupMemberMapper.insert(owner);

            // Add members (skip duplicates)
            for (Long memberId : memberIds) {
                if (memberId.equals(ownerId)) continue;
                GroupMember gm = new GroupMember();
                gm.setGroupId(group.getId());
                gm.setUserId(memberId);
                gm.setRole("member");
                gm.setJoinTime(new Date());
                groupMemberMapper.insert(gm);
            }

            // Populate Redis cache
            try {
                List<String> allMemberIds = new ArrayList<>();
                allMemberIds.add(String.valueOf(ownerId));
                for (Long memberId : memberIds) {
                    if (!memberId.equals(ownerId)) allMemberIds.add(String.valueOf(memberId));
                }
                String key = groupMembersKey(group.getId());
                stringRedisTemplate.delete(key);
                stringRedisTemplate.opsForSet().add(key, allMemberIds.toArray(new String[0]));
                stringRedisTemplate.expire(key, 1, TimeUnit.HOURS);
            } catch (Exception ignored) {
            }

            return ResponseEntity.ok("Group created successfully");
        } catch (Exception e) {
            log.error("createGroup failed: groupName={}, memberIds={}, error={}", groupName, memberIds, e.getMessage(), e);
            return ResponseEntity.status(500).body("创建群组失败: " + e.getMessage());
        }
    }

    // ─── Get My Groups ────────────────────────────────────────────────────────

    @GetMapping("/my")
    public List<ChatGroup> getMyGroups(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");

        QueryWrapper<GroupMember> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);
        List<GroupMember> memberships = groupMemberMapper.selectList(qw);

        if (memberships.isEmpty()) return new ArrayList<>();

        List<Long> groupIds = memberships.stream()
                .map(GroupMember::getGroupId)
                .collect(Collectors.toList());

        return chatGroupMapper.selectBatchIds(groupIds);
    }

    // ─── Get Group Members ────────────────────────────────────────────────────

    @GetMapping("/{groupId}/members")
    public ResponseEntity<?> getMembers(@PathVariable Long groupId, HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("currentUserId");

        // Verify requester is a member
        if (!isMember(groupId, currentUserId)) {
            return ResponseEntity.status(403).body("Not a group member");
        }

        QueryWrapper<GroupMember> qw = new QueryWrapper<>();
        qw.eq("group_id", groupId);
        List<GroupMember> members = groupMemberMapper.selectList(qw);

        if (members.isEmpty()) return ResponseEntity.ok(new ArrayList<>());

        // R-4 修复：收集所有 userId，一次批量查询代替 N+1 逐条查询
        List<Long> userIds = members.stream()
                .map(GroupMember::getUserId)
                .collect(Collectors.toList());
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> result = new ArrayList<>();
        for (GroupMember gm : members) {
            User user = userMap.get(gm.getUserId());
            if (user == null) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("userId", user.getId());
            m.put("username", user.getUsername());
            m.put("avatar", user.getAvatar());
            m.put("role", gm.getRole());
            m.put("joinTime", gm.getJoinTime());
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }

    // ─── Quit Group ───────────────────────────────────────────────────────────

    @DeleteMapping("/{groupId}/quit")
    @Transactional
    public ResponseEntity<?> quitGroup(@PathVariable Long groupId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");

        GroupMember membership = getMembership(groupId, userId);
        if (membership == null) return ResponseEntity.badRequest().body("Not in this group");
        if ("owner".equals(membership.getRole())) {
            return ResponseEntity.badRequest().body("群主不能直接退出，请先转让群主或解散群聊");
        }

        groupMemberMapper.deleteById(membership.getId());
        invalidateGroupCache(groupId);
        return ResponseEntity.ok("已退出群聊");
    }

    // ─── Kick Member ──────────────────────────────────────────────────────────

    @DeleteMapping("/{groupId}/kick")
    @Transactional
    public ResponseEntity<?> kickMember(@PathVariable Long groupId,
                                        @RequestParam Long userId,
                                        HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("currentUserId");

        GroupMember myMembership = getMembership(groupId, currentUserId);
        if (myMembership == null) return ResponseEntity.status(403).body("不在该群内");

        String myRole = myMembership.getRole();
        if (!"owner".equals(myRole) && !"admin".equals(myRole)) {
            return ResponseEntity.status(403).body("只有群主或管理员才能移除成员");
        }
        if (userId.equals(currentUserId)) {
            return ResponseEntity.badRequest().body("不能踢出自己");
        }

        GroupMember target = getMembership(groupId, userId);
        if (target == null) return ResponseEntity.badRequest().body("该用户不在群内");

        // Admin cannot kick owner or other admins
        if ("admin".equals(myRole) && !"member".equals(target.getRole())) {
            return ResponseEntity.status(403).body("管理员只能移除普通成员");
        }

        groupMemberMapper.deleteById(target.getId());
        invalidateGroupCache(groupId);
        return ResponseEntity.ok("已移除成员");
    }

    // ─── Set Admin ────────────────────────────────────────────────────────────

    @PutMapping("/{groupId}/promote")
    @Transactional
    public ResponseEntity<?> promoteToAdmin(@PathVariable Long groupId,
                                            @RequestParam Long userId,
                                            HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("currentUserId");

        ChatGroup group = chatGroupMapper.selectById(groupId);
        if (group == null) return ResponseEntity.notFound().build();
        if (!group.getOwnerId().equals(currentUserId)) {
            return ResponseEntity.status(403).body("只有群主才能设置管理员");
        }
        if (userId.equals(currentUserId)) {
            return ResponseEntity.badRequest().body("群主本身无需设为管理员");
        }

        GroupMember target = getMembership(groupId, userId);
        if (target == null) return ResponseEntity.badRequest().body("该用户不在群内");
        if ("admin".equals(target.getRole())) {
            // Toggle: demote back to member
            target.setRole("member");
            groupMemberMapper.updateById(target);
            invalidateGroupCache(groupId);
            return ResponseEntity.ok("已取消管理员");
        }
        target.setRole("admin");
        groupMemberMapper.updateById(target);
        invalidateGroupCache(groupId);
        return ResponseEntity.ok("已设为管理员");
    }

    // ─── Invite Members ───────────────────────────────────────────────────────

    @PostMapping("/{groupId}/invite")
    @Transactional
    public ResponseEntity<?> inviteMembers(@PathVariable Long groupId,
                                           @RequestBody List<Long> userIds,
                                           HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("currentUserId");

        GroupMember myMembership = getMembership(groupId, currentUserId);
        if (myMembership == null) return ResponseEntity.status(403).body("不在该群内");
        if (!"owner".equals(myMembership.getRole()) && !"admin".equals(myMembership.getRole())) {
            return ResponseEntity.status(403).body("只有群主或管理员才能邀请成员");
        }

        int added = 0;
        for (Long uid : userIds) {
            if (getMembership(groupId, uid) != null) continue; // already in group
            GroupMember gm = new GroupMember();
            gm.setGroupId(groupId);
            gm.setUserId(uid);
            gm.setRole("member");
            gm.setJoinTime(new Date());
            groupMemberMapper.insert(gm);
            added++;
        }
        invalidateGroupCache(groupId);
        return ResponseEntity.ok("已邀请 " + added + " 位成员加入群聊");
    }

    // ─── Disband Group ────────────────────────────────────────────────────────

    @DeleteMapping("/{groupId}")
    @Transactional
    public ResponseEntity<?> disbandGroup(@PathVariable Long groupId, HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("currentUserId");

        ChatGroup group = chatGroupMapper.selectById(groupId);
        if (group == null) return ResponseEntity.notFound().build();
        if (!group.getOwnerId().equals(currentUserId)) {
            return ResponseEntity.status(403).body("只有群主才能解散群聊");
        }

        // Delete all members
        QueryWrapper<GroupMember> qw = new QueryWrapper<>();
        qw.eq("group_id", groupId);
        groupMemberMapper.delete(qw);

        // Delete group
        chatGroupMapper.deleteById(groupId);
        invalidateGroupCache(groupId);
        return ResponseEntity.ok("群聊已解散");
    }

    // ─── Update Group Info ────────────────────────────────────────────────────

    @PutMapping("/{groupId}")
    public ResponseEntity<?> updateGroup(@PathVariable Long groupId,
                                         @RequestParam(required = false) String groupName,
                                         @RequestParam(required = false) String avatar,
                                         HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("currentUserId");

        GroupMember myMembership = getMembership(groupId, currentUserId);
        if (myMembership == null || (!myMembership.getRole().equals("owner") && !myMembership.getRole().equals("admin"))) {
            return ResponseEntity.status(403).body("无权限修改群信息");
        }

        ChatGroup group = chatGroupMapper.selectById(groupId);
        if (group == null) return ResponseEntity.notFound().build();

        if (groupName != null && !groupName.isBlank()) group.setName(groupName);
        if (avatar != null && !avatar.isBlank()) group.setAvatar(avatar);
        chatGroupMapper.updateById(group);
        return ResponseEntity.ok(group);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private boolean isMember(Long groupId, Long userId) {
        return getMembership(groupId, userId) != null;
    }

    private GroupMember getMembership(Long groupId, Long userId) {
        QueryWrapper<GroupMember> qw = new QueryWrapper<>();
        qw.eq("group_id", groupId).eq("user_id", userId);
        return groupMemberMapper.selectOne(qw);
    }
}
