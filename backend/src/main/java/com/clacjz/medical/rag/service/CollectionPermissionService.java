package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.context.TenantContext;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.mapper.CollectionMapper;
import com.clacjz.medical.rag.mapper.CollectionPermissionMapper;
import com.clacjz.medical.rag.mapper.CollectionUserPermissionMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.model.dto.AddCollectionUserPermissionRequest;
import com.clacjz.medical.rag.model.dto.CollectionPermissionDTO;
import com.clacjz.medical.rag.model.dto.UpdateCollectionPermissionRequest;
import com.clacjz.medical.rag.model.entity.Collection;
import com.clacjz.medical.rag.model.entity.CollectionPermission;
import com.clacjz.medical.rag.model.entity.CollectionUserPermission;
import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.enums.CollectionPermissionLevelEnums;
import com.clacjz.medical.rag.model.enums.CollectionPermissionTypeEnums;
import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionPermissionService {

    private final CollectionMapper collectionMapper;
    private final CollectionPermissionMapper collectionPermissionMapper;
    private final CollectionUserPermissionMapper collectionUserPermissionMapper;
    private final UserMapper userMapper;
    private final DepartmentService departmentService;
    private final PermissionCacheService permissionCacheService;

    @Transactional(readOnly = true)
    public CollectionAccessCheckResult checkCollectionAccess(String userId, String collectionId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        if (user.getRole() == UserRoleEnum.SYSTEM_ADMIN) {
            return CollectionAccessCheckResult.allowed("系统管理员权限", CollectionPermissionLevelEnums.ADMIN);
        }

        Collection collection = collectionMapper.selectById(collectionId);
        if (collection == null) {
            throw new BizException(ErrorCode.COLLECTION_NOT_FOUND);
        }

        if (userId.equals(collection.getOwnerId())) {
            return CollectionAccessCheckResult.allowed("知识库所有者", CollectionPermissionLevelEnums.ADMIN);
        }

        CollectionPermission permission = collectionPermissionMapper.selectByCollectionId(collectionId);

        if (permission == null) {
            if (isDepartmentAdminOfCollection(user, collection)) {
                return CollectionAccessCheckResult.allowed("部门管理员权限", CollectionPermissionLevelEnums.MANAGE);
            }
            return CollectionAccessCheckResult.denied("知识库无权限配置");
        }

        return switch (permission.getPermissionType()) {
            case PUBLIC -> CollectionAccessCheckResult.allowed("公开访问", CollectionPermissionLevelEnums.READ);
            case DEPARTMENT_WIDE -> checkDepartmentCollectionAccess(user, collection, permission);
            case RESTRICTED -> checkRestrictedCollectionAccess(user, collection, permission);
            case DEPARTMENT_WIDE_RESTRICTED -> checkDepartmentWideRestrictedAccess(user, collection, permission);
        };
    }

    /**
     * DEPARTMENT_WIDE_RESTRICTED类型的访问检查：
     * 部门成员拥有READ权限，直接用户权限列表中的用户拥有其对应权限级别（两者满足一即可），
     * 不在部门内且不在直接用户列表中的用户被拒绝访问。
     */
    private CollectionAccessCheckResult checkDepartmentWideRestrictedAccess(User user, Collection collection, CollectionPermission permission) {
        Set<String> userDeptIds = departmentService.getUserDepartmentIds(user.getId());
        String collectionDeptId = collection.getDepartmentId();

        // 1. 部门成员检查：属于知识库所属部门的用户拥有READ权限
        if (collectionDeptId != null && userDeptIds.contains(collectionDeptId)) {
            return CollectionAccessCheckResult.allowed("属于知识库所属部门", CollectionPermissionLevelEnums.READ);
        }

        // 2. 检查直接用户权限（对所有用户，包括非部门成员）
        List<CollectionUserPermission> userPermList = collectionUserPermissionMapper
                .selectByCollectionIdAndUserId(collection.getId(), user.getId());
        if (userPermList != null && !userPermList.isEmpty()) {
            CollectionUserPermission userPerm = userPermList.get(0);
            if (!userPerm.isExpired()) {
                return CollectionAccessCheckResult.allowed("在允许访问名单中", userPerm.getPermissionLevel());
            }
        }

        // 3. 部门管理员仍可访问
        if (isDepartmentAdminOfCollection(user, collection)) {
            return CollectionAccessCheckResult.allowed("部门管理员", CollectionPermissionLevelEnums.MANAGE);
        }

        return CollectionAccessCheckResult.denied("不在知识库所属部门且未在允许访问名单中");
    }

    private CollectionAccessCheckResult checkDepartmentCollectionAccess(User user, Collection collection, CollectionPermission permission) {
        Set<String> userDeptIds = departmentService.getUserDepartmentIds(user.getId());
        String collectionDeptId = collection.getDepartmentId();

        if (userDeptIds.contains(collectionDeptId)) {
            return CollectionAccessCheckResult.allowed("属于知识库所属部门", CollectionPermissionLevelEnums.READ);
        }

        if (isDepartmentAdminOfCollection(user, collection)) {
            return CollectionAccessCheckResult.allowed("部门管理员", CollectionPermissionLevelEnums.MANAGE);
        }

        return CollectionAccessCheckResult.denied("不在知识库所属部门");
    }

    private CollectionAccessCheckResult checkRestrictedCollectionAccess(User user, Collection collection, CollectionPermission permission) {
        List<CollectionUserPermission> userPermList = collectionUserPermissionMapper
                .selectByCollectionIdAndUserId(collection.getId(), user.getId());

        if (userPermList == null || userPermList.isEmpty()) {
            return CollectionAccessCheckResult.denied("未在允许访问名单中");
        }

        CollectionUserPermission userPerm = userPermList.get(0);

        if (userPerm.isExpired()) {
            return CollectionAccessCheckResult.denied("权限已过期");
        }

        return CollectionAccessCheckResult.allowed("在允许访问名单中", userPerm.getPermissionLevel());
    }

    private boolean isDepartmentAdminOfCollection(User user, Collection collection) {
        if (user.getRole() != UserRoleEnum.DEPT_ADMIN) {
            return false;
        }

        Set<String> userDeptIds = departmentService.getUserDepartmentIds(user.getId());
        return collection.getDepartmentId() != null && userDeptIds.contains(collection.getDepartmentId());
    }

    @Transactional(readOnly = true)
    public CollectionPermissionDTO getCollectionPermission(String collectionId) {
        Collection collection = collectionMapper.selectById(collectionId);
        if (collection == null) {
            throw new BizException(ErrorCode.COLLECTION_NOT_FOUND);
        }

        CollectionPermissionDTO dto = CollectionPermissionDTO.builder()
                .collectionId(collectionId)
                .collectionName(collection.getName())
                .ownerId(collection.getOwnerId())
                .build();

        User owner = userMapper.selectById(collection.getOwnerId());
        if (owner != null) {
            dto.setOwnerName(owner.getDisplayName());
        }

        CollectionPermission permission = collectionPermissionMapper.selectByCollectionId(collectionId);

        if (permission != null) {
            dto.setId(permission.getId());
            dto.setPermissionType(permission.getPermissionType());
            dto.setPermissionTypeDescription(permission.getPermissionType().getDescription());
            dto.setGrantedBy(permission.getGrantedBy());
            dto.setGrantedAt(permission.getCreatedAt());
            dto.setUpdatedAt(permission.getUpdatedAt());

            User granter = userMapper.selectById(permission.getGrantedBy());
            if (granter != null) {
                dto.setGranterName(granter.getDisplayName());
            }

            if (permission.getPermissionType() == CollectionPermissionTypeEnums.RESTRICTED
                    || permission.getPermissionType() == CollectionPermissionTypeEnums.DEPARTMENT_WIDE_RESTRICTED) {
                List<CollectionUserPermission> userPermissions = collectionUserPermissionMapper.selectByCollectionId(collectionId);
                // 批量查询用户信息，避免N+1查询
                List<String> userIds = userPermissions.stream()
                        .map(CollectionUserPermission::getUserId)
                        .distinct()
                        .collect(Collectors.toList());
                Map<String, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
                        : userMapper.selectBatchIds(userIds).stream()
                                .collect(Collectors.toMap(User::getId, u -> u));

                dto.setAllowedUsers(userPermissions.stream()
                        .map(up -> {
                            CollectionPermissionDTO.AllowedUserDTO allowedUser = CollectionPermissionDTO.AllowedUserDTO.builder()
                                    .userId(up.getUserId())
                                    .permissionLevel(up.getPermissionLevel().getDescription())
                                    .expiresAt(up.getExpiresAt())
                                    .build();

                            User u = userMap.get(up.getUserId());
                            if (u != null) {
                                allowedUser.setUsername(u.getUsername());
                                allowedUser.setDisplayName(u.getDisplayName());
                            }

                            return allowedUser;
                        })
                        .collect(Collectors.toList()));
            }
        }

        return dto;
    }

    @Transactional
    public CollectionPermissionDTO updateCollectionPermission(String collectionId, UpdateCollectionPermissionRequest request) {
        Collection collection = collectionMapper.selectById(collectionId);
        if (collection == null) {
            throw new BizException(ErrorCode.COLLECTION_NOT_FOUND);
        }

        String operatorId = TenantContext.getUserId();
        User operator = userMapper.selectById(operatorId);
        if (operator == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        if (!canModifyCollectionPermission(operator, collection)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "无权修改此知识库权限");
        }

        CollectionPermission permission = collectionPermissionMapper.selectByCollectionId(collectionId);
        if (permission == null) {
            permission = CollectionPermission.builder()
                    .collectionId(collectionId)
                    .grantedBy(operatorId)
                    .build();
        }

        permission.setPermissionType(request.getPermissionType());
        permission.setGrantedBy(operatorId);

        if (permission.getId() == null) {
            collectionPermissionMapper.insert(permission);
        } else {
            collectionPermissionMapper.updateById(permission);
        }

        // 保存被移除的用户ID列表，用于清除其缓存
        Set<String> removedUserIds = new HashSet<>();

        if ((request.getPermissionType() == CollectionPermissionTypeEnums.RESTRICTED
                || request.getPermissionType() == CollectionPermissionTypeEnums.DEPARTMENT_WIDE_RESTRICTED)
                && request.getAllowedUserIds() != null) {
            // 10人限制校验
            if (request.getAllowedUserIds().size() > 10) {
                throw new BizException(ErrorCode.OPERATION_FAILED,
                        "直接授权用户数量不能超过10人，当前: " + request.getAllowedUserIds().size());
            }
            List<CollectionUserPermission> existingUserPerms = collectionUserPermissionMapper.selectByCollectionId(collectionId);
            removedUserIds.addAll(existingUserPerms.stream()
                    .map(CollectionUserPermission::getUserId)
                    .collect(Collectors.toSet()));
            removedUserIds.removeAll(request.getAllowedUserIds());

            for (CollectionUserPermission up : existingUserPerms) {
                collectionUserPermissionMapper.deleteById(up.getId());
            }
            List<CollectionUserPermission> newPermissions = request.getAllowedUserIds().stream()
                    .map(userId -> CollectionUserPermission.builder()
                            .collectionId(collectionId)
                            .userId(userId)
                            .permissionLevel(CollectionPermissionLevelEnums.READ)
                            .grantedBy(operatorId)
                            .expiresAt(request.getExpiresAt())
                            .build())
                    .collect(Collectors.toList());
            for (CollectionUserPermission up : newPermissions) {
                collectionUserPermissionMapper.insert(up);
            }
        }

        log.info("Collection permission updated: collectionId={}, operator={}, newType={}",
                collectionId, operatorId, request.getPermissionType());

        // 清除该知识库相关用户的权限缓存
        evictCollectionPermissionCache(collectionId);
        // 清除被移除用户的权限缓存（不在新列表中但在旧列表中的用户）
        for (String removedUserId : removedUserIds) {
            permissionCacheService.evictUserPermissionCache(removedUserId);
        }

        return getCollectionPermission(collectionId);
    }

    @Transactional
    public void addUserPermission(String collectionId, AddCollectionUserPermissionRequest request) {
        Collection collection = collectionMapper.selectById(collectionId);
        if (collection == null) {
            throw new BizException(ErrorCode.COLLECTION_NOT_FOUND);
        }

        String operatorId = TenantContext.getUserId();
        User operator = userMapper.selectById(operatorId);
        if (operator == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        if (!canModifyCollectionPermission(operator, collection)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "无权修改此知识库权限");
        }

        // 检查知识库权限类型是否支持直接用户访问，以及10人限制
        CollectionPermission permConfig = collectionPermissionMapper.selectByCollectionId(collectionId);
        if (permConfig != null && permConfig.getPermissionType() != null
                && permConfig.getPermissionType().supportsDirectUserAccess()) {
            long currentCount = collectionUserPermissionMapper.selectByCollectionId(collectionId).size();
            if (currentCount >= 10) {
                throw new BizException(ErrorCode.OPERATION_FAILED, "直接授权用户数量已达上限(10人)");
            }
        }

        List<CollectionUserPermission> existing = collectionUserPermissionMapper
                .selectByCollectionIdAndUserId(collectionId, request.getUserId());
        if (existing != null && !existing.isEmpty()) {
            throw new BizException(ErrorCode.USER_PERMISSION_EXISTS, "用户已有该知识库权限");
        }

        CollectionUserPermission userPermission = CollectionUserPermission.builder()
                .collectionId(collectionId)
                .userId(request.getUserId())
                .permissionLevel(request.getPermissionLevel())
                .grantedBy(operatorId)
                .expiresAt(request.getExpiresAt())
                .build();

        collectionUserPermissionMapper.insert(userPermission);

        // 清除该知识库相关用户的权限缓存
        evictCollectionPermissionCache(collectionId);
        permissionCacheService.evictUserPermissionCache(request.getUserId());

        log.info("User permission added to collection: collectionId={}, userId={}, operator={}",
                collectionId, request.getUserId(), operatorId);
    }

    @Transactional
    public void removeUserPermission(String collectionId, String targetUserId) {
        Collection collection = collectionMapper.selectById(collectionId);
        if (collection == null) {
            throw new BizException(ErrorCode.COLLECTION_NOT_FOUND);
        }

        String operatorId = TenantContext.getUserId();
        User operator = userMapper.selectById(operatorId);
        if (operator == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        if (!canModifyCollectionPermission(operator, collection)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "无权修改此知识库权限");
        }

        List<CollectionUserPermission> userPermissions = collectionUserPermissionMapper
                .selectByCollectionIdAndUserId(collectionId, targetUserId);
        if (userPermissions == null || userPermissions.isEmpty()) {
            throw new BizException(ErrorCode.USER_PERMISSION_NOT_FOUND);
        }

        for (CollectionUserPermission up : userPermissions) {
            collectionUserPermissionMapper.deleteById(up.getId());
        }

        // 清除该知识库相关用户的权限缓存
        evictCollectionPermissionCache(collectionId);
        permissionCacheService.evictUserPermissionCache(targetUserId);

        log.info("User permission removed from collection: collectionId={}, userId={}, operator={}",
                collectionId, targetUserId, operatorId);
    }

    public boolean canModifyCollectionPermission(User user, Collection collection) {
        if (user.getRole() == UserRoleEnum.SYSTEM_ADMIN) {
            return true;
        }

        if (user.getId().equals(collection.getOwnerId())) {
            return true;
        }

        if (user.getRole() == UserRoleEnum.DEPT_ADMIN) {
            Set<String> userDeptIds = departmentService.getUserDepartmentIds(user.getId());
            return collection.getDepartmentId() != null && userDeptIds.contains(collection.getDepartmentId());
        }

        return false;
    }

    /**
     * 清除知识库权限变更影响的用户权限缓存。
     * 包括：直接用户权限列表中的用户、知识库所属部门的用户。
     */
    private void evictCollectionPermissionCache(String collectionId) {
        try {
            // 清除直接被授权的用户的缓存
            List<CollectionUserPermission> userPermissions = collectionUserPermissionMapper.selectByCollectionId(collectionId);
            for (CollectionUserPermission up : userPermissions) {
                permissionCacheService.evictUserPermissionCache(up.getUserId());
            }
        } catch (Exception e) {
            // 缓存清除失败不应阻断业务操作，降级清除所有
            log.warn("Failed to evict collection permission cache for specific users, evicting all: collectionId={}", collectionId, e);
            permissionCacheService.evictAllUserPermissionCache();
        }
    }

    public boolean canManageCollection(User user, Collection collection) {
        return canModifyCollectionPermission(user, collection);
    }

    /**
     * 获取并校验操作者用户，用于controller层权限校验
     */
    public User getOperatorWithCheck(String userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.AUTHENTICATION_FAILED, "用户身份未识别，请重新登录");
        }
        User operator = userMapper.selectById(userId);
        if (operator == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return operator;
    }

    /**
     * 获取并校验知识库，用于controller层权限校验
     */
    public Collection getCollectionWithCheck(String collectionId) {
        Collection collection = collectionMapper.selectById(collectionId);
        if (collection == null) {
            throw new BizException(ErrorCode.COLLECTION_NOT_FOUND);
        }
        return collection;
    }

    public void checkPermission(String collectionId, CollectionPermissionLevelEnums requiredLevel, User user) {
        Collection collection = collectionMapper.selectById(collectionId);
        if (collection == null) {
            throw new BizException(ErrorCode.COLLECTION_NOT_FOUND);
        }

        CollectionAccessCheckResult accessResult = checkCollectionAccess(user.getId(), collectionId);

        if (!accessResult.isHasAccess()) {
            throw new BizException(ErrorCode.PERMISSION_DENIED,
                    String.format("无权访问此知识库: %s", accessResult.getReason()));
        }

        CollectionPermissionLevelEnums userLevel = accessResult.getPermissionLevel();

        if (userLevel == null || requiredLevel.getLevel() > userLevel.getLevel()) {
            throw new BizException(ErrorCode.PERMISSION_DENIED,
                    String.format("需要 [%s] 权限，当前权限: [%s]",
                            requiredLevel.getDescription(),
                            userLevel != null ? userLevel.getDescription() : "无"));
        }
    }

    public void checkCollectionManagePermission(String collectionId, User user) {
        checkPermission(collectionId, CollectionPermissionLevelEnums.MANAGE, user);
    }

    @lombok.Data
    @lombok.Builder
    public static class CollectionAccessCheckResult {
        private boolean hasAccess;
        private String reason;
        private CollectionPermissionLevelEnums permissionLevel;

        public static CollectionAccessCheckResult allowed(String reason, CollectionPermissionLevelEnums level) {
            return CollectionAccessCheckResult.builder()
                    .hasAccess(true)
                    .reason(reason)
                    .permissionLevel(level)
                    .build();
        }

        public static CollectionAccessCheckResult denied(String reason) {
            return CollectionAccessCheckResult.builder()
                    .hasAccess(false)
                    .reason(reason)
                    .permissionLevel(null)
                    .build();
        }
    }
}