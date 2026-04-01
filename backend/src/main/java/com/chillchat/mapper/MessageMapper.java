package com.chillchat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chillchat.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    List<Message> selectPagedHistory(
            @Param("currentUserId") Long currentUserId,
            @Param("targetId") Long targetId,
            @Param("isGroup") Boolean isGroup,
            @Param("beforeId") Long beforeId,
            @Param("pageSize") int pageSize
    );
}
