package com.hfut.counselor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("counselor_message")
public class Message {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String conversationId;
    private String role;
    private String content;
    private String attachments;
    private Boolean streaming;
    private Boolean streamingEnd;
    private LocalDateTime createTime;
}
