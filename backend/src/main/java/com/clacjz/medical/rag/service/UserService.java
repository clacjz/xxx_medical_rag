package com.clacjz.medical.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clacjz.medical.rag.config.DefaultDepartmentProperties;
import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.mapper.CollectionUserPermissionMapper;
import com.clacjz.medical.rag.mapper.DepartmentMapper;
import com.clacjz.medical.rag.mapper.DocumentUserPermissionMapper;
import com.clacjz.medical.rag.mapper.UserDepartmentMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.mapper.UserRoleMapper;
import com.clacjz.medical.rag.model.dto.UpdateUserRequest;
import com.clacjz.medical.rag.model.dto.UserDTO;
import com.clacjz.medical.rag.model.entity.Department;
import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.entity.UserDepartment;
import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import com.clacjz.medical.rag.model.enums.UserStatusEnums;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final UserDepartmentMapper userDepartmentMapper;
    private final UserRoleMapper userRoleMapper;
    private final CollectionUserPermissionMapper collectionUserPermissionMapper;
    private final DocumentUserPermissionMapper documentUserPermissionMapper;
    private final PasswordEncoder passwordEncoder;
    private final DefaultDepartmentProperties defaultDepartmentProperties;
    private final DepartmentService departmentService;
    private final TokenBlacklistService tokenBlacklistService;
    private final PermissionCacheService permissionCacheService;

    private static final String PASSWORD_COMPLEXITY_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$";
    private static final int PASSWORD_MIN_LENGTH = 8;

    @Transactional(readOnly = true)
    public IPage<UserDTO> getUserList(int page, int pageSize, UserRoleEnum role, UserStatusEnums status) {
        Page<User> mpPage = new Page<>(page + 1, pageSize);

        QueryWrapper<User> wrapper = new QueryWrapper<>();
        if (role != null) {
            wrapper.eq("role", role);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("created_at");

        IPage<User> userPage = userMapper.selectPage(mpPage, wrapper);

        return userPage.convert(UserDTO::fromUser);
    }

    @Transactional(readOnly = true)
    public UserDTO getUserById(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return UserDTO.fromUser(user);
    }

    /**
     * 判断给定的userId是否为当前登录用户本人（供SpEL @PreAuthorize使用）。
     * 由于UserDetails.getUsername()返回的是用户名而非UUID，
     * 需要通过username查找对应的userId进行比较。
     */
    @Transactional(readOnly = true)
    public boolean isSelf(String userId, String username) {
        if (userId == null || username == null) {
            return false;
        }
        User user = userMapper.selectByUsername(username);
        return user != null && userId.equals(user.getId());
    }

    @Transactional
    public UserDTO createUser(String username, String password, String email, String roleStr) {
        // 密码复杂度校验：与注册接口保持一致
        validatePasswordComplexity(password);

        QueryWrapper<User> checkWrapper = new QueryWrapper<>();
        checkWrapper.eq("username", username);
        Long count = userMapper.selectCount(checkWrapper);
        if (count > 0) {
            throw new BizException(ErrorCode.USER_ALREADY_EXISTS, "用户名已存在");
        }

        checkWrapper = new QueryWrapper<>();
        checkWrapper.eq("email", email);
        count = userMapper.selectCount(checkWrapper);
        if (count > 0) {
            throw new BizException(ErrorCode.USER_ALREADY_EXISTS, "邮箱已被使用");
        }

        UserRoleEnum role = UserRoleEnum.DEPT_USER;
        if (roleStr != null) {
            try {
                role = UserRoleEnum.valueOf(roleStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "无效的角色: " + roleStr);
            }
        }

        User user = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .email(email)
                .role(role)
                .status(UserStatusEnums.ACTIVE)
                .build();

        userMapper.insert(user);
        log.info("User {} created successfully", username);

        if (Boolean.TRUE.equals(defaultDepartmentProperties.isEnabled()) &&
                Boolean.TRUE.equals(defaultDepartmentProperties.isAutoAssignNewUsers())) {
            autoAssignUserToDefaultDepartment(user);
        }

        return UserDTO.fromUser(user);
    }

    private void autoAssignUserToDefaultDepartment(User user) {
        try {
            QueryWrapper<Department> wrapper = new QueryWrapper<>();
            wrapper.eq("is_default", true);
            Department defaultDept = departmentMapper.selectOne(wrapper);
            if (defaultDept == null) {
                log.debug("No default department configured, skipping auto-assignment for user: {}",
                        user.getUsername());
                return;
            }

            QueryWrapper<UserDepartment> checkWrapper = new QueryWrapper<>();
            checkWrapper.eq("user_id", user.getId());
            checkWrapper.eq("department_id", defaultDept.getId());
            Long count = userDepartmentMapper.selectCount(checkWrapper);
            if (count > 0) {
                log.debug("User {} already assigned to default department",
                        user.getUsername());
                return;
            }

            UserDepartment userDepartment = UserDepartment.builder()
                    .userId(user.getId())
                    .departmentId(defaultDept.getId())
                    .isPrimary(true)
                    .position("成员")
                    .build();

            userDepartmentMapper.insert(userDepartment);
            log.info("User {} automatically assigned to default department: {}",
                    user.getUsername(), defaultDept.getName());
        } catch (Exception e) {
            log.warn("Failed to auto-assign user {} to default department: {}",
                    user.getUsername(), e.getMessage());
        }
    }

    @Transactional
    public void updatePassword(String userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        // 安全：oldPassword不可为null，防止绕过旧密码验证
        if (oldPassword == null || !passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BizException(ErrorCode.AUTHENTICATION_FAILED, "原密码错误");
        }

        // 密码复杂度校验：与注册接口保持一致（min=8 + 大小写 + 数字）
        validatePasswordComplexity(newPassword);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);

        // 密码修改后吊销所有已签发token：放在事务提交后执行，避免DB回滚时Redis已写入导致不一致
        registerPostCommitAction(() -> tokenBlacklistService.revokeAllUserTokens(userId));

        log.info("Password updated for user: {}", userId);
    }

    @Transactional
    public UserDTO updateUser(String userId, UpdateUserRequest request, UserRoleEnum operatorRole) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            QueryWrapper<User> checkWrapper = new QueryWrapper<>();
            checkWrapper.eq("email", request.getEmail());
            Long count = userMapper.selectCount(checkWrapper);
            if (count > 0) {
                throw new BizException(ErrorCode.USER_ALREADY_EXISTS, "邮箱已被使用");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getRole() != null) {
            // 只有系统管理员可以修改用户角色
            if (operatorRole != UserRoleEnum.SYSTEM_ADMIN) {
                throw new BizException(ErrorCode.PERMISSION_DENIED, "无权修改用户角色");
            }
            user.setRole(request.getRole());
            // 角色变更后清除用户权限缓存，确保权限即时生效
            permissionCacheService.evictUserPermissionCache(userId);
        }

        if (request.getStatus() != null) {
            // 只有系统管理员可以修改用户状态
            if (operatorRole != UserRoleEnum.SYSTEM_ADMIN) {
                throw new BizException(ErrorCode.PERMISSION_DENIED, "无权修改用户状态");
            }
            user.setStatus(request.getStatus());
            if (request.getStatus() == UserStatusEnums.ACTIVE) {
                user.resetLoginFailCount();
            }
            // 用户被禁用或锁定时，吊销所有已签发token：放在事务提交后执行，
            // 避免DB回滚时Redis已写入导致数据不一致
            if (request.getStatus() == UserStatusEnums.DISABLED || request.getStatus() == UserStatusEnums.LOCKED) {
                registerPostCommitAction(() -> tokenBlacklistService.revokeAllUserTokens(userId));
                log.info("Token revocation scheduled for user {} due to status change: {}", userId, request.getStatus());
            }
        }

        userMapper.updateById(user);
        log.info("User {} updated successfully", userId);

        return UserDTO.fromUser(user);
    }

    @Transactional
    public void deleteUser(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        // 清理用户角色关联
        QueryWrapper<com.clacjz.medical.rag.model.entity.UserRole> roleDeleteWrapper = new QueryWrapper<>();
        roleDeleteWrapper.eq("user_id", userId);
        userRoleMapper.delete(roleDeleteWrapper);

        // 清理用户部门关联
        QueryWrapper<UserDepartment> deptDeleteWrapper = new QueryWrapper<>();
        deptDeleteWrapper.eq("user_id", userId);
        userDepartmentMapper.delete(deptDeleteWrapper);

        // 清理知识库用户权限
        collectionUserPermissionMapper.deleteByUserId(userId);

        // 清理文档用户权限
        documentUserPermissionMapper.deleteByUserId(userId);

        // 清除该用户关联的权限缓存：放在事务提交后执行，确保DB回滚时Redis不会丢失缓存
        registerPostCommitAction(() -> permissionCacheService.evictUserPermissionCache(userId));

        // 吊销已签发token：放在事务提交后执行，避免DB回滚时Redis已写入导致数据不一致
        registerPostCommitAction(() -> tokenBlacklistService.revokeAllUserTokens(user.getId()));

        userMapper.deleteById(userId);
        log.info("User {} deleted successfully with all related records cleaned up", userId);
    }

    @Transactional(readOnly = true)
    public long countUsers() {
        return userMapper.selectCount(null);
    }

    @Transactional(readOnly = true)
    public long countUsersByRole(UserRoleEnum role) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("role", role);
        return userMapper.selectCount(wrapper);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        return Optional.ofNullable(userMapper.selectOne(wrapper));
    }

    /**
     * 为用户分配部门（替换现有部门分配）
     */
    @Transactional
    public void assignDepartments(String userId, java.util.List<String> departmentIds) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        // 删除现有部门分配
        QueryWrapper<UserDepartment> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("user_id", userId);
        userDepartmentMapper.delete(deleteWrapper);

        // 添加新的部门分配
        if (departmentIds != null && !departmentIds.isEmpty()) {
            for (int i = 0; i < departmentIds.size(); i++) {
                String deptId = departmentIds.get(i);
                Department dept = departmentMapper.selectById(deptId);
                if (dept == null) {
                    throw new BizException(ErrorCode.DEPARTMENT_NOT_FOUND, "部门不存在: " + deptId);
                }

                UserDepartment userDepartment = UserDepartment.builder()
                        .userId(userId)
                        .departmentId(deptId)
                        .isPrimary(i == 0)  // 第一个设为主部门
                        .position("成员")
                        .joinedAt(java.time.LocalDateTime.now())
                        .build();
                userDepartmentMapper.insert(userDepartment);
            }
        }

        log.info("Assigned {} departments to user: {}", departmentIds != null ? departmentIds.size() : 0, userId);
    }

    /**
     * 验证DEPT_ADMIN的部门操作范围：只能管理本部门内的用户，且只能分配本部门范围内的部门。
     *
     * @param operatorUsername 操作者用户名
     * @param targetUserId     目标用户ID
     * @param departmentIds    要分配的部门ID列表
     */
    @Transactional(readOnly = true)
    public void validateDeptAdminDepartmentScope(String operatorUsername, String targetUserId,
                                                   java.util.List<String> departmentIds) {
        // 获取操作者信息
        User operator = userMapper.selectByUsername(operatorUsername);
        if (operator == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        // 获取操作者所属部门
        java.util.Set<String> operatorDeptIds = departmentService.getUserDepartmentIds(operator.getId());
        if (operatorDeptIds.isEmpty()) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "您未关联任何部门，无法执行此操作");
        }

        // 验证目标用户属于操作者的部门
        java.util.Set<String> targetDeptIds = departmentService.getUserDepartmentIds(targetUserId);
        boolean targetInOperatorDept = targetDeptIds.stream().anyMatch(operatorDeptIds::contains);
        if (!targetInOperatorDept) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "只能管理本部门内的用户");
        }

        // 验证分配的部门都在操作者的部门范围内
        if (departmentIds != null) {
            for (String deptId : departmentIds) {
                if (!operatorDeptIds.contains(deptId)) {
                    throw new BizException(ErrorCode.PERMISSION_DENIED, "只能分配本部门范围内的部门");
                }
            }
        }
    }

    /**
     * 密码复杂度校验，与RegisterRequest的@Size+@Pattern约束保持一致：
     * 至少8位，必须包含大小写字母和数字。
     * 集中校验逻辑，避免各入口（注册/创建用户/修改密码）校验标准不一致。
     */
    private void validatePasswordComplexity(String password) {
        if (password == null || password.length() < PASSWORD_MIN_LENGTH) {
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    "密码长度不能少于" + PASSWORD_MIN_LENGTH + "位");
        }
        if (!password.matches(PASSWORD_COMPLEXITY_REGEX)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    "密码必须包含大写字母、小写字母和数字");
        }
    }

    /**
     * 注册事务提交后执行的动作，确保Redis写入在DB事务成功提交后进行，
     * 避免DB回滚时Redis已写入导致数据不一致。
     */
    private void registerPostCommitAction(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        action.run();
                    } catch (Exception e) {
                        log.warn("Post-commit action failed", e);
                    }
                }
            });
        } else {
            action.run();
        }
    }
}