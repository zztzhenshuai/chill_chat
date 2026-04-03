package com.chillchat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chillchat.entity.MessageEmbedding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MessageEmbeddingMapper extends BaseMapper<MessageEmbedding> {

    @Select("SELECT * FROM message_embeddings WHERE user_id = #{userId} ORDER BY create_time DESC LIMIT 200")
    List<MessageEmbedding> selectLatestByUser(@Param("userId") Long userId);
}
