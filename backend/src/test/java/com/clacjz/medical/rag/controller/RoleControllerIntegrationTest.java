package com.clacjz.medical.rag.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.BaseIntegrationTest;
import com.clacjz.medical.rag.mapper.RoleMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.mapper.UserRoleMapper;
import com.clacjz.medical.rag.model.entity.Role;
import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.entity.UserRole;
import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import com.clacjz.medical.rag.model.enums.UserStatusEnums;
import com.clacjz.medical.rag.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Role Controller Integration Tests with Testcontainers")
@AutoConfigureMockMvc
class RoleControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        // Clean up tables in correct order (respect foreign keys)
        userRoleMapper.delete(new QueryWrapper<>());
        userMapper.delete(new QueryWrapper<>());
        roleMapper.delete(new QueryWrapper<>());

        // Create roles
        Role systemAdmin = Role.builder()
                .id(UUID.randomUUID().toString())
                .code("SYSTEM_ADMIN")
                .name("系统管理员")
                .level(1)
                .permissions("[\"user:*\", \"document:*\"]")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Role deptUser = Role.builder()
                .id(UUID.randomUUID().toString())
                .code("DEPT_USER")
                .name("部门普通人员")
                .level(4)
                .permissions("[\"document:read\"]")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        roleMapper.insert(systemAdmin);
        roleMapper.insert(deptUser);

        // Create users
        User adminUser = User.builder()
                .id(UUID.randomUUID().toString())
                .username("admin")
                .email("admin@example.com")
                .passwordHash(passwordEncoder.encode("admin123"))
                .displayName("Admin User")
                .role(UserRoleEnum.SYSTEM_ADMIN)
                .status(UserStatusEnums.ACTIVE)
                .loginFailCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        User normalUser = User.builder()
                .id(UUID.randomUUID().toString())
                .username("user")
                .email("user@example.com")
                .passwordHash(passwordEncoder.encode("user123"))
                .displayName("Normal User")
                .role(UserRoleEnum.DEPT_USER)
                .status(UserStatusEnums.ACTIVE)
                .loginFailCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userMapper.insert(adminUser);
        userMapper.insert(normalUser);

        // Link users to roles via user_roles table
        UserRole adminUserRole = UserRole.builder()
                .id(UUID.randomUUID().toString())
                .userId(adminUser.getId())
                .roleId(systemAdmin.getId())
                .isPrimary(true)
                .createdAt(LocalDateTime.now())
                .build();

        UserRole normalUserRole = UserRole.builder()
                .id(UUID.randomUUID().toString())
                .userId(normalUser.getId())
                .roleId(deptUser.getId())
                .isPrimary(true)
                .createdAt(LocalDateTime.now())
                .build();

        userRoleMapper.insert(adminUserRole);
        userRoleMapper.insert(normalUserRole);

        // Generate tokens
        adminToken = jwtTokenProvider.generateAccessToken(
                adminUser.getId(),
                adminUser.getUsername(),
                adminUser.getRole().name()
        );
        userToken = jwtTokenProvider.generateAccessToken(
                normalUser.getId(),
                normalUser.getUsername(),
                normalUser.getRole().name()
        );
    }

    @Test
    @DisplayName("GET /roles - With admin role should return 200 and all roles")
    void testGetAllRoles_WithAdminRole_ShouldReturn200() throws Exception {
        mockMvc.perform(get("/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].code").value("SYSTEM_ADMIN"))
                .andExpect(jsonPath("$.data[1].code").value("DEPT_USER"));
    }

    @Test
    @DisplayName("GET /roles - Without auth should return 401")
    void testGetAllRoles_WithoutAuth_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/roles")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /roles - With normal user should return 403")
    void testGetAllRoles_WithNormalUser_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/roles")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /roles/active - With dept admin role should return 200")
    void testGetAllActiveRoles_WithDeptAdminRole_ShouldReturn200() throws Exception {
        // Create DEPT_ADMIN role and user
        Role deptAdmin = Role.builder()
                .id(UUID.randomUUID().toString())
                .code("DEPT_ADMIN")
                .name("部门管理员")
                .level(2)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        roleMapper.insert(deptAdmin);

        User deptAdminUser = User.builder()
                .id(UUID.randomUUID().toString())
                .username("deptadmin")
                .email("deptadmin@example.com")
                .passwordHash(passwordEncoder.encode("password"))
                .displayName("Dept Admin")
                .role(UserRoleEnum.DEPT_ADMIN)
                .status(UserStatusEnums.ACTIVE)
                .loginFailCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userMapper.insert(deptAdminUser);

        UserRole deptAdminUserRole = UserRole.builder()
                .id(UUID.randomUUID().toString())
                .userId(deptAdminUser.getId())
                .roleId(deptAdmin.getId())
                .isPrimary(true)
                .createdAt(LocalDateTime.now())
                .build();
        userRoleMapper.insert(deptAdminUserRole);

        String deptAdminToken = jwtTokenProvider.generateAccessToken(
                deptAdminUser.getId(),
                deptAdminUser.getUsername(),
                deptAdminUser.getRole().name()
        );

        mockMvc.perform(get("/roles/active")
                        .header("Authorization", "Bearer " + deptAdminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /roles/{id} - With valid id should return 200")
    void testGetRoleById_WithValidId_ShouldReturn200() throws Exception {
        Role role = roleMapper.selectList(new QueryWrapper<>()).stream()
                .filter(r -> r.getCode().equals("SYSTEM_ADMIN"))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/roles/{id}", role.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.code").value("SYSTEM_ADMIN"))
                .andExpect(jsonPath("$.data.name").value("系统管理员"));
    }

    @Test
    @DisplayName("GET /roles/{id} - With invalid id should return 404")
    void testGetRoleById_WithInvalidId_ShouldReturn404() throws Exception {
        mockMvc.perform(get("/roles/{id}", "non-existent-id")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("POST /roles - With valid data should return 200")
    void testCreateRole_WithValidData_ShouldReturn200() throws Exception {
        String roleJson = """
            {
                "code": "TEST_ROLE",
                "name": "测试角色",
                "level": 3,
                "description": "Test role",
                "active": true
            }
            """;

        mockMvc.perform(post("/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.code").value("TEST_ROLE"))
                .andExpect(jsonPath("$.data.name").value("测试角色"));

        assertTrue(roleMapper.existsByCode("TEST_ROLE"));
    }

    @Test
    @DisplayName("POST /roles - With duplicate code should return 409")
    void testCreateRole_WithDuplicateCode_ShouldReturn409() throws Exception {
        String roleJson = """
            {
                "code": "SYSTEM_ADMIN",
                "name": "Duplicate Admin",
                "level": 1,
                "active": true
            }
            """;

        mockMvc.perform(post("/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @DisplayName("DELETE /roles/{id} - System role should return 400")
    void testDeleteSystemRole_ShouldReturn400() throws Exception {
        Role systemAdmin = roleMapper.selectList(new QueryWrapper<>()).stream()
                .filter(r -> r.getCode().equals("SYSTEM_ADMIN"))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/roles/{id}", systemAdmin.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("Roles should be initialized with correct data")
    void testRolesInitializedOnStartup() {
        var roles = roleMapper.selectList(new QueryWrapper<>());

        assertNotNull(roles);
        assertEquals(2, roles.size());

        assertTrue(roles.stream().anyMatch(r -> r.getCode().equals("SYSTEM_ADMIN")));
        assertTrue(roles.stream().anyMatch(r -> r.getCode().equals("DEPT_USER")));
    }

    @Test
    @DisplayName("Users should have correct roles")
    void testUsersHaveCorrectRoles() {
        var users = userMapper.selectList(new QueryWrapper<>());

        assertNotNull(users);
        assertEquals(2, users.size());

        User admin = users.stream()
                .filter(u -> u.getUsername().equals("admin"))
                .findFirst()
                .orElseThrow();
        assertEquals(UserRoleEnum.SYSTEM_ADMIN, admin.getRole());
        assertEquals(UserStatusEnums.ACTIVE, admin.getStatus());

        User user = users.stream()
                .filter(u -> u.getUsername().equals("user"))
                .findFirst()
                .orElseThrow();
        assertEquals(UserRoleEnum.DEPT_USER, user.getRole());
        assertEquals(UserStatusEnums.ACTIVE, user.getStatus());
    }
}
