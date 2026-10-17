package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.mapper.SysConfigMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.model.dto.SysConfigDTO;
import com.clacjz.medical.rag.model.entity.SysConfig;
import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.enums.AuditActionEnums;
import com.clacjz.medical.rag.model.enums.AuditLevelEnums;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final SysConfigMapper sysConfigMapper;
    private final UserMapper userMapper;
    private final AuditService auditService;
    private final ConfigRefreshService configRefreshService;

    @Transactional(readOnly = true)
    public List<SysConfigDTO> getConfigByCategory(String category) {
        log.info("Get config by category: {}", category);
        List<SysConfig> configs = sysConfigMapper.selectByCategory(category);
        return configs.stream()
                .map(SysConfigDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SysConfigDTO getConfigByKey(String configKey) {
        log.info("Get config by key: {}", configKey);
        SysConfig config = sysConfigMapper.selectByConfigKey(configKey);
        if (config == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "配置项不存在: " + configKey);
        }
        return SysConfigDTO.fromEntity(config);
    }

    @Transactional(readOnly = true)
    public List<SysConfigDTO> getAllConfigs() {
        log.info("Get all configs");
        List<SysConfig> configs = sysConfigMapper.selectAll();
        return configs.stream()
                .map(SysConfigDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Map<String, String>> getAllConfigsGrouped() {
        List<SysConfig> configs = sysConfigMapper.selectAll();
        Map<String, Map<String, String>> result = new HashMap<>();

        for (SysConfig config : configs) {
            result.computeIfAbsent(config.getCategory(), k -> new HashMap<>())
                    .put(config.getConfigKey(), config.getValue());
        }

        return result;
    }

    @Transactional
    public SysConfigDTO updateConfig(String configKey, String value, String operatorId) {
        log.info("Update config: key={}, value={}, operator={}", configKey, value, operatorId);

        SysConfig config = sysConfigMapper.selectByConfigKey(configKey);
        if (config == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "配置项不存在: " + configKey);
        }

        User operator = userMapper.selectById(operatorId);
        String oldValue = config.getValue();
        config.setValue(value);
        config.setUpdatedAt(LocalDateTime.now());
        config.setUpdatedBy(operatorId);

        sysConfigMapper.updateById(config);

        configRefreshService.refreshConfig(config.getCategory(), configKey, value);

        logAudit(config, oldValue, value, operatorId, operator != null ? operator.getUsername() : "system", operator != null ? operator.getDisplayName() : "System");

        return SysConfigDTO.fromEntity(config);
    }

    @Transactional
    public List<SysConfigDTO> updateConfigByCategory(String category, Map<String, String> values, String operatorId) {
        log.info("Batch update config by category: category={}, operator={}", category, operatorId);

        List<SysConfig> configs = sysConfigMapper.selectByCategory(category);
        if (configs.isEmpty()) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "配置分类不存在: " + category);
        }

        User operator = userMapper.selectById(operatorId);
        String operatorName = operator != null ? operator.getUsername() : "system";
        String operatorDisplayName = operator != null ? operator.getDisplayName() : "System";

        for (SysConfig config : configs) {
            if (values.containsKey(config.getConfigKey())) {
                String oldValue = config.getValue();
                String newValue = values.get(config.getConfigKey());

                config.setValue(newValue);
                config.setUpdatedAt(LocalDateTime.now());
                config.setUpdatedBy(operatorId);

                sysConfigMapper.updateById(config);

                configRefreshService.refreshConfig(category, config.getConfigKey(), newValue);

                logAudit(config, oldValue, newValue, operatorId, operatorName, operatorDisplayName);
            }
        }

        return configs.stream()
                .map(SysConfigDTO::fromEntity)
                .collect(Collectors.toList());
    }

    private void logAudit(SysConfig config, String oldValue, String newValue, String operatorId, String operatorName, String operatorDisplayName) {

        Map<String, Object> details = new HashMap<>();
        details.put("category", config.getCategory());
        details.put("configKey", config.getConfigKey());
        details.put("oldValue", oldValue);
        details.put("newValue", newValue);

        auditService.logAction(
                operatorId,
                operatorName,
                operatorDisplayName,
                AuditActionEnums.SYSTEM_CONFIG_UPDATE,
                AuditLevelEnums.INFO,
                "SYSTEM",
                config.getId(),
                String.format("更新系统配置: %s.%s", config.getCategory(), config.getConfigKey()),
                null,
                null,
                details,
                "success"
        );
    }
}