package com.chillchat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chillchat.entity.FriendRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FriendRequestMapper extends BaseMapper<FriendRequest> {
    @Select("SELECT r.*, u.username as requester_name, u.avatar as requester_avatar " +
            "FROM friend_requests r " +
            "LEFT JOIN users u ON r.requester_id = u.id " +
            "WHERE r.receiver_id = #{userId} AND r.status = 'PENDING' " +
            "ORDER BY r.create_time DESC")
    List<FriendRequest> selectPendingRequests(Long userId);
}
