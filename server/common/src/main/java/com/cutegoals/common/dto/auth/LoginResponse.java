package com.cutegoals.common.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Login response DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private Long accountId;
    private String phone;
    private List<String> roles;
    private Long familyId;
    private String accessToken;
    private String refreshToken;
    private int expiresIn;
}
