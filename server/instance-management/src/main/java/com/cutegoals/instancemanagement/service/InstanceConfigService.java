package com.cutegoals.instancemanagement.service;

import com.cutegoals.auth.service.AuditEvent;
import com.cutegoals.auth.service.AuditService;
import com.cutegoals.common.entity.instance.InstanceConfig;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.common.util.MaskUtil;
import com.cutegoals.instancemanagement.mapper.InstanceConfigMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service for instance configuration management (Task 6.2).
 * Whitelist-based config with secret field masking.
 */
@Service
@RequiredArgsConstructor
public class InstanceConfigService {

    private static final Logger log = LoggerFactory.getLogger(InstanceConfigService.class);

    private final InstanceConfigMapper configMapper;
    private final AuditService auditService;

    /**
     * Whitelist of allowed configuration keys with metadata.
     */
    private static final Map<String, ConfigMeta> CONFIG_WHITELIST = new LinkedHashMap<>();

    static {
        // SMS provider config
        CONFIG_WHITELIST.put("sms.provider", new ConfigMeta("string", false,
                "SMS service provider (e.g., aliyun, tencent)"));
        CONFIG_WHITELIST.put("sms.api_key", new ConfigMeta("string", true,
                "SMS API key (secret)"));
        CONFIG_WHITELIST.put("sms.api_secret", new ConfigMeta("string", true,
                "SMS API secret (secret)"));
        CONFIG_WHITELIST.put("sms.sign_name", new ConfigMeta("string", false,
                "SMS signature name"));
        CONFIG_WHITELIST.put("sms.enabled", new ConfigMeta("boolean", false,
                "Whether SMS login is enabled"));

        // Recovery config
        CONFIG_WHITELIST.put("recovery.email", new ConfigMeta("string", false,
                "Recovery notification email"));

        // Backup config
        CONFIG_WHITELIST.put("backup.schedule", new ConfigMeta("string", false,
                "Backup cron schedule"));
        CONFIG_WHITELIST.put("backup.retention_days", new ConfigMeta("integer", false,
                "Backup retention in days"));

        // Rate limiting config
        CONFIG_WHITELIST.put("rate_limit.login_max", new ConfigMeta("integer", false,
                "Max login attempts per minute"));
        CONFIG_WHITELIST.put("rate_limit.pin_max", new ConfigMeta("integer", false,
                "Max PIN attempts before lockout"));
    }

    /**
     * Get all config entries with secret fields masked.
     */
    public List<Map<String, Object>> getAllConfig() {
        List<InstanceConfig> configs = configMapper.selectList(null);
        Map<String, InstanceConfig> configMap = new HashMap<>();
        for (InstanceConfig c : configs) {
            configMap.put(c.getConfigKey(), c);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, ConfigMeta> entry : CONFIG_WHITELIST.entrySet()) {
            String key = entry.getKey();
            ConfigMeta meta = entry.getValue();
            InstanceConfig stored = configMap.get(key);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", key);
            item.put("type", meta.type);
            item.put("description", meta.description);
            item.put("masked", meta.secret);

            if (stored != null) {
                if (meta.secret) {
                    item.put("value", MaskUtil.MASKED);
                    item.put("configured", true);
                } else {
                    item.put("value", stored.getConfigValue());
                    item.put("configured", true);
                }
            } else {
                item.put("value", null);
                item.put("configured", false);
            }
            result.add(item);
        }
        return result;
    }

    /**
     * Update config entries. All entries are validated against the whitelist.
     */
    @Transactional
    public void updateConfig(Map<String, Object> updates, Long adminAccountId) {
        Set<String> changedKeys = new LinkedHashSet<>();

        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Validate key is in whitelist
            ConfigMeta meta = CONFIG_WHITELIST.get(key);
            if (meta == null) {
                throw new BusinessException(ErrorCode.CONFIGURATION_INVALID,
                        "Unknown configuration key: " + key);
            }

            // Validate type
            if (value != null) {
                String strVal = value.toString();
                switch (meta.type) {
                    case "boolean" -> {
                        if (!"true".equalsIgnoreCase(strVal) && !"false".equalsIgnoreCase(strVal)) {
                            throw new BusinessException(ErrorCode.CONFIGURATION_INVALID,
                                    "Invalid boolean value for key: " + key);
                        }
                    }
                    case "integer" -> {
                        try {
                            Integer.parseInt(strVal);
                        } catch (NumberFormatException e) {
                            throw new BusinessException(ErrorCode.CONFIGURATION_INVALID,
                                    "Invalid integer value for key: " + key);
                        }
                    }
                }
            }

            // Save or update
            InstanceConfig config = configMapper.findByKey(key).orElse(null);
            if (config == null) {
                config = new InstanceConfig();
                config.setConfigKey(key);
                config.setConfigValue(value != null ? value.toString() : "");
                config.setMasked(meta.secret);
                configMapper.insert(config);
            } else {
                configMapper.updateByKey(key, value != null ? value.toString() : "", meta.secret);
            }
            changedKeys.add(key);
        }

        // Check SMS config completeness
        checkSmsConfig();

        auditService.record(AuditEvent.CONFIG_CHANGED, adminAccountId, "SUCCESS",
                "Configuration updated: keys=" + changedKeys);
        log.info("Configuration updated by accountId={}: keys={}", adminAccountId, changedKeys);
    }

    /**
     * Check if SMS config is complete. If not, SMS login remains disabled.
     */
    private void checkSmsConfig() {
        InstanceConfig provider = configMapper.findByKey("sms.provider").orElse(null);
        InstanceConfig apiKey = configMapper.findByKey("sms.api_key").orElse(null);
        InstanceConfig apiSecret = configMapper.findByKey("sms.api_secret").orElse(null);
        InstanceConfig enabled = configMapper.findByKey("sms.enabled").orElse(null);

        boolean smsComplete = provider != null && apiKey != null && apiSecret != null
                && enabled != null && "true".equalsIgnoreCase(enabled.getConfigValue());

        if (!smsComplete) {
            log.info("SMS login configuration is incomplete - SMS login remains disabled");
        }
    }

    /**
     * Configuration key metadata.
     */
    private record ConfigMeta(String type, boolean secret, String description) {}
}
