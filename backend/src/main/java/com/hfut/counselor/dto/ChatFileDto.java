package com.hfut.counselor.dto;

import lombok.Data;

@Data
public class ChatFileDto {
    private String id;
    private String name;
    private String originalName;
    private String url;
    private String type;
    private Long size;
}
