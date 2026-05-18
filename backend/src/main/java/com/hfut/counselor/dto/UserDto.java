package com.hfut.counselor.dto;

import com.hfut.counselor.entity.User;
import lombok.Data;

@Data
public class UserDto {
    private String id;
    private String username;
    private String displayName;
    private String email;
    private String phone;
    private String avatar;
    private String userType;

    public static UserDto from(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setDisplayName(user.getDisplayName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setAvatar(user.getAvatar());
        dto.setUserType(user.getUserType());
        return dto;
    }
}
