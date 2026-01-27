package com.chillchat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chillchat.entity.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PostMapper extends BaseMapper<Post> {
    @Select("SELECT p.*, u.username, u.avatar FROM posts p LEFT JOIN users u ON p.user_id = u.id ORDER BY p.create_time DESC LIMIT 50")
    List<Post> selectPostWithUser();
}
