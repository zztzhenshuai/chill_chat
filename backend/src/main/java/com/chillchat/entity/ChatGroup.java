package com.chillchat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("chat_groups")
public class ChatGroup {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String name;
    private Long ownerId;
    private String avatar;
    private Date createTime;
}
