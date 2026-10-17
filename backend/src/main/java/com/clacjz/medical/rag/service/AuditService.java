package com.clacjz.medical.rag.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clacjz.medical.rag.mapper.AuditLogMapper;
import com.clacjz.medical.rag.model.dto.AuditLogDTO;
import com.clacjz.medical.rag.model.entity.AuditLog;
import com.clacjz.medical.rag.model.enums.AuditActionEnums;
import com.clacjz.medical.rag.model.enums.AuditLevelEnums;
import com.clacjz.medical.rag.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogMapper auditLogMapper;
    private final JsonUtil jsonUtil;
    private final TransactionTemplate transactionTemplate;

    @Lazy
    private final AuditService self;

    @Async
    public void logAction(String userId, String username, String displayName,
                         AuditActionEnums action, AuditLevelEnums level,
                         String resourceType, String resourceId,
                         String description, String ipAddress, String userAgent,
                         Map<String, Object> details, String status) {
        try {
            transactionTemplate.executeWithoutResult(transactionStatus -> {
                try {
                    String detailsJson = jsonUtil.toJson(details);

                    AuditLog auditLog = AuditLog.builder()
                            .userId(userId)
                            .username(username)
                            .displayName(displayName)
                            .action(action)
                            .level(level)
                            .resourceType(resourceType)
                            .resourceId(resourceId)
                            .description(description)
                            .ipAddress(ipAddress)
                            .userAgent(userAgent)
                            .details(detailsJson)
                            .status(status)
                            .build();

                    auditLogMapper.insert(auditLog);
                    log.debug("Audit log created: userId={}, action={}, level={}, resourceType={}, resourceId={}",
                            userId, action, level, resourceType, resourceId);
                } catch (Exception e) {
                    log.error("Failed to create audit log: userId={}, action={}, error={}",
                            userId, action, e.getMessage(), e);
                    throw new RuntimeException("Audit log save failed", e);
                }
            });
        } catch (Exception e) {
            log.error("Failed to execute audit log transaction: userId={}, action={}, error={}",
                    userId, action, e.getMessage(), e);
        }
    }

    public void logLogin(String userId, String username, String displayName,
                        String ipAddress, String userAgent, boolean success, String reason) {
        AuditActionEnums action = success ? AuditActionEnums.LOGIN : AuditActionEnums.SECURITY_LOGIN_FAILED;
        AuditLevelEnums level = success ? AuditLevelEnums.INFO : AuditLevelEnums.WARNING;
        String status = success ? "success" : "failed";
        Map<String, Object> details = Map.of(
                "reason", reason != null ? reason : "success"
        );
        self.logAction(userId, username, displayName, action, level,
                "AUTH", null, null, ipAddress, userAgent, details, status);
    }

    public void logLogout(String userId, String username, String displayName,
                         String ipAddress, String userAgent) {
        self.logAction(userId, username, displayName, AuditActionEnums.LOGOUT, AuditLevelEnums.INFO,
                "AUTH", null, null, ipAddress, userAgent, null, "success");
    }

    public void logRegister(String userId, String username, String displayName,
                          String ipAddress, String userAgent, boolean success) {
        self.logAction(userId, username, displayName, AuditActionEnums.REGISTER,
                success ? AuditLevelEnums.INFO : AuditLevelEnums.WARNING,
                "AUTH", userId, null, ipAddress, userAgent,
                Map.of("success", success), success ? "success" : "failed");
    }

    public void logPasswordChange(String userId, String username, String displayName,
                                  String ipAddress, String userAgent, boolean success) {
        self.logAction(userId, username, displayName, AuditActionEnums.PASSWORD_CHANGE,
                AuditLevelEnums.SECURITY,
                "AUTH", userId, null, ipAddress, userAgent,
                Map.of("success", success), success ? "success" : "failed");
    }

    public void logUserOperation(String operatorId, String operatorName, String operatorDisplayName,
                                AuditActionEnums action, String userTargetId, String userTargetName,
                                String ipAddress, String userAgent, boolean success) {
        AuditLevelEnums level = determineAuditLevel(action);
        Map<String, Object> details = Map.of(
                "targetUserId", userTargetId,
                "targetUserName", userTargetName != null ? userTargetName : "",
                "success", success
        );
        self.logAction(operatorId, operatorName, operatorDisplayName, action, level,
                "USER", userTargetId, null, ipAddress, userAgent, details,
                success ? "success" : "failed");
    }

    public void logDocumentOperation(String userId, String username, String displayName,
                                    AuditActionEnums action, String documentId, String documentName,
                                    String ipAddress, String userAgent, boolean success) {
        AuditLevelEnums level = determineAuditLevel(action);
        Map<String, Object> details = Map.of(
                "documentName", documentName != null ? documentName : "",
                "success", success
        );
        self.logAction(userId, username, displayName, action, level,
                "DOCUMENT", documentId, null, ipAddress, userAgent, details,
                success ? "success" : "failed");
    }

    public void logCollectionOperation(String userId, String username, String displayName,
                                      AuditActionEnums action, String collectionId, String collectionName,
                                      String ipAddress, String userAgent, boolean success) {
        AuditLevelEnums level = determineAuditLevel(action);
        Map<String, Object> details = Map.of(
                "collectionName", collectionName != null ? collectionName : "",
                "success", success
        );
        self.logAction(userId, username, displayName, action, level,
                "COLLECTION", collectionId, null, ipAddress, userAgent, details,
                success ? "success" : "failed");
    }

    public void logPermissionOperation(String operatorId, String operatorName, String operatorDisplayName,
                                      AuditActionEnums action, String targetType, String targetId, String targetName,
                                      String ipAddress, String userAgent, boolean success) {
        Map<String, Object> details = Map.of(
                "targetType", targetType,
                "targetName", targetName != null ? targetName : "",
                "success", success
        );
        self.logAction(operatorId, operatorName, operatorDisplayName, action, AuditLevelEnums.SECURITY,
                "PERMISSION", targetId, null, ipAddress, userAgent, details,
                success ? "success" : "failed");
    }

    private AuditLevelEnums determineAuditLevel(AuditActionEnums action) {
        return switch (action) {
            case USER_DELETE, DOCUMENT_DELETE, COLLECTION_DELETE,
                 USER_LOCK, PERMISSION_GRANT, PERMISSION_REVOKE -> AuditLevelEnums.WARNING;
            case USER_CREATE, COLLECTION_CREATE, DOCUMENT_UPLOAD -> AuditLevelEnums.INFO;
            default -> AuditLevelEnums.INFO;
        };
    }

    /**
     * 记录系统操作（如邮件发送等）
     */
    public void logSystemOperation(String userId, String action, String targetType,
                                   String targetId, boolean success, String description) {
        self.logAction(userId, null, null, AuditActionEnums.SYSTEM_OPERATION,
                AuditLevelEnums.INFO,
                targetType, targetId, null, null, null,
                Map.of("description", description != null ? description : ""),
                success ? "success" : "failed");
    }

    @Transactional(readOnly = true)
    public IPage<AuditLogDTO> getAuditLogs(int page, int pageSize, String userId,
                                          String action, AuditLevelEnums level,
                                          LocalDateTime startTime, LocalDateTime endTime) {
        Page<AuditLog> mpPage = new Page<>(page + 1, pageSize);

        AuditActionEnums actionEnum = null;
        if (action != null && !action.isBlank()) {
            try {
                actionEnum = AuditActionEnums.valueOf(action);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid audit action enum value: {}", action);
            }
        }

        IPage<AuditLog> logs = auditLogMapper.selectByFilters(
                userId,
                actionEnum != null ? actionEnum.name() : null,
                level != null ? level.name() : null,
                startTime,
                endTime,
                mpPage
        );

        return logs.convert(AuditLogDTO::fromEntity);
    }

    @Transactional(readOnly = true)
    public AuditLogDTO getAuditLogById(String id) {
        AuditLog auditLog = auditLogMapper.selectById(id);
        if (auditLog == null) {
            throw new RuntimeException("Audit log not found: " + id);
        }
        return AuditLogDTO.fromEntity(auditLog);
    }

    @Transactional(readOnly = true)
    public String exportAuditLogs(LocalDateTime startTime, LocalDateTime endTime) {
        Page<AuditLog> mpPage = new Page<>(1, 10000);
        IPage<AuditLog> logs = auditLogMapper.selectByFilters(null, null, null, startTime, endTime, mpPage);

        StringBuilder csv = new StringBuilder();
        csv.append("ID,用户ID,用户名,显示名称,操作,操作描述,级别,级别描述,资源类型,资源ID,描述,IP地址,时间,状态\n");

        for (AuditLog logItem : logs.getRecords()) {
            csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                    logItem.getId(),
                    logItem.getUserId(),
                    logItem.getUsername(),
                    logItem.getDisplayName(),
                    logItem.getAction(),
                    logItem.getAction() != null ? logItem.getAction().getDescription() : "",
                    logItem.getLevel(),
                    logItem.getLevel() != null ? logItem.getLevel().getDescription() : "",
                    logItem.getResourceType(),
                    logItem.getResourceId(),
                    logItem.getDescription(),
                    logItem.getIpAddress(),
                    logItem.getTimestamp(),
                    logItem.getStatus()));
        }

        return csv.toString();
    }
}
