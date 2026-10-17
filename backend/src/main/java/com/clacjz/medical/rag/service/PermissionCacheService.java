package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.config.CacheConfig;
import com.clacjz.medical.rag.mapper.RoleMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.mapper.UserRoleMapper;
import com.clacjz.medical.rag.model.entity.Role;
import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.entity.UserRole;
import com.clacjz.medical.rag.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCacheService {

    @Lazy
    private final PermissionCacheService self;

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final UserMapper userMapper;
    private final JsonUtil jsonUtil;

    @Cacheable(value = CacheConfig.USER_PERMISSIONS_CACHE, key = "#userId")
    public Set<String> getUserPermissions(String userId) {
        log.debug("Computing user permission cache: userId={}", userId);
        Set<String> permissions = new HashSet<>();

        // 加载用户的角色权限
        List<UserRole> userRoles = userRoleMapper.selectByUserId(userId);
        for (UserRole ur : userRoles) {
            Role role = ur.getRole();
            if (role == null) {
                role = roleMapper.selectById(ur.getRoleId());
            }
            if (role != null && role.getActive()) {
                // 添加角色标识
                permissions.add("ROLE_" + role.getCode());
                // 解析并添加细粒度权限（防止格式错误的JSON导致整个权限加载崩溃）
                if (role.getPermissions() != null && !role.getPermissions().isEmpty()) {
                    try {
                        List<String> permList = jsonUtil.fromJsonList(role.getPermissions(), String.class);
                        if (permList != null) {
                            permissions.addAll(permList);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse permissions JSON for role: roleId={}, code={}", role.getId(), role.getCode(), e);
                    }
                }
            }
        }

        // 同时加载User实体上的主角色（null-safe：使用getSafeRoleName防止role为null时NPE）
        User user = userMapper.selectById(userId);
        if (user != null && user.getRole() != null) {
            // 仅当User.role在UserRole表中没有对应记录时才添加，避免重复ROLE_条目
            String roleCode = user.getRole().name();
            boolean alreadyAdded = permissions.stream()
                    .anyMatch(p -> p.equals("ROLE_" + roleCode));
            if (!alreadyAdded) {
                permissions.add("ROLE_" + roleCode);
            }
        }

        log.debug("Loaded {} permissions for user: {}", permissions.size(), userId);
        return permissions;
    }

    public boolean hasPermission(String userId, String permission) {
        return self.getUserPermissions(userId).contains(permission);
    }

    public boolean isSystemAdmin(String userId) {
        Set<String> perms = self.getUserPermissions(userId);
        return perms.contains("SYSTEM_ADMIN") || perms.contains("ROLE_SYSTEM_ADMIN");
    }

    @CacheEvict(value = CacheConfig.USER_PERMISSIONS_CACHE, key = "#userId")
    public void evictUserPermissionCache(String userId) {
        log.info("Evict user permission cache: userId={}", userId);
    }

    public void evictUserAllCaches(String userId) {
        self.evictUserPermissionCache(userId);
        log.info("Evict all caches for user: userId={}", userId);
    }

    /**
     * @deprecated DEPT_USERS_CACHE目前没有@Cacheable方法填充，此驱逐为空操作。
     * 保留方法以备将来添加部门用户缓存时使用。
     */
    @Deprecated
    @CacheEvict(value = CacheConfig.DEPT_USERS_CACHE, key = "#userId")
    public void evictDeptUsersCache(String userId) {
        log.info("Evict dept users cache: userId={}", userId);
    }

    @CacheEvict(value = CacheConfig.USER_PERMISSIONS_CACHE, allEntries = true)
    public void evictAllUserPermissionCache() {
        log.warn("Evict all user permission caches");
    }

    /**
     * @deprecated DOCUMENT_PERMISSION_CACHE目前没有@Cacheable方法填充，此驱逐为空操作。
     * 保留方法以备将来添加文档权限缓存时使用。
     */
    @Deprecated
    @CacheEvict(value = CacheConfig.DOCUMENT_PERMISSION_CACHE, key = "#documentId")
    public void evictDocumentPermissionCache(String documentId) {
        log.info("Evict document permission cache: documentId={}", documentId);
    }

    /**
     * @deprecated DOCUMENT_PERMISSION_CACHE目前没有@Cacheable方法填充，此驱逐为空操作。
     * 保留方法以备将来添加文档权限缓存时使用。
     */
    @Deprecated
    @CacheEvict(value = CacheConfig.DOCUMENT_PERMISSION_CACHE, allEntries = true)
    public void evictAllDocumentPermissionCache() {
        log.warn("Evict all document permission caches");
    }
}