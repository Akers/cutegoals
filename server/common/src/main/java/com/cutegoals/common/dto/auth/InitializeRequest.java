package com.cutegoals.common.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Instance initialization request DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitializeRequest {
    private String token;
    private String phone;
    private String password;
}
