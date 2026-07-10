package com.cutegoals.common.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin recovery request DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryRequest {
    private String recoveryToken;
    private String newPassword;
}
