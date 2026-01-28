package com.chillchat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chillchat.entity.Friend;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FriendMapper extends BaseMapper<Friend> {
    @Select("SELECT f.*, u.username as friend_name, u.avatar as friend_avatar, u.signature as friend_signature, u.gender as friend_gender, u.birthday as friend_birthday, u.location as friend_location FROM friends f LEFT JOIN users u ON f.friend_id = u.id WHERE f.user_id = #{userId}")
    List<Friend> selectFriendsWithInfo(Long userId);
}
