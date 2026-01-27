package com.chillchat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("friend_requests")
public class FriendRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requesterId;
    private Long receiverId;
    private String status; // PENDING, ACCEPTED, REJECTED
    private String reason;
    private LocalDateTime createTime;

    @TableField(exist = false)
    private String requesterName;
    @TableField(exist = false)
    private String requesterAvatar;
}
