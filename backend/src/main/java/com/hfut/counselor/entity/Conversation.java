package com.hfut.counselor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("counselor_conversation")
public class Conversation {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String userId;
    private String title;
    private Integer messageCount;
    private Boolean archived;
    private LocalDateTime lastMessageTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
