package com.atlasmind.ai_travel_recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class LoginUserDto {
    private String loginInfo; // Can be username or email
    private String password;
}
