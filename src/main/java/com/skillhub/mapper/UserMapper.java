package com.skillhub.mapper;

import com.skillhub.dto.UserDTO;
import com.skillhub.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserDTO toDTO(User user) {
        if (user == null)
            return null;
        return new UserDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getHeadline(),
                user.getBio(),
                user.getProfileImageUrl());
    }
}
