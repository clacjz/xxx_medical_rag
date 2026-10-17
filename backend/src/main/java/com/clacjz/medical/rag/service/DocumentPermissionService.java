package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.mapper.DocumentMapper;
import com.clacjz.medical.rag.mapper.DocumentPermissionMapper;
import com.clacjz.medical.rag.mapper.DocumentUserPermissionMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.model.dto.*;
import com.clacjz.medical.rag.model.entity.*;
import com.clacjz.medical.rag.model.enums.AuditOperationTypeEnums;
import com.clacjz.medical.rag.model.enums.AuditTargetTypeEnums;
import com.clacjz.medical.rag.model.enums.PermissionLevelEnums;
import com.clacjz.medical.rag.model.enums.PermissionTypeEnums;
import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentPermissionService {

    private final DocumentMapper documentMapper;
    private final DocumentPermissionMapper documentPermissionMapper;
    private final DocumentUserPermissionMapper documentUserPermissionMapper;
    private final UserMapper userMapper;
    private final DepartmentService departmentService;
    private final PermissionAuditService permissionAuditService;
    private final PermissionCacheService permissionCacheService;

    @Transactional(readOnly = true)
    public AccessCheckResult checkDocumentAccess(String userId, String documentId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
        }

        // 1. 超级管理员检查
        if (user.getRole() == UserRoleEnum.SYSTEM_ADMIN) {
            return AccessCheckResult.allowed("系统管理员权限",
                    PermissionLevelEnums.EDIT, null, "system_admin");
        }

        // 2. 所有者检查
        if (document.isOwner(userId)) {
            return AccessCheckResult.allowed("文档所有者",
                    PermissionLevelEnums.EDIT, null, "owner");
        }

        // 3. 获取用户部门列表
        Set<String> userDeptIds = departmentService.getUserDepartmentIds(userId);

        // 4. 获取文档权限配置
        DocumentPermission permission = documentPermissionMapper.selectByDocumentId(documentId);
        if (permission == null) {
            // 无权限配置时，检查是否为部门管理员
            if (isDepartmentAdminOfDocument(user, document, userDeptIds)) {
                return AccessCheckResult.allowed("部门管理员权限",
                        PermissionLevelEnums.EDIT, null, "dept_admin");
            }
            return AccessCheckResult.denied("文档无权限配置");
        }

        // 5. 按权限类型判断
        return checkAccessByPermissionType(user, document, permission, userDeptIds);
    }

    private AccessCheckResult checkAccessByPermissionType(User user, Document document,
            DocumentPermission permission, Set<String> userDeptIds) {

        switch (permission.getPermissionType()) {
            case COMPANY_WIDE:
                // 所有认证用户可访问
                return AccessCheckResult.allowed("全公司可查看",
                        PermissionLevelEnums.READ, "0", "company_wide");

            case DEPARTMENT_WIDE:
                // 部门交集检查
                List<String> docDeptIds = permission.getDepartmentIdList();
                if (docDeptIds.isEmpty()) {
                    docDeptIds = document.getDepartmentIdList();
                }
                String matchedDept = findIntersection(docDeptIds, userDeptIds);
                if (matchedDept != null) {
                    return AccessCheckResult.allowed("属于文档所属部门",
                            PermissionLevelEnums.READ, matchedDept, "department_intersection");
                }
                if (isDepartmentAdminOfDocument(user, document, userDeptIds)) {
                    return AccessCheckResult.allowed("部门管理员",
                            PermissionLevelEnums.EDIT, null, "dept_admin");
                }
                return AccessCheckResult.denied("不在文档所属部门");

            case RESTRICTED:
                // 仅直接用户检查（不使用部门交集），包括权限过期检查
                DocumentUserPermission userPerm = documentUserPermissionMapper
                        .selectByDocumentIdAndUserId(document.getId(), user.getId());
                if (userPerm == null) {
                    return AccessCheckResult.denied("未在允许访问名单中");
                }
                if (userPerm.getExpiresAt() != null && userPerm.getExpiresAt().isBefore(LocalDateTime.now())) {
                    return AccessCheckResult.denied("权限已过期");
                }
                return AccessCheckResult.allowed("在允许访问名单中",
                        userPerm.getPermissionLevel() != null ? userPerm.getPermissionLevel() : PermissionLevelEnums.READ,
                        null, "direct_user");

            case DEPARTMENT_WIDE_RESTRICTED:
                // 部门交集 OR 直接用户（两者满足一即可）
                List<String> docDeptIds4 = permission.getDepartmentIdList();
                if (docDeptIds4.isEmpty()) {
                    docDeptIds4 = document.getDepartmentIdList();
                }
                String matchedDept4 = findIntersection(docDeptIds4, userDeptIds);
                if (matchedDept4 != null) {
                    return AccessCheckResult.allowed("属于文档所属部门",
                            PermissionLevelEnums.READ, matchedDept4, "department_intersection");
                }

                // 直接用户检查：查询 document_user_permissions 获取实际权限级别和过期时间
                DocumentUserPermission directUserPerm = documentUserPermissionMapper
                        .selectByDocumentIdAndUserId(document.getId(), user.getId());
                if (directUserPerm != null) {
                    if (directUserPerm.getExpiresAt() != null && directUserPerm.getExpiresAt().isBefore(LocalDateTime.now())) {
                        return AccessCheckResult.denied("权限已过期");
                    }
                    return AccessCheckResult.allowed("在允许访问名单中",
                            directUserPerm.getPermissionLevel() != null ? directUserPerm.getPermissionLevel() : PermissionLevelEnums.READ,
                            null, "direct_user");
                }

                if (isDepartmentAdminOfDocument(user, document, userDeptIds)) {
                    return AccessCheckResult.allowed("部门管理员",
                            PermissionLevelEnums.EDIT, null, "dept_admin");
                }
                return AccessCheckResult.denied("不在文档所属部门且未在允许访问名单中");

            default:
                return AccessCheckResult.denied("未知权限类型");
        }
    }

    /**
     * 查找两个集合的交集
     */
    private String findIntersection(List<String> list, Set<String> set) {
        for (String item : list) {
            if (set.contains(item)) {
                return item;
            }
        }
        return null;
    }

    private boolean isDepartmentAdminOfDocument(User user, Document document, Set<String> userDeptIds) {
        if (user.getRole() != UserRoleEnum.DEPT_ADMIN) {
            return false;
        }
        List<String> docDeptIds = document.getDepartmentIdList();
        for (String deptId : docDeptIds) {
            if (userDeptIds.contains(deptId)) {
                return true;
            }
        }
        return false;
    }

    @Transactional(readOnly = true)
    public BatchAccessCheckResult batchCheckAccess(String userId, List<String> documentIds) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        List<BatchAccessCheckResult.DocumentAccessResult> results = documentIds.stream()
                .map(docId -> {
                    AccessCheckResult checkResult = checkDocumentAccess(userId, docId);
                    return BatchAccessCheckResult.DocumentAccessResult.builder()
                            .documentId(docId)
                            .hasAccess(checkResult.isHasAccess())
                            .reason(checkResult.getReason())
                            .accessedDeptId(checkResult.getAccessedDeptId())
                            .matchReason(checkResult.getMatchReason())
                            .build();
                })
                .collect(Collectors.toList());

        return BatchAccessCheckResult.builder()
                .results(results)
                .build();
    }

    @Transactional(readOnly = true)
    public DocumentPermissionDTO getDocumentPermission(String documentId) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
        }

        DocumentPermissionDTO dto = DocumentPermissionDTO.builder()
                .documentId(documentId)
                .documentTitle(document.getTitle())
                .ownerId(document.getOwnerId())
                .departmentIds(document.getDepartmentIdList())
                .build();

        User owner = userMapper.selectById(document.getOwnerId());
        if (owner != null) {
            dto.setOwnerName(owner.getDisplayName());
        }

        DocumentPermission permission = documentPermissionMapper.selectByDocumentId(documentId);

        if (permission != null) {
            dto.setId(permission.getId());
            dto.setPermissionType(permission.getPermissionType());
            dto.setPermissionTypeDescription(permission.getPermissionType().getDescription());
            dto.setDepartmentIds(permission.getDepartmentIdList());
            dto.setDirectUserIds(permission.getDirectUserIdList());
            dto.setDirectUserCount(permission.getDirectUserIdList().size());
            dto.setGrantedBy(permission.getGrantedBy());
            dto.setGrantedAt(permission.getCreatedAt());
            dto.setUpdatedAt(permission.getUpdatedAt());

            User granter = userMapper.selectById(permission.getGrantedBy());
            if (granter != null) {
                dto.setGranterName(granter.getDisplayName());
            }

            if (permission.getPermissionType() == PermissionTypeEnums.RESTRICTED
                    || permission.getPermissionType() == PermissionTypeEnums.DEPARTMENT_WIDE_RESTRICTED) {
                List<DocumentUserPermission> userPermissions = documentUserPermissionMapper.selectByDocumentId(documentId);
                // 批量查询用户信息，避免N+1查询
                List<String> userIds = userPermissions.stream()
                        .map(DocumentUserPermission::getUserId)
                        .distinct()
                        .collect(Collectors.toList());
                Map<String, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
                        : userMapper.selectBatchIds(userIds).stream()
                                .collect(Collectors.toMap(User::getId, u -> u));

                dto.setAllowedUsers(userPermissions.stream()
                        .map(up -> {
                            DocumentPermissionDTO.AllowedUserDTO allowedUser = DocumentPermissionDTO.AllowedUserDTO.builder()
                                    .userId(up.getUserId())
                                    .permissionLevel(up.getPermissionLevel())
                                    .expiresAt(up.getExpiresAt())
                                    .build();

                            User permUser = userMap.get(up.getUserId());
                            if (permUser != null) {
                                allowedUser.setUsername(permUser.getUsername());
                                allowedUser.setDisplayName(permUser.getDisplayName());
                            }

                            return allowedUser;
                        })
                        .collect(Collectors.toList()));
            }
        }

        return dto;
    }

    @Transactional
    public DocumentPermissionDTO updateDocumentPermission(String documentId, UpdateDocumentPermissionRequest request,
                                                           String operatorId, String ipAddress, String userAgent) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
        }

        User operator = userMapper.selectById(operatorId);
        if (operator == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        if (!canModifyDocumentPermission(operator, document)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "无权修改此文档权限");
        }

        // 新增校验：10人限制
        if (request.getPermissionType() == PermissionTypeEnums.RESTRICTED
                || request.getPermissionType() == PermissionTypeEnums.DEPARTMENT_WIDE_RESTRICTED) {
            // 使用allowedUserIds进行10人限制校验，因为这是实际写入document_user_permissions表的用户列表
            int allowedUserCount = request.getAllowedUserIds() != null ? request.getAllowedUserIds().size() : 0;
            if (allowedUserCount > 10) {
                throw new BizException(ErrorCode.OPERATION_FAILED,
                        "直接授权用户数量不能超过10人，当前: " + allowedUserCount);
            }
        }


        DocumentPermission permission = documentPermissionMapper.selectByDocumentId(documentId);
        if (permission == null) {
            permission = DocumentPermission.builder()
                    .documentId(documentId)
                    .grantedBy(operatorId)
                    .build();
        }

        Map<String, Object> oldValue = new HashMap<>();
        oldValue.put("permissionType", permission.getPermissionType() != null ? permission.getPermissionType().name() : null);

        // 更新权限类型
        permission.setPermissionType(request.getPermissionType());
        permission.setGrantedBy(operatorId);

        // 新增：更新部门/用户列表
        if (request.getDepartmentIds() != null) {
            permission.setDepartmentIdList(request.getDepartmentIds());
        }

        if (request.getDirectUserIds() != null) {
            // 二次校验（allowedUserIds的校验已在上方完成）
            if (request.getDirectUserIds().size() > 10) {
                throw new BizException(ErrorCode.OPERATION_FAILED, "直接授权用户数量不能超过10人");
            }
            permission.setDirectUserIdList(request.getDirectUserIds());
        }


        // 保存权限
        if (permission.getId() != null) {
            documentPermissionMapper.updateById(permission);
        } else {
            documentPermissionMapper.insert(permission);
        }

        // 如果是 RESTRICTED 或 DEPARTMENT_WIDE_RESTRICTED 类型，更新直接用户权限表
        // DEPARTMENT_WIDE_RESTRICTED 同样需要同步 document_user_permissions，
        // 因为 checkAccessByPermissionType 会查询该表来获取直接用户的权限级别和过期时间
        Set<String> removedUserIds = new HashSet<>();
        if ((request.getPermissionType() == PermissionTypeEnums.RESTRICTED
                || request.getPermissionType() == PermissionTypeEnums.DEPARTMENT_WIDE_RESTRICTED)
                && request.getAllowedUserIds() != null) {
            // 保存被移除的用户ID列表，用于清除其缓存
            List<DocumentUserPermission> existingUserPerms = documentUserPermissionMapper.selectByDocumentId(documentId);
            Set<String> oldUserIds = existingUserPerms.stream()
                    .map(DocumentUserPermission::getUserId)
                    .collect(Collectors.toSet());
            removedUserIds.addAll(oldUserIds);
            removedUserIds.removeAll(request.getAllowedUserIds());

            documentUserPermissionMapper.deleteByDocumentId(documentId);
            for (String userId : request.getAllowedUserIds()) {
                DocumentUserPermission userPerm = DocumentUserPermission.builder()
                        .documentId(documentId)
                        .userId(userId)
                        .permissionLevel(PermissionLevelEnums.READ)
                        .grantedBy(operatorId)
                        .expiresAt(request.getExpiresAt())
                        .build();
                documentUserPermissionMapper.insert(userPerm);
            }
        }

        // 更新文档的部门信息
        if (request.getDepartmentIds() != null && !request.getDepartmentIds().isEmpty()) {
            document.setDepartmentIdList(request.getDepartmentIds());
            documentMapper.updateById(document);
        }

        Map<String, Object> newValue = new HashMap<>();
        newValue.put("permissionType", request.getPermissionType().name());
        if (request.getDepartmentIds() != null) {
            newValue.put("departmentIds", request.getDepartmentIds());
        }
        if (request.getDirectUserIds() != null) {
            newValue.put("directUserIds", request.getDirectUserIds());
        }
        if (request.getAllowedUserIds() != null) {
            newValue.put("allowedUsers", request.getAllowedUserIds());
        }

        permissionAuditService.logPermissionChange(
                operatorId,
                AuditOperationTypeEnums.UPDATE,
                AuditTargetTypeEnums.DOCUMENT,
                documentId,
                oldValue,
                newValue,
                ipAddress,
                userAgent
        );

        log.info("Document permission updated: documentId={}, operator={}, newType={}",
                documentId, operatorId, request.getPermissionType());

        // 清除缓存
        permissionCacheService.evictDocumentPermissionCache(documentId);
        // 如果有直接用户变更，清除相关用户权限缓存
        if (request.getDirectUserIds() != null) {
            for (String uid : request.getDirectUserIds()) {
                permissionCacheService.evictUserPermissionCache(uid);
            }
        }
        if (request.getAllowedUserIds() != null) {
            for (String uid : request.getAllowedUserIds()) {
                permissionCacheService.evictUserPermissionCache(uid);
            }
        }
        // 清除被移除用户的权限缓存（不在新列表中但在旧列表中的用户）
        for (String removedUid : removedUserIds) {
            permissionCacheService.evictUserPermissionCache(removedUid);
        }

        return getDocumentPermission(documentId);
    }

    @Transactional
    public void addUserPermission(String documentId, AddDocumentUserPermissionRequest request,
                                  String operatorId, String ipAddress, String userAgent) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
        }

        User operator = userMapper.selectById(operatorId);
        if (operator == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        if (!canModifyDocumentPermission(operator, document)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "无权修改此文档权限");
        }

        // 检查10人限制 — 统计 document_user_permissions 表中的实际行数
        DocumentPermission permission = documentPermissionMapper.selectByDocumentId(documentId);
        if (permission != null && permission.getPermissionType().supportsDirectUserAccess()) {
            long currentCount = documentUserPermissionMapper.selectByDocumentId(documentId).size();
            if (currentCount >= 10) {
                throw new BizException(ErrorCode.OPERATION_FAILED, "直接授权用户数量已达上限(10人)");
            }
        }

        if (documentUserPermissionMapper.existsByDocumentIdAndUserId(documentId, request.getUserId())) {
            throw new BizException(ErrorCode.USER_PERMISSION_EXISTS, "用户已有该文档权限");
        }

        DocumentUserPermission userPermission = DocumentUserPermission.builder()
                .documentId(documentId)
                .userId(request.getUserId())
                .permissionLevel(request.getPermissionLevel())
                .grantedBy(operatorId)
                .expiresAt(request.getExpiresAt())
                .build();

        documentUserPermissionMapper.insert(userPermission);

        // 同步 DocumentPermission.directUserIds，确保 DEPARTMENT_WIDE_RESTRICTED 类型检查一致
        if (permission != null && permission.getPermissionType().supportsDirectUserAccess()) {
            List<String> currentIds = permission.getDirectUserIdList();
            if (!currentIds.contains(request.getUserId())) {
                currentIds.add(request.getUserId());
                permission.setDirectUserIdList(currentIds);
                documentPermissionMapper.updateById(permission);
            }
        }

        Map<String, Object> newValue = new HashMap<>();
        newValue.put("userId", request.getUserId());
        newValue.put("permissionLevel", request.getPermissionLevel().name());

        permissionAuditService.logPermissionChange(
                operatorId,
                AuditOperationTypeEnums.GRANT,
                AuditTargetTypeEnums.DOCUMENT,
                documentId,
                null,
                newValue,
                ipAddress,
                userAgent
        );

        log.info("User permission added: documentId={}, userId={}, operator={}", documentId, request.getUserId(), operatorId);

        // 清除缓存
        permissionCacheService.evictDocumentPermissionCache(documentId);
        permissionCacheService.evictUserPermissionCache(request.getUserId());
    }

    @Transactional
    public void removeUserPermission(String documentId, String targetUserId,
                                     String operatorId, String ipAddress, String userAgent) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
        }

        User operator = userMapper.selectById(operatorId);
        if (operator == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        if (!canModifyDocumentPermission(operator, document)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "无权修改此文档权限");
        }

        DocumentUserPermission userPermission = documentUserPermissionMapper
                .selectByDocumentIdAndUserId(documentId, targetUserId);
        if (userPermission == null) {
            throw new BizException(ErrorCode.USER_PERMISSION_NOT_FOUND);
        }

        Map<String, Object> oldValue = new HashMap<>();
        oldValue.put("userId", targetUserId);
        oldValue.put("permissionLevel", userPermission.getPermissionLevel().name());

        documentUserPermissionMapper.deleteByDocumentIdAndUserId(documentId, targetUserId);

        // 同步 DocumentPermission.directUserIds，确保 DEPARTMENT_WIDE_RESTRICTED 类型检查一致
        DocumentPermission permForSync = documentPermissionMapper.selectByDocumentId(documentId);
        if (permForSync != null && permForSync.getPermissionType().supportsDirectUserAccess()) {
            List<String> currentIds = permForSync.getDirectUserIdList();
            if (currentIds.remove(targetUserId)) {
                permForSync.setDirectUserIdList(currentIds);
                documentPermissionMapper.updateById(permForSync);
            }
        }

        permissionAuditService.logPermissionChange(
                operatorId,
                AuditOperationTypeEnums.REVOKE,
                AuditTargetTypeEnums.DOCUMENT,
                documentId,
                oldValue,
                null,
                ipAddress,
                userAgent
        );

        log.info("User permission removed: documentId={}, userId={}, operator={}", documentId, targetUserId, operatorId);

        // 清除缓存
        permissionCacheService.evictDocumentPermissionCache(documentId);
        permissionCacheService.evictUserPermissionCache(targetUserId);
    }

    /**
     * 获取并校验文档，用于controller层权限校验
     */
    public Document getDocumentWithCheck(String documentId) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        return document;
    }

    public boolean canModifyDocumentPermission(User user, Document document) {
        if (user.getRole() == UserRoleEnum.SYSTEM_ADMIN) {
            return true;
        }

        if (document.isOwner(user.getId())) {
            return true;
        }

        if (user.getRole() == UserRoleEnum.DEPT_ADMIN) {
            Set<String> userDeptIds = departmentService.getUserDepartmentIds(user.getId());
            List<String> docDeptIds = document.getDepartmentIdList();
            for (String deptId : docDeptIds) {
                if (userDeptIds.contains(deptId)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean canDeleteDocument(User user, Document document) {
        return canModifyDocumentPermission(user, document);
    }

    public void checkPermission(String documentId, PermissionLevelEnums requiredLevel, User user) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
        }

        AccessCheckResult accessResult = checkDocumentAccess(user.getId(), documentId);

        if (!accessResult.isHasAccess()) {
            throw new BizException(ErrorCode.PERMISSION_DENIED,
                    String.format("无权操作此文档: %s", accessResult.getReason()));
        }

        PermissionLevelEnums userLevel = accessResult.getPermissionLevel();

        if (userLevel == null || requiredLevel.getLevel() > userLevel.getLevel()) {
            throw new BizException(ErrorCode.PERMISSION_DENIED,
                    String.format("需要 [%s] 权限，当前权限: [%s]",
                            requiredLevel.getDescription(),
                            userLevel != null ? userLevel.getDescription() : "无"));
        }
    }

    public void checkDocumentOperatePermission(String documentId, User user) {
        checkPermission(documentId, PermissionLevelEnums.EDIT, user);
    }
}
