package com.clacjz.medical.rag.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clacjz.medical.rag.mapper.DocumentMapper;
import com.clacjz.medical.rag.mapper.PermissionAuditLogMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.model.dto.PermissionAuditLogDTO;
import com.clacjz.medical.rag.model.entity.Document;
import com.clacjz.medical.rag.model.entity.PermissionAuditLog;
import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.enums.AuditOperationTypeEnums;
import com.clacjz.medical.rag.model.enums.AuditTargetTypeEnums;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionAuditService {

    private final PermissionAuditLogMapper auditLogMapper;
    private final UserMapper userMapper;
    private final DocumentMapper documentMapper;

    /**
     * 记录权限变更审计日志。
     * 使用事务提交后写入模式，确保只有调用方事务成功提交后才写入审计日志，
     * 避免调用方事务回滚时产生孤立的审计记录。
     */
    public void logPermissionChange(String operatorId, AuditOperationTypeEnums operationType,
                                    AuditTargetTypeEnums targetType, String targetId,
                                    Map<String, Object> oldValue, Map<String, Object> newValue,
                                    String ipAddress, String userAgent) {
        PermissionAuditLog auditLog = PermissionAuditLog.builder()
                .operatorId(operatorId)
                .operationType(operationType)
                .targetType(targetType)
                .targetId(targetId)
                .oldValue(oldValue)
                .newValue(newValue)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        // 在调用方事务提交后执行审计日志写入，避免回滚时产生孤立记录
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        auditLogMapper.insert(auditLog);
                        log.debug("Permission audit log created: operation={}, targetType={}, targetId={}",
                                operationType, targetType, targetId);
                    } catch (Exception e) {
                        log.error("Failed to create permission audit log after commit: operation={}, targetType={}, targetId={}",
                                operationType, targetType, targetId, e);
                    }
                }
            });
        } else {
            // 无事务上下文时直接写入
            try {
                auditLogMapper.insert(auditLog);
                log.debug("Permission audit log created (no transaction): operation={}, targetType={}, targetId={}",
                        operationType, targetType, targetId);
            } catch (Exception e) {
                log.error("Failed to create permission audit log: operation={}, targetType={}, targetId={}",
                        operationType, targetType, targetId, e);
            }
        }
    }

    @Transactional(readOnly = true)
    public IPage<PermissionAuditLogDTO> getAuditLogs(int page, int pageSize, String operatorId,
                                                     AuditTargetTypeEnums targetType, String targetId,
                                                     AuditOperationTypeEnums operationType,
                                                     LocalDateTime startTime, LocalDateTime endTime) {
        int zeroBasedPage = Math.max(0, page);
        int offset = zeroBasedPage * pageSize;

        List<PermissionAuditLog> logs = auditLogMapper.selectByFilters(
                operatorId,
                targetType != null ? targetType.name() : null,
                targetId,
                operationType != null ? operationType.name() : null,
                startTime,
                endTime,
                offset,
                pageSize
        );

        List<PermissionAuditLogDTO> dtoList = logs.stream()
                .map(this::convertToDTO)
                .toList();

        long total = auditLogMapper.countByFilters(
                operatorId,
                targetType != null ? targetType.name() : null,
                targetId,
                operationType != null ? operationType.name() : null,
                startTime,
                endTime
        );

        Page<PermissionAuditLogDTO> pageResult = new Page<>(zeroBasedPage + 1, pageSize);
        pageResult.setRecords(dtoList);
        pageResult.setTotal(total);
        return pageResult;
    }

    @Transactional(readOnly = true)
    public List<PermissionAuditLogDTO> getRecentAuditLogs(String targetType, String targetId, int limit) {
        AuditTargetTypeEnums type = AuditTargetTypeEnums.valueOf(targetType);
        List<PermissionAuditLog> logs = auditLogMapper.selectRecentByTarget(type.name(), targetId, limit);
        return logs.stream().map(this::convertToDTO).toList();
    }

    private PermissionAuditLogDTO convertToDTO(PermissionAuditLog log) {
        PermissionAuditLogDTO dto = PermissionAuditLogDTO.fromEntity(log);

        User operator = userMapper.selectById(log.getOperatorId());
        if (operator != null) {
            dto.setOperator(PermissionAuditLogDTO.OperatorDTO.builder()
                    .id(operator.getId())
                    .username(operator.getUsername())
                    .displayName(operator.getDisplayName())
                    .build());
        }

        // 如果目标是用户且与操作者相同，复用已查询的 operator 信息
        String targetName;
        if (log.getTargetType() == AuditTargetTypeEnums.USER
                && log.getTargetId().equals(log.getOperatorId())
                && operator != null) {
            targetName = operator.getDisplayName() != null ? operator.getDisplayName() : operator.getUsername();
        } else {
            targetName = getTargetName(log.getTargetType(), log.getTargetId());
        }
        dto.setTargetName(targetName);

        return dto;
    }

    private String getTargetName(AuditTargetTypeEnums targetType, String targetId) {
        return switch (targetType) {
            case USER -> {
                User user = userMapper.selectById(targetId);
                yield user != null ? (user.getDisplayName() != null ? user.getDisplayName() : user.getUsername()) : "未知用户";
            }
            case DOCUMENT -> {
                Document doc = documentMapper.selectById(targetId);
                yield doc != null ? doc.getTitle() : "未知文档";
            }
            case DEPARTMENT -> "部门ID: " + targetId;
            case ROLE -> "角色ID: " + targetId;
        };
    }
}
