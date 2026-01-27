package com.chillchat.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chillchat.entity.ChatGroup;
import com.chillchat.entity.GroupMember;
import com.chillchat.mapper.ChatGroupMapper;
import com.chillchat.mapper.GroupMemberMapper;
import com.chillchat.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    @Autowired
    private ChatGroupMapper chatGroupMapper;

    @Autowired
    private GroupMemberMapper groupMemberMapper;
    
    @Autowired
    private UserMapper userMapper;

    // Create Group
    @PostMapping("/create")
    @Transactional
    public String createGroup(@RequestParam Long ownerId, 
                              @RequestParam String groupName, 
                              @RequestBody List<Long> memberIds) {
        // 1. Create Group
        ChatGroup group = new ChatGroup();
        group.setName(groupName);
        group.setOwnerId(ownerId);
        group.setCreateTime(new Date());
        group.setAvatar("https://api.dicebear.com/7.x/initials/svg?seed=" + groupName);
        chatGroupMapper.insert(group);

        // 2. Add Owner
        GroupMember owner = new GroupMember();
        owner.setGroupId(group.getId());
        owner.setUserId(ownerId);
        owner.setJoinTime(new Date());
        groupMemberMapper.insert(owner);

        // 3. Add Members
        for (Long memberId : memberIds) {
            GroupMember gm = new GroupMember();
            gm.setGroupId(group.getId());
            gm.setUserId(memberId);
            gm.setJoinTime(new Date());
            groupMemberMapper.insert(gm);
        }

        return "Group created successfully";
    }

    // Get My Groups
    @GetMapping("/my")
    public List<ChatGroup> getMyGroups(@RequestParam Long userId) {
        // Find all group IDs where user is a member
        QueryWrapper<GroupMember> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);
        List<GroupMember> memberships = groupMemberMapper.selectList(qw);
        
        if (memberships.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> groupIds = memberships.stream()
            .map(GroupMember::getGroupId)
            .collect(Collectors.toList());

        return chatGroupMapper.selectBatchIds(groupIds);
    }
}
