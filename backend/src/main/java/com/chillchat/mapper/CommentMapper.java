package com.chillchat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chillchat.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
    @Select("SELECT c.*, u.username, u.avatar FROM comments c LEFT JOIN users u ON c.user_id = u.id WHERE c.post_id = #{postId} ORDER BY c.create_time ASC")
    List<Comment> selectCommentsByPostId(Long postId);
}
