package com.clacjz.medical.rag.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.clacjz.medical.rag.model.entity.Role;
import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.entity.UserRole;
import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import com.clacjz.medical.rag.model.enums.UserStatusEnums;
import com.clacjz.medical.rag.mapper.DepartmentMapper;
import com.clacjz.medical.rag.mapper.RoleMapper;
import com.clacjz.medical.rag.mapper.UserDepartmentMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.mapper.UserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private UserDepartmentMapper userDepartmentMapper;

    @Mock
    private DepartmentMapper departmentMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private DefaultUsersProperties defaultUsersProperties;

    @Mock
    private DefaultDepartmentProperties defaultDepartmentProperties;

    @InjectMocks
    private DataInitializer dataInitializer;

    private Role systemAdminRole;
    private Role deptAdminRole;
    private Role deptUserRole;
    private DefaultUsersProperties.DefaultUser adminUser;

    @BeforeEach
    void setUp() {
        systemAdminRole = Role.builder()
                .id("role-1")
                .code("SYSTEM_ADMIN")
                .name("系统管理员")
                .level(1)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        deptAdminRole = Role.builder()
                .id("role-2")
                .code("DEPT_ADMIN")
                .name("部门管理员")
                .level(2)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        deptUserRole = Role.builder()
                .id("role-4")
                .code("DEPT_USER")
                .name("部门普通人员")
                .level(4)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        adminUser = new DefaultUsersProperties.DefaultUser();
        adminUser.setUsername("admin");
        adminUser.setPassword("admin123");
        adminUser.setEmail("admin@example.com");
        adminUser.setDisplayName("系统管理员");
        adminUser.setRole("SYSTEM_ADMIN");
        adminUser.setEnabled(true);
    }

    @Test
    void testEnsureDefaultRolesExist_WhenRolesNotExist_ShouldCreateRoles() {
        when(roleMapper.selectCount(null)).thenReturn(0L);
        when(roleMapper.insert(any(Role.class))).thenAnswer(invocation -> {
            Role role = invocation.getArgument(0);
            role.setId("generated-id-" + System.nanoTime());
            return 1;
        });

        dataInitializer.ensureDefaultRolesExist();

        verify(roleMapper, times(4)).insert(any(Role.class));
        verify(roleMapper, times(4)).insert(argThat(role ->
            role.getCode().equals("SYSTEM_ADMIN") ||
            role.getCode().equals("DEPT_ADMIN") ||
            role.getCode().equals("DEPT_UPLOADER") ||
            role.getCode().equals("DEPT_USER")
        ));
    }

    @Test
    void testEnsureDefaultRolesExist_WhenRolesExist_ShouldSkipCreation() {
        when(roleMapper.selectCount(null)).thenReturn(4L);

        dataInitializer.ensureDefaultRolesExist();

        verify(roleMapper, never()).insert(any(Role.class));
    }

    @Test
    void testInitializeDefaultUsers_WhenUsersConfigured_ShouldCreateUsers() {
        when(roleMapper.selectList(null)).thenReturn(Arrays.asList(systemAdminRole, deptAdminRole, deptUserRole));
        when(defaultUsersProperties.getUsers()).thenReturn(Collections.singletonList(adminUser));
        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("admin123")).thenReturn("encoded_password");

        User savedUser = User.builder()
                .id("user-1")
                .username("admin")
                .email("admin@example.com")
                .passwordHash("encoded_password")
                .displayName("系统管理员")
                .role(UserRoleEnum.SYSTEM_ADMIN)
                .status(UserStatusEnums.ACTIVE)
                .build();
        when(userMapper.insert(any(User.class))).thenReturn(1);

        dataInitializer.initializeDefaultUsers();

        verify(userMapper, times(1)).insert(any(User.class));
        verify(userRoleMapper, times(1)).insert(any());
    }

    @Test
    void testInitializeDefaultUsers_WhenUserAlreadyExists_ShouldSkip() {
        when(defaultUsersProperties.getUsers()).thenReturn(Collections.singletonList(adminUser));
        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        dataInitializer.initializeDefaultUsers();

        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void testInitializeDefaultUsers_WhenNoUsersConfigured_ShouldNotCreate() {
        when(defaultUsersProperties.getUsers()).thenReturn(Collections.emptyList());

        dataInitializer.initializeDefaultUsers();

        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void testInitializeDefaultUsers_WhenInvalidRole_ShouldDefaultToDeptUser() {
        DefaultUsersProperties.DefaultUser invalidUser = new DefaultUsersProperties.DefaultUser();
        invalidUser.setUsername("testuser");
        invalidUser.setPassword("password");
        invalidUser.setEmail("test@example.com");
        invalidUser.setDisplayName("Test User");
        invalidUser.setRole("INVALID_ROLE");
        invalidUser.setEnabled(true);

        when(roleMapper.selectList(null)).thenReturn(Arrays.asList(systemAdminRole, deptAdminRole, deptUserRole));
        when(defaultUsersProperties.getUsers()).thenReturn(Collections.singletonList(invalidUser));
        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("password")).thenReturn("encoded_password");

        User savedUser = User.builder()
                .id("user-2")
                .username("testuser")
                .role(UserRoleEnum.DEPT_USER)
                .build();
        when(userMapper.insert(any(User.class))).thenReturn(1);

        dataInitializer.initializeDefaultUsers();

        verify(userMapper).insert(argThat(user -> user.getRole() == UserRoleEnum.DEPT_USER));
    }

    @Test
    void testInitializeDefaultUsers_WhenDisabledUser_ShouldSkip() {
        DefaultUsersProperties.DefaultUser disabledUser = new DefaultUsersProperties.DefaultUser();
        disabledUser.setUsername("disabled");
        disabledUser.setEnabled(false);

        when(defaultUsersProperties.getUsers()).thenReturn(Collections.singletonList(disabledUser));

        dataInitializer.initializeDefaultUsers();

        verify(userMapper, never()).selectCount(any(QueryWrapper.class));
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void testEnsureUserRoleAssociations_WhenUsersWithoutRoles_ShouldCreateAssociations() {
        User userWithoutRole = User.builder()
                .id("user-1")
                .username("testuser")
                .role(UserRoleEnum.SYSTEM_ADMIN)
                .userRoles(new java.util.HashSet<>())
                .build();

        when(roleMapper.selectList(null)).thenReturn(Arrays.asList(systemAdminRole, deptAdminRole));
        when(userMapper.selectList(null)).thenReturn(Collections.singletonList(userWithoutRole));
        when(userRoleMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        dataInitializer.ensureUserRoleAssociations();

        verify(userRoleMapper, times(1)).insert(any());
    }

    @Test
    void testEnsureUserRoleAssociations_WhenAllUsersHaveRoles_ShouldSkip() {
        UserRole existingUserRole = UserRole.builder()
                .id("ur-existing")
                .userId("user-1")
                .roleId("role-1")
                .isPrimary(true)
                .build();

        User userWithRole = User.builder()
                .id("user-1")
                .username("testuser")
                .role(UserRoleEnum.SYSTEM_ADMIN)
                .userRoles(new java.util.HashSet<>(Collections.singleton(existingUserRole)))
                .build();

        when(roleMapper.selectList(null)).thenReturn(Arrays.asList(systemAdminRole));
        when(userMapper.selectList(null)).thenReturn(Collections.singletonList(userWithRole));

        dataInitializer.ensureUserRoleAssociations();

        verify(userRoleMapper, never()).insert(any());
    }

    @Test
    void testRun_WhenDisabled_ShouldNotInitialize() {
        when(defaultUsersProperties.isEnabled()).thenReturn(false);

        dataInitializer.run();

        verify(roleMapper, never()).selectCount(null);
        verify(userMapper, never()).selectCount(null);
    }
}
