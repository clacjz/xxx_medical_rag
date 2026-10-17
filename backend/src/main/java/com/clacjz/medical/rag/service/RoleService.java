package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.mapper.RoleMapper;
import com.clacjz.medical.rag.mapper.UserRoleMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.model.dto.AssignRoleRequest;
import com.clacjz.medical.rag.model.dto.CreateRoleRequest;
import com.clacjz.medical.rag.model.dto.RoleDTO;
import com.clacjz.medical.rag.model.dto.RolePermissionDTO;
import com.clacjz.medical.rag.model.dto.UpdateRoleRequest;
import com.clacjz.medical.rag.model.entity.Role;
import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.entity.UserRole;
import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private static final Set<String> SYSTEM_ROLES = Set.of(
            "SYSTEM_ADMIN",
            "DEPT_ADMIN",
            "DEPT_UPLOADER",
            "DEPT_USER"
    );

    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserMapper userMapper;
    private final PermissionCacheService permissionCacheService;

    @Transactional(readOnly = true)
    public List<RoleDTO> getAllRoles() {
        List<Role> roles = roleMapper.selectList(null);
        Map<String, Long> userCountMap = loadUserCountMap();
        return roles.stream()
                .map(role -> RoleDTO.fromEntityWithUserCount(role,
                        userCountMap.getOrDefault(role.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RoleDTO> getAllActiveRoles() {
        List<Role> roles = roleMapper.selectAllActiveOrderByLevel();
        Map<String, Long> userCountMap = loadUserCountMap();
        return roles.stream()
                .map(role -> RoleDTO.fromEntityWithUserCount(role,
                        userCountMap.getOrDefault(role.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RoleDTO getRoleById(String roleId) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }
        return RoleDTO.fromEntityWithUserCount(role, roleMapper.countUsersByRoleId(role.getId()));
    }

    @Transactional(readOnly = true)
    public RoleDTO getRoleByCode(String code) {
        Role role = roleMapper.selectByCode(code);
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }
        return RoleDTO.fromEntityWithUserCount(role, roleMapper.countUsersByRoleId(role.getId()));
    }

    @Transactional
    public RoleDTO createRole(CreateRoleRequest request) {
        if (roleMapper.existsByCode(request.getCode())) {
            throw new BizException(ErrorCode.ROLE_ALREADY_EXISTS, "角色代码已存在: " + request.getCode());
        }

        Role role = Role.builder()
                .code(request.getCode())
                .name(request.getName())
                .level(request.getLevel() != null ? request.getLevel() : 4)
                .permissions(request.getPermissions())
                .description(request.getDescription())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        roleMapper.insert(role);
        log.info("Role created: id={}, code={}", role.getId(), role.getCode());

        return RoleDTO.fromEntityWithUserCount(role, 0);
    }

    @Transactional
    public RoleDTO updateRole(String roleId, UpdateRoleRequest request) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }

        // 系统内置角色仅允许修改description，其余字段禁止修改
        if (SYSTEM_ROLES.contains(role.getCode())) {
            if (request.getName() != null || request.getLevel() != null
                    || request.getPermissions() != null || request.getActive() != null) {
                throw new BizException(ErrorCode.CANNOT_MODIFY_SYSTEM_ROLE_PERMISSIONS,
                        "系统内置角色的核心属性(name/level/permissions/active)不可修改: " + role.getCode());
            }
        }

        if (request.getName() != null) {
            role.setName(request.getName());
        }

        if (request.getLevel() != null) {
            role.setLevel(request.getLevel());
        }

        if (request.getPermissions() != null) {
            role.setPermissions(request.getPermissions());
        }

        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }

        if (request.getActive() != null) {
            role.setActive(request.getActive());
        }

        roleMapper.updateById(role);
        log.info("Role updated: id={}, code={}", role.getId(), role.getCode());

        // 角色变更后清除所有持有该角色的用户权限缓存
        evictCacheForUsersWithRole(roleId);

        return RoleDTO.fromEntityWithUserCount(role, roleMapper.countUsersByRoleId(role.getId()));
    }

    @Transactional
    public void deleteRole(String roleId) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }

        if (SYSTEM_ROLES.contains(role.getCode())) {
            throw new BizException(ErrorCode.CANNOT_DELETE_SYSTEM_ROLE, "系统内置角色无法删除: " + role.getCode());
        }

        long userCount = roleMapper.countUsersByRoleId(roleId);
        if (userCount > 0) {
            throw new BizException(ErrorCode.ROLE_HAS_USERS, "角色下存在用户，无法删除，用户数: " + userCount);
        }

        // 角色删除前先清除所有曾持有该角色的用户权限缓存
        // 必须在deleteById之前执行，因为deleteById可能触发级联删除user_role记录
        evictCacheForUsersWithRole(roleId);

        roleMapper.deleteById(roleId);
        log.info("Role deleted: id={}, code={}", roleId, role.getCode());
    }

    @Transactional
    public void assignRoleToUser(String userId, String roleId, boolean isPrimary) {
        if (userMapper.selectById(userId) == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }

        if (!role.getActive()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "角色已被禁用: " + role.getName());
        }

        if (userRoleMapper.existsByUserIdAndRoleId(userId, roleId)) {
            throw new BizException(ErrorCode.USER_ROLE_ALREADY_EXISTS);
        }

        if (isPrimary) {
            UserRole existingPrimary = userRoleMapper.selectByUserIdAndIsPrimaryTrue(userId);
            if (existingPrimary != null) {
                existingPrimary.setIsPrimary(false);
                userRoleMapper.updateById(existingPrimary);
            }
        }

        UserRole userRole = UserRole.builder()
                .userId(userId)
                .roleId(roleId)
                .isPrimary(isPrimary)
                .build();

        userRoleMapper.insert(userRole);

        // 同步User实体的role字段：如果新分配的角色级别更高，更新User.role
        syncUserRoleField(userId);

        // 清除用户权限缓存
        permissionCacheService.evictUserPermissionCache(userId);

        log.info("Role assigned to user: userId={}, roleId={}, isPrimary={}", userId, roleId, isPrimary);
    }

    @Transactional
    public void assignRoleToUser(String userId, AssignRoleRequest request) {
        boolean isPrimary = request.getIsPrimary() != null && request.getIsPrimary();
        assignRoleToUser(userId, request.getRoleId(), isPrimary);
    }

    @Transactional
    public void removeRoleFromUser(String userId, String roleId) {
        if (userMapper.selectById(userId) == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        if (roleMapper.selectById(roleId) == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }

        UserRole userRole = userRoleMapper.selectByUserIdAndRoleId(userId, roleId);
        if (userRole == null) {
            throw new BizException(ErrorCode.USER_ROLE_NOT_FOUND);
        }

        if (userRole.getIsPrimary()) {
            long userRoleCount = userRoleMapper.countByUserId(userId);
            if (userRoleCount <= 1) {
                throw new BizException(ErrorCode.LAST_PRIMARY_ROLE_CANNOT_DELETE);
            }
            List<UserRole> userRoles = userRoleMapper.selectByUserId(userId);
            UserRole nextPrimary = userRoles.stream()
                    .filter(ur -> !ur.getId().equals(userRole.getId()))
                    .findFirst()
                    .orElse(null);
            if (nextPrimary != null) {
                nextPrimary.setIsPrimary(true);
                userRoleMapper.updateById(nextPrimary);
            }
        }

        userRoleMapper.deleteById(userRole.getId());

        // 同步User实体的role字段
        syncUserRoleField(userId);

        // 清除用户权限缓存
        permissionCacheService.evictUserPermissionCache(userId);

        log.info("Role removed from user: userId={}, roleId={}", userId, roleId);
    }

    @Transactional(readOnly = true)
    public List<RoleDTO> getUserRoles(String userId) {
        if (userMapper.selectById(userId) == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        List<UserRole> userRoles = userRoleMapper.selectByUserId(userId);
        return userRoles.stream()
                .map(ur -> {
                    Role role = ur.getRole();
                    if (role == null) {
                        role = roleMapper.selectById(ur.getRoleId());
                    }
                    if (role == null) {
                        return null;
                    }
                    RoleDTO dto = RoleDTO.fromEntity(role);
                    dto.setUserCount(roleMapper.countUsersByRoleId(role.getId()));
                    return dto;
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RoleDTO> getRolesByLevel(Integer level) {
        return roleMapper.selectByLevel(level).stream()
                .map(RoleDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean hasRole(String userId, String roleCode) {
        List<UserRole> userRoles = userRoleMapper.selectByUserId(userId);
        return userRoles.stream()
                .anyMatch(ur -> {
                    Role role = ur.getRole();
                    if (role == null) {
                        role = roleMapper.selectById(ur.getRoleId());
                    }
                    return role != null && role.getCode().equals(roleCode);
                });
    }

    @Transactional(readOnly = true)
    public boolean hasRoleLevel(String userId, int level) {
        List<UserRole> userRoles = userRoleMapper.selectByUserId(userId);
        return userRoles.stream()
                .anyMatch(ur -> {
                    Role role = ur.getRole();
                    if (role == null) {
                        role = roleMapper.selectById(ur.getRoleId());
                    }
                    return role != null && role.getLevel() <= level;
                });
    }

    @Transactional
    public RolePermissionDTO updateRolePermissions(String roleId, String permissions) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }

        if (SYSTEM_ROLES.contains(role.getCode())) {
            throw new BizException(ErrorCode.CANNOT_MODIFY_SYSTEM_ROLE_PERMISSIONS,
                    "系统内置角色的权限不可修改: " + role.getCode());
        }

        role.setPermissions(permissions);
        roleMapper.updateById(role);
        log.info("Role permissions updated: roleId={}, code={}", roleId, role.getCode());

        // 权限变更后清除所有持有该角色的用户权限缓存
        evictCacheForUsersWithRole(roleId);

        return RolePermissionDTO.fromEntityWithUserCount(role, roleMapper.countUsersByRoleId(roleId));
    }

    @Transactional(readOnly = true)
    public RolePermissionDTO getRolePermissions(String roleId) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }

        return RolePermissionDTO.fromEntityWithUserCount(role, roleMapper.countUsersByRoleId(roleId));
    }

    @Transactional(readOnly = true)
    public List<RolePermissionDTO> getAllRolePermissions() {
        List<Role> roles = roleMapper.selectList(null);
        Map<String, Long> userCountMap = loadUserCountMap();
        return roles.stream()
                .map(role -> RolePermissionDTO.fromEntityWithUserCount(role,
                        userCountMap.getOrDefault(role.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Transactional
    public void initializeDefaultRoles() {
        if (roleMapper.selectCount(null) > 0) {
            log.info("Default roles already initialized, skipping...");
            return;
        }

        log.info("Initializing default roles...");

        Role systemAdmin = Role.builder()
                .code("SYSTEM_ADMIN")
                .name("系统管理员")
                .level(1)
                .permissions("[\"user:*\", \"department:*\", \"document:*\", \"collection:*\", \"audit:*\", \"system:*\"]")
                .description("全系统最高权限，用户管理、部门管理、系统配置、审计日志查看")
                .active(true)
                .build();
        roleMapper.insert(systemAdmin);

        Role deptAdmin = Role.builder()
                .code("DEPT_ADMIN")
                .name("部门管理员")
                .level(2)
                .permissions("[\"department:read\", \"department:update\", \"user:read\", \"document:*\", \"collection:read\", \"audit:read\"]")
                .description("本部门全部权限，本部门用户管理、文档权限配置、本部门审计日志查看")
                .active(true)
                .build();
        roleMapper.insert(deptAdmin);

        Role deptUploader = Role.builder()
                .code("DEPT_UPLOADER")
                .name("部门文档上传者")
                .level(3)
                .permissions("[\"document:upload\", \"document:read\", \"document:delete:own\", \"collection:read\", \"chat:ask\"]")
                .description("本部门普通权限，文档上传、文档权限配置（仅自己上传的）、文档删除（仅自己上传的）")
                .active(true)
                .build();
        roleMapper.insert(deptUploader);

        Role deptUser = Role.builder()
                .code("DEPT_USER")
                .name("部门普通人员")
                .level(4)
                .permissions("[\"document:read\", \"collection:read\", \"chat:ask\", \"conversation:read\"]")
                .description("文档查看（根据文档权限配置访问）、智能问答、对话历史查看、个人设置")
                .active(true)
                .build();
        roleMapper.insert(deptUser);

        log.info("Default roles initialized: SYSTEM_ADMIN(id={}), DEPT_ADMIN(id={}), DEPT_UPLOADER(id={}), DEPT_USER(id={})",
                systemAdmin.getId(), deptAdmin.getId(), deptUploader.getId(), deptUser.getId());
    }

    /**
     * 批量加载所有角色的用户数量，避免N+1查询。
     * 返回roleId -> userCount的映射。
     */
    private Map<String, Long> loadUserCountMap() {
        List<Map<String, Object>> rows = roleMapper.countUsersByRoleIds();
        return rows.stream().collect(Collectors.toMap(
                row -> String.valueOf(row.get("role_id")),
                row -> ((Number) row.get("cnt")).longValue()
        ));
    }

    /**
     * 清除持有指定角色的所有用户的权限缓存。
     * 在角色属性或权限变更后调用，确保缓存一致性。
     */
    private void evictCacheForUsersWithRole(String roleId) {
        try {
            List<UserRole> userRoles = userRoleMapper.selectByRoleId(roleId);
            for (UserRole ur : userRoles) {
                permissionCacheService.evictUserPermissionCache(ur.getUserId());
            }
            if (!userRoles.isEmpty()) {
                log.info("Evicted permission cache for {} users with role: roleId={}", userRoles.size(), roleId);
            }
        } catch (Exception e) {
            // 缓存清除失败不应阻断业务操作，但需记录警告
            log.warn("Failed to evict cache for users with role: roleId={}", roleId, e);
            // 降级：清除所有用户权限缓存
            permissionCacheService.evictAllUserPermissionCache();
        }
    }

    /**
     * 同步User实体的role字段与UserRole关联表。
     * 取用户所有角色中level最小的（权限最高的）作为User.role字段值。
     */
    private void syncUserRoleField(String userId) {
        try {
            User user = userMapper.selectById(userId);
            if (user == null) {
                return;
            }

            List<UserRole> userRoles = userRoleMapper.selectByUserId(userId);
            if (userRoles == null || userRoles.isEmpty()) {
                return;
            }

            // 找到level最小的角色（权限最高）
            Role highestRole = null;
            for (UserRole ur : userRoles) {
                Role role = ur.getRole();
                if (role == null) {
                    role = roleMapper.selectById(ur.getRoleId());
                }
                if (role != null && (highestRole == null || role.getLevel() < highestRole.getLevel())) {
                    highestRole = role;
                }
            }

            if (highestRole != null) {
                try {
                    UserRoleEnum newRole = UserRoleEnum.valueOf(highestRole.getCode());
                    if (user.getRole() != newRole) {
                        user.setRole(newRole);
                        userMapper.updateById(user);
                        log.info("Synced user role field: userId={}, newRole={}", userId, newRole);
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Cannot sync user role field: unknown role code {} for userId={}", highestRole.getCode(), userId);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to sync user role field for userId={}", userId, e);
        }
    }
}