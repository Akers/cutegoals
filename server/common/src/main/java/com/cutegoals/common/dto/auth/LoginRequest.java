package com.cutegoals.common.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login request DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    private String phone;
    private String password;
}
