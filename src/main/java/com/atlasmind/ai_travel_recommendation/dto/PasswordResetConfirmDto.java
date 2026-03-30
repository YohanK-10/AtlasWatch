package com.atlasmind.ai_travel_recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetConfirmDto {
    private String email;
    private String resetCode;
    private String newPassword;
}
