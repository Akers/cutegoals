package com.cutegoals.auth.service.impl;

import com.cutegoals.auth.service.SmsAuthProvider;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default NoOp SMS provider. Active when no real SMS provider is configured.
 * Always returns {@code isEnabled() == false}.
 */
@Component
@ConditionalOnMissingBean(SmsAuthProvider.class)
@ConditionalOnProperty(name = "app.auth.sms.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpSmsAuthProvider implements SmsAuthProvider {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void sendVerificationCode(String phone) {
        throw new BusinessException(ErrorCode.SMS_LOGIN_NOT_CONFIGURED);
    }

    @Override
    public boolean verifyCode(String phone, String code) {
        throw new BusinessException(ErrorCode.SMS_LOGIN_NOT_CONFIGURED);
    }
}
