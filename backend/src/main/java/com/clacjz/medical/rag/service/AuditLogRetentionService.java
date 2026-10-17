package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.mapper.AuditLogMapper;
import com.clacjz.medical.rag.mapper.PermissionAuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 审计日志保留策略服务
 *
 * 根据设计文档LLD Section 16.2定义的保留策略:
 * - 权限变更审计: 永久保留
 * - 用户管理审计: 永久保留
 * - 文档操作审计: 1年
 * - 系统配置审计: 6个月
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogRetentionService {

    private final AuditLogMapper auditLogMapper;
    private final PermissionAuditLogMapper permissionAuditLogMapper;

    /**
     * 每月1日凌晨2点执行清理任务
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    @Transactional
    public void cleanupExpiredAuditLogs() {
        log.info("Starting audit log cleanup task...");

        LocalDateTime now = LocalDateTime.now();

        // 文档操作审计保留1年
        LocalDateTime documentOperationRetention = now.minusYears(1);
        int deletedDocumentOps = auditLogMapper.deleteExpiredByCategory("DOCUMENT_OPERATION", documentOperationRetention);
        log.info("Cleaned up {} expired document operation audit logs (older than 1 year)", deletedDocumentOps);

        // 系统配置审计保留6个月
        LocalDateTime systemConfigRetention = now.minusMonths(6);
        int deletedSystemConfig = auditLogMapper.deleteExpiredByCategory("SYSTEM_CONFIG", systemConfigRetention);
        log.info("Cleaned up {} expired system config audit logs (older than 6 months)", deletedSystemConfig);

        log.info("Audit log cleanup task completed");
    }

    /**
     * 获取审计日志保留状态信息
     */
    public RetentionStatus getRetentionStatus() {
        LocalDateTime now = LocalDateTime.now();

        long documentOperationCount = auditLogMapper.countByCategory("DOCUMENT_OPERATION");
        long systemConfigCount = auditLogMapper.countByCategory("SYSTEM_CONFIG");
        long permissionAuditCount = permissionAuditLogMapper.countAll();
        long userManagementCount = auditLogMapper.countByCategory("USER_MANAGEMENT");

        return RetentionStatus.builder()
                .documentOperationCount(documentOperationCount)
                .documentOperationRetentionYears(1)
                .systemConfigCount(systemConfigCount)
                .systemConfigRetentionMonths(6)
                .permissionAuditCount(permissionAuditCount)
                .userManagementCount(userManagementCount)
                .lastCleanupTime(now)
                .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class RetentionStatus {
        private long documentOperationCount;
        private int documentOperationRetentionYears;
        private long systemConfigCount;
        private int systemConfigRetentionMonths;
        private long permissionAuditCount;
        private long userManagementCount;
        private LocalDateTime lastCleanupTime;
    }
}