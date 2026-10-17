package com.clacjz.medical.rag.config;

import com.clacjz.medical.rag.mapper.DepartmentMapper;
import com.clacjz.medical.rag.mapper.UserDepartmentMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.mapper.UserRoleMapper;
import com.clacjz.medical.rag.mapper.RoleMapper;
import com.clacjz.medical.rag.model.entity.Department;
import com.clacjz.medical.rag.model.entity.Role;
import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.entity.UserDepartment;
import com.clacjz.medical.rag.model.entity.UserRole;
import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import com.clacjz.medical.rag.model.enums.UserStatusEnums;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserDepartmentMapper userDepartmentMapper;
    private final DepartmentMapper departmentMapper;
    private final PasswordEncoder passwordEncoder;
    private final DefaultUsersProperties defaultUsersProperties;
    private final DefaultDepartmentProperties defaultDepartmentProperties;

    @Override
    @Transactional
    public void run(String... args) {
        try {
            log.info("Starting data initialization process...");

            boolean usersEnabled = defaultUsersProperties.isEnabled();
            boolean deptEnabled = defaultDepartmentProperties.isEnabled();

            // 两项都关闭时跳过全部初始化
            if (!usersEnabled && !deptEnabled) {
                log.info("Both default users and department initialization are disabled, skipping");
                return;
            }

            // 只要用户或部门初始化任一开启，就确保角色已存在
            if (usersEnabled || deptEnabled) {
                ensureDefaultRolesExist();
            }

            if (usersEnabled) {
                if (userMapper.selectCount(null) == 0) {
                    log.info("Initializing default users from configuration...");
                    initializeDefaultUsers();
                    log.info("Default users initialization completed successfully");
                } else {
                    log.info("Users already exist, checking for role associations...");
                    ensureUserRoleAssociations();
                }
            }

            if (deptEnabled) {
                ensureDefaultDepartmentExists();
                ensureDefaultDepartmentHasManager();
                validateDepartmentDataIntegrity();
            }

            log.info("Data initialization process completed successfully");

        } catch (Exception e) {
            log.error("Critical error during data initialization: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize data", e);
        }
    }

    @Transactional
    public void initializeDefaultUsers() {
        List<Role> roles = roleMapper.selectList(null);
        Map<String, Role> roleMap = roles.stream()
                .collect(Collectors.toMap(Role::getCode, Function.identity()));

        List<DefaultUsersProperties.DefaultUser> configuredUsers = defaultUsersProperties.getUsers();

        if (configuredUsers == null || configuredUsers.isEmpty()) {
            log.warn("No default users configured in app.default-users.users");
            return;
        }

        for (DefaultUsersProperties.DefaultUser userConfig : configuredUsers) {
            if (!userConfig.isEnabled()) {
                log.debug("Skipping disabled user: {}", userConfig.getUsername());
                continue;
            }

            try {
                QueryWrapper<User> checkWrapper = new QueryWrapper<>();
                checkWrapper.eq("username", userConfig.getUsername());
                Long count = userMapper.selectCount(checkWrapper);
                if (count > 0) {
                    log.debug("User already exists: {}", userConfig.getUsername());
                    continue;
                }

                UserRoleEnum roleEnum;
                try {
                    roleEnum = UserRoleEnum.valueOf(userConfig.getRole().toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid role '{}' for user '{}', defaulting to DEPT_USER",
                            userConfig.getRole(), userConfig.getUsername());
                    roleEnum = UserRoleEnum.DEPT_USER;
                }

                User user = User.builder()
                        .username(userConfig.getUsername())
                        .email(userConfig.getEmail())
                        .passwordHash(passwordEncoder.encode(userConfig.getPassword()))
                        .displayName(userConfig.getDisplayName())
                        .role(roleEnum)
                        .status(UserStatusEnums.ACTIVE)
                        .build();
                userMapper.insert(user);

                Role roleEntity = roleMap.get(userConfig.getRole().toUpperCase());
                if (roleEntity != null) {
                    UserRole userRole = UserRole.builder()
                            .userId(user.getId())
                            .roleId(roleEntity.getId())
                            .isPrimary(true)
                            .build();
                    userRoleMapper.insert(userRole);
                    log.info("Created user: {}/{} with role: {}",
                            userConfig.getUsername(), "******", userConfig.getRole());
                } else {
                    log.warn("Role not found for user {}: {}", userConfig.getUsername(), userConfig.getRole());
                }

            } catch (DataIntegrityViolationException e) {
                log.warn("User already exists (concurrent creation): {}", userConfig.getUsername());
            } catch (Exception e) {
                log.error("Failed to create user: {} - {}", userConfig.getUsername(), e.getMessage());
            }
        }
    }

    @Transactional
    public void ensureDefaultRolesExist() {
        if (roleMapper.selectCount(null) > 0) {
            log.debug("Default roles already exist, skipping creation");
            return;
        }

        log.info("Creating default roles...");

        try {
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

            log.info("Default roles created successfully: SYSTEM_ADMIN, DEPT_ADMIN, DEPT_UPLOADER, DEPT_USER");

        } catch (DataIntegrityViolationException e) {
            log.warn("Roles were created concurrently by another instance, skipping");
        } catch (Exception e) {
            log.error("Failed to create default roles: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize default roles", e);
        }
    }

    @Transactional
    public void ensureDefaultDepartmentExists() {
        if (!defaultDepartmentProperties.isEnabled()) {
            log.info("Default department initialization is disabled");
            return;
        }

        QueryWrapper<Department> wrapper = new QueryWrapper<>();
        wrapper.eq("is_default", true);
        Department existingDefaultDept = departmentMapper.selectOne(wrapper);
        if (existingDefaultDept != null) {
            log.debug("Default department already exists, skipping creation");
            ensureDefaultDepartmentHasManager(existingDefaultDept);
            return;
        }

        wrapper = new QueryWrapper<>();
        wrapper.eq("code", defaultDepartmentProperties.getCode());
        Department existingDept = departmentMapper.selectOne(wrapper);
        if (existingDept != null) {
            log.warn("Department with code {} already exists but is not marked as default",
                    defaultDepartmentProperties.getCode());
            existingDept.setIsDefault(true);
            existingDept.setParentId(null);
            departmentMapper.updateById(existingDept);
            ensureDefaultDepartmentHasManager(existingDept);
            return;
        }

        // 创建部门前先检查系统中是否已有用户
        if (userMapper.selectCount(null) == 0) {
            log.info("No users found in system. Skipping default department creation. " +
                    "Department will be created after users are initialized.");
            return;
        }

        try {
            String managerId = getDefaultDepartmentManager();
            if (managerId == null) {
                log.warn("No SYSTEM_ADMIN user found. Skipping default department creation. " +
                        "Please create at least one SYSTEM_ADMIN user first, then restart.");
                return;
            }

            Department defaultDept = Department.builder()
                    .code(defaultDepartmentProperties.getCode())
                    .name(defaultDepartmentProperties.getName())
                    .description(defaultDepartmentProperties.getDescription())
                    .parentId(null)
                    .managerId(managerId)
                    .status("active")
                    .isDefault(true)
                    .build();

            departmentMapper.insert(defaultDept);
            log.info("Default department created: id={}, code={}, name={}, managerId={}",
                    defaultDept.getId(), defaultDept.getCode(), defaultDept.getName(), managerId);

        } catch (DataIntegrityViolationException e) {
            log.warn("Default department was created concurrently by another instance, skipping");
        } catch (Exception e) {
            log.error("Failed to create default department: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize default department", e);
        }
    }

    private String getDefaultDepartmentManager() {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("role", UserRoleEnum.SYSTEM_ADMIN);
        List<User> adminUsers = userMapper.selectList(wrapper);

        if (!adminUsers.isEmpty()) {
            User adminUser = adminUsers.get(0);
            log.debug("Found SYSTEM_ADMIN user: {} for default department manager",
                    adminUser.getUsername());
            return adminUser.getId();
        }

        log.debug("No SYSTEM_ADMIN user found. Default department manager will not be assigned.");
        return null;
    }

    private void ensureDefaultDepartmentHasManager(Department department) {
        if (department.getManagerId() != null && !department.getManagerId().isEmpty()) {
            return;
        }

        String managerId = getDefaultDepartmentManager();
        if (managerId != null) {
            department.setManagerId(managerId);
            departmentMapper.updateById(department);
            log.info("Default department {} updated with manager: {}",
                    department.getCode(), managerId);
        } else {
            log.error("Cannot update department {}: No SYSTEM_ADMIN user available", department.getCode());
        }
    }

    private void ensureDefaultDepartmentHasManager() {
        QueryWrapper<Department> wrapper = new QueryWrapper<>();
        wrapper.eq("is_default", true);
        Department defaultDept = departmentMapper.selectOne(wrapper);
        if (defaultDept != null) {
            ensureDefaultDepartmentHasManager(defaultDept);
            ensureUsersAssociatedWithDefaultDepartment(defaultDept);
        }
    }

    private void ensureUsersAssociatedWithDefaultDepartment(Department department) {
        List<User> allUsers = userMapper.selectList(null);
        for (User user : allUsers) {
            QueryWrapper<UserDepartment> checkWrapper = new QueryWrapper<>();
            checkWrapper.eq("user_id", user.getId());
            checkWrapper.eq("department_id", department.getId());
            Long count = userDepartmentMapper.selectCount(checkWrapper);
            if (count == 0) {
                UserDepartment userDept = UserDepartment.builder()
                        .userId(user.getId())
                        .departmentId(department.getId())
                        .isPrimary(true)
                        .build();
                userDepartmentMapper.insert(userDept);
                log.info("Associated user {} with default department: {}", user.getUsername(), department.getName());
            }
        }
    }

    @Transactional(readOnly = true)
    public void validateDepartmentDataIntegrity() {
        log.info("Validating department data integrity...");

        List<Department> departments = departmentMapper.selectList(null);
        List<User> users = userMapper.selectList(null);
        Set<String> userIds = users.stream()
                .map(User::getId)
                .collect(Collectors.toSet());

        List<String> errors = new ArrayList<>();

        for (Department dept : departments) {
            if (dept.getManagerId() == null || dept.getManagerId().isEmpty()) {
                errors.add(String.format("Department '%s' (id=%s) has no manager",
                        dept.getName(), dept.getId()));
            } else if (!userIds.contains(dept.getManagerId())) {
                errors.add(String.format("Department '%s' (id=%s) has invalid manager_id: %s (user not found)",
                        dept.getName(), dept.getId(), dept.getManagerId()));
            }

            if (dept.getParentId() != null && !dept.getParentId().isEmpty()) {
                boolean parentExists = departments.stream()
                        .anyMatch(d -> d.getId().equals(dept.getParentId()));
                if (!parentExists) {
                    errors.add(String.format("Department '%s' (id=%s) has invalid parent_id: %s (parent not found)",
                            dept.getName(), dept.getId(), dept.getParentId()));
                }
            }

            if (Boolean.TRUE.equals(dept.getIsDefault())) {
                if (dept.getParentId() != null && !dept.getParentId().isEmpty()) {
                    errors.add(String.format("Default department '%s' (id=%s) must not have a parent",
                            dept.getName(), dept.getId()));
                }
            }
        }

        if (!errors.isEmpty()) {
            log.error("Department data integrity validation failed:");
            errors.forEach(error -> log.error("  - {}", error));
        } else {
            log.info("Department data integrity validation passed: all {} departments are valid", departments.size());
        }
    }

    @Transactional
    public void ensureUserRoleAssociations() {
        List<Role> roles = roleMapper.selectList(null);
        Map<String, Role> roleMap = roles.stream()
                .collect(Collectors.toMap(Role::getCode, Function.identity()));

        List<User> users = userMapper.selectList(null);
        List<User> usersWithoutRoles = users.stream()
                .filter(user -> user.getUserRoles() == null || user.getUserRoles().isEmpty())
                .collect(Collectors.toList());

        if (usersWithoutRoles.isEmpty()) {
            log.debug("All users have role associations");
            return;
        }

        log.info("Found {} users without role associations, creating associations...", usersWithoutRoles.size());

        for (User user : usersWithoutRoles) {
            try {
                String roleCode = user.getRole().name();
                Role roleEntity = roleMap.get(roleCode);

                if (roleEntity != null) {
                    QueryWrapper<UserRole> checkWrapper = new QueryWrapper<>();
                    checkWrapper.eq("user_id", user.getId());
                    checkWrapper.eq("role_id", roleEntity.getId());
                    Long count = userRoleMapper.selectCount(checkWrapper);
                    if (count == 0) {
                        UserRole userRole = UserRole.builder()
                                .userId(user.getId())
                                .roleId(roleEntity.getId())
                                .isPrimary(true)
                                .build();
                        userRoleMapper.insert(userRole);
                        log.info("Created role association for existing user: {} -> {}",
                                user.getUsername(), roleCode);
                    }
                } else {
                    log.warn("Role not found for user {}: {}", user.getUsername(), roleCode);
                }
            } catch (Exception e) {
                log.error("Failed to create role association for user {}: {}", user.getUsername(), e.getMessage());
            }
        }
    }
}