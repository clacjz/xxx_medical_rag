package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.mapper.RoleMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.mapper.UserRoleMapper;
import com.clacjz.medical.rag.model.dto.CreateRoleRequest;
import com.clacjz.medical.rag.model.dto.RoleDTO;
import com.clacjz.medical.rag.model.dto.UpdateRoleRequest;
import com.clacjz.medical.rag.model.entity.Role;
import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.entity.UserRole;
import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import com.clacjz.medical.rag.model.enums.UserStatusEnums;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoleService 单元测试")
class RoleServiceTest {

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PermissionCacheService permissionCacheService;

    @InjectMocks
    private RoleService roleService;

    private Role systemAdminRole;
    private Role deptAdminRole;
    private Role deptUserRole;
    private User testUser;

    @BeforeEach
    void setUp() {
        systemAdminRole = Role.builder()
                .id("role-1")
                .code("SYSTEM_ADMIN")
                .name("系统管理员")
                .level(1)
                .permissions("[\"user:*\"]")
                .description("全系统最高权限")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        deptAdminRole = Role.builder()
                .id("role-2")
                .code("DEPT_ADMIN")
                .name("部门管理员")
                .level(2)
                .permissions("[\"department:*\"]")
                .description("部门管理权限")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        deptUserRole = Role.builder()
                .id("role-4")
                .code("DEPT_USER")
                .name("部门普通人员")
                .level(4)
                .permissions("[\"document:read\"]")
                .description("普通用户权限")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testUser = User.builder()
                .id("user-1")
                .username("testuser")
                .email("test@example.com")
                .passwordHash("hashedpassword")
                .displayName("Test User")
                .role(UserRoleEnum.DEPT_USER)
                .status(UserStatusEnums.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("获取所有角色")
    void testGetAllRoles_Success() {
        when(roleMapper.selectList(any())).thenReturn(Arrays.asList(systemAdminRole, deptAdminRole, deptUserRole));
        when(roleMapper.countUsersByRoleIds()).thenReturn(Arrays.asList(
                Map.of("role_id", "role-1", "cnt", 5L),
                Map.of("role_id", "role-2", "cnt", 10L),
                Map.of("role_id", "role-4", "cnt", 50L)
        ));

        List<RoleDTO> roles = roleService.getAllRoles();

        assertNotNull(roles);
        assertEquals(3, roles.size());
        assertEquals("SYSTEM_ADMIN", roles.get(0).getCode());
        assertEquals(5L, roles.get(0).getUserCount());
    }

    @Test
    @DisplayName("获取所有活跃角色")
    void testGetAllActiveRoles_Success() {
        when(roleMapper.selectAllActiveOrderByLevel()).thenReturn(Arrays.asList(systemAdminRole, deptAdminRole, deptUserRole));
        when(roleMapper.countUsersByRoleIds()).thenReturn(Arrays.asList(
                Map.of("role_id", "role-1", "cnt", 5L),
                Map.of("role_id", "role-2", "cnt", 10L),
                Map.of("role_id", "role-4", "cnt", 50L)
        ));

        List<RoleDTO> roles = roleService.getAllActiveRoles();

        assertNotNull(roles);
        assertEquals(3, roles.size());
        verify(roleMapper).selectAllActiveOrderByLevel();
    }

    @Test
    @DisplayName("按ID获取角色成功")
    void testGetRoleById_Success() {
        when(roleMapper.selectById("role-1")).thenReturn(systemAdminRole);
        when(roleMapper.countUsersByRoleId("role-1")).thenReturn(5L);

        RoleDTO role = roleService.getRoleById("role-1");

        assertNotNull(role);
        assertEquals("SYSTEM_ADMIN", role.getCode());
        assertEquals("系统管理员", role.getName());
        assertEquals(1, role.getLevel());
    }

    @Test
    @DisplayName("按ID获取角色失败 - 不存在")
    void testGetRoleById_NotFound() {
        when(roleMapper.selectById("non-existent")).thenReturn(null);

        BizException exception = assertThrows(BizException.class, () -> roleService.getRoleById("non-existent"));

        assertEquals(ErrorCode.ROLE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("按代码获取角色成功")
    void testGetRoleByCode_Success() {
        when(roleMapper.selectByCode("SYSTEM_ADMIN")).thenReturn(systemAdminRole);
        when(roleMapper.countUsersByRoleId("role-1")).thenReturn(5L);

        RoleDTO role = roleService.getRoleByCode("SYSTEM_ADMIN");

        assertNotNull(role);
        assertEquals("role-1", role.getId());
    }

    @Test
    @DisplayName("创建角色成功")
    void testCreateRole_Success() {
        CreateRoleRequest request = CreateRoleRequest.builder()
                .code("TEST_ROLE")
                .name("测试角色")
                .level(3)
                .description("测试用角色")
                .active(true)
                .build();

        when(roleMapper.existsByCode("TEST_ROLE")).thenReturn(false);
        when(roleMapper.insert(any(Role.class))).thenReturn(1);

        RoleDTO result = roleService.createRole(request);

        assertNotNull(result);
        assertEquals("TEST_ROLE", result.getCode());
        assertEquals("测试角色", result.getName());
        verify(roleMapper).insert(any(Role.class));
    }

    @Test
    @DisplayName("创建角色失败 - 已存在")
    void testCreateRole_AlreadyExists() {
        CreateRoleRequest request = CreateRoleRequest.builder()
                .code("SYSTEM_ADMIN")
                .name("系统管理员")
                .level(1)
                .build();

        when(roleMapper.existsByCode("SYSTEM_ADMIN")).thenReturn(true);

        BizException exception = assertThrows(BizException.class, () -> roleService.createRole(request));

        assertEquals(ErrorCode.ROLE_ALREADY_EXISTS, exception.getErrorCode());
    }

    @Test
    @DisplayName("更新角色成功 - 自定义角色")
    void testUpdateRole_Success() {
        Role customRole = Role.builder()
                .id("custom-role")
                .code("CUSTOM_ROLE")
                .name("自定义角色")
                .level(3)
                .active(true)
                .build();

        UpdateRoleRequest request = UpdateRoleRequest.builder()
                .name("更新后的名称")
                .description("更新后的描述")
                .build();

        when(roleMapper.selectById("custom-role")).thenReturn(customRole);
        when(roleMapper.updateById(any(Role.class))).thenReturn(1);
        when(roleMapper.countUsersByRoleId("custom-role")).thenReturn(5L);

        RoleDTO result = roleService.updateRole("custom-role", request);

        assertNotNull(result);
        verify(roleMapper).updateById(any(Role.class));
    }

    @Test
    @DisplayName("更新系统角色核心属性被拒绝")
    void testUpdateRole_SystemRoleCoreFieldsRejected() {
        UpdateRoleRequest request = UpdateRoleRequest.builder()
                .name("更新后的名称")
                .description("更新后的描述")
                .build();

        when(roleMapper.selectById("role-1")).thenReturn(systemAdminRole);

        assertThrows(BizException.class, () -> roleService.updateRole("role-1", request));
    }

    @Test
    @DisplayName("更新系统角色仅description允许修改")
    void testUpdateRole_SystemRoleDescriptionOnly() {
        UpdateRoleRequest request = UpdateRoleRequest.builder()
                .description("仅更新描述")
                .build();

        when(roleMapper.selectById("role-1")).thenReturn(systemAdminRole);
        when(roleMapper.updateById(any(Role.class))).thenReturn(1);
        when(roleMapper.countUsersByRoleId("role-1")).thenReturn(5L);

        RoleDTO result = roleService.updateRole("role-1", request);

        assertNotNull(result);
        verify(roleMapper).updateById(any(Role.class));
    }

    @Test
    @DisplayName("删除角色成功")
    void testDeleteRole_Success() {
        Role customRole = Role.builder()
                .id("custom-role")
                .code("CUSTOM_ROLE")
                .name("自定义角色")
                .level(3)
                .active(true)
                .build();

        when(roleMapper.selectById("custom-role")).thenReturn(customRole);
        when(roleMapper.countUsersByRoleId("custom-role")).thenReturn(0L);
        when(roleMapper.deleteById("custom-role")).thenReturn(1);

        assertDoesNotThrow(() -> roleService.deleteRole("custom-role"));
        verify(roleMapper).deleteById("custom-role");
    }

    @Test
    @DisplayName("删除角色失败 - 系统角色")
    void testDeleteRole_SystemRole() {
        when(roleMapper.selectById("role-1")).thenReturn(systemAdminRole);

        BizException exception = assertThrows(BizException.class, () -> roleService.deleteRole("role-1"));

        assertEquals(ErrorCode.CANNOT_DELETE_SYSTEM_ROLE, exception.getErrorCode());
    }

    @Test
    @DisplayName("删除角色失败 - 角色下有用户")
    void testDeleteRole_HasUsers() {
        Role customRole = Role.builder()
                .id("custom-role")
                .code("CUSTOM_ROLE")
                .name("自定义角色")
                .level(3)
                .active(true)
                .build();

        when(roleMapper.selectById("custom-role")).thenReturn(customRole);
        when(roleMapper.countUsersByRoleId("custom-role")).thenReturn(10L);

        BizException exception = assertThrows(BizException.class, () -> roleService.deleteRole("custom-role"));

        assertEquals(ErrorCode.ROLE_HAS_USERS, exception.getErrorCode());
    }

    @Test
    @DisplayName("分配角色给用户成功")
    void testAssignRoleToUser_Success() {
        when(userMapper.selectById("user-1")).thenReturn(testUser);
        when(roleMapper.selectById("role-2")).thenReturn(deptAdminRole);
        when(userRoleMapper.existsByUserIdAndRoleId("user-1", "role-2")).thenReturn(false);
        when(userRoleMapper.insert(any(UserRole.class))).thenReturn(1);

        assertDoesNotThrow(() -> roleService.assignRoleToUser("user-1", "role-2", false));
        verify(userRoleMapper).insert(any(UserRole.class));
    }

    @Test
    @DisplayName("分配角色失败 - 用户不存在")
    void testAssignRoleToUser_UserNotFound() {
        when(userMapper.selectById("non-existent")).thenReturn(null);

        BizException exception = assertThrows(BizException.class,
                () -> roleService.assignRoleToUser("non-existent", "role-2", false));

        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("分配角色失败 - 角色不存在")
    void testAssignRoleToUser_RoleNotFound() {
        when(userMapper.selectById("user-1")).thenReturn(testUser);
        when(roleMapper.selectById("non-existent")).thenReturn(null);

        BizException exception = assertThrows(BizException.class,
                () -> roleService.assignRoleToUser("user-1", "non-existent", false));

        assertEquals(ErrorCode.ROLE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("分配角色失败 - 角色已禁用")
    void testAssignRoleToUser_RoleDisabled() {
        Role disabledRole = Role.builder()
                .id("disabled-role")
                .code("DISABLED_ROLE")
                .name("已禁用角色")
                .level(3)
                .active(false)
                .build();

        when(userMapper.selectById("user-1")).thenReturn(testUser);
        when(roleMapper.selectById("disabled-role")).thenReturn(disabledRole);

        BizException exception = assertThrows(BizException.class,
                () -> roleService.assignRoleToUser("user-1", "disabled-role", false));

        assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
    }

    @Test
    @DisplayName("分配角色失败 - 已分配")
    void testAssignRoleToUser_AlreadyAssigned() {
        when(userMapper.selectById("user-1")).thenReturn(testUser);
        when(roleMapper.selectById("role-2")).thenReturn(deptAdminRole);
        when(userRoleMapper.existsByUserIdAndRoleId("user-1", "role-2")).thenReturn(true);

        BizException exception = assertThrows(BizException.class,
                () -> roleService.assignRoleToUser("user-1", "role-2", false));

        assertEquals(ErrorCode.USER_ROLE_ALREADY_EXISTS, exception.getErrorCode());
    }

    @Test
    @DisplayName("分配角色失败 - 设置主要角色")
    void testAssignRoleToUser_SetPrimary() {
        UserRole existingPrimary = UserRole.builder()
                .id("ur-1")
                .userId("user-1")
                .roleId("role-4")
                .isPrimary(true)
                .build();

        when(userMapper.selectById("user-1")).thenReturn(testUser);
        when(roleMapper.selectById("role-2")).thenReturn(deptAdminRole);
        when(userRoleMapper.existsByUserIdAndRoleId("user-1", "role-2")).thenReturn(false);
        when(userRoleMapper.selectByUserIdAndIsPrimaryTrue("user-1")).thenReturn(existingPrimary);
        when(userRoleMapper.updateById(any(UserRole.class))).thenReturn(1);
        when(userRoleMapper.insert(any(UserRole.class))).thenReturn(1);

        assertDoesNotThrow(() -> roleService.assignRoleToUser("user-1", "role-2", true));

        verify(userRoleMapper, times(1)).updateById(any(UserRole.class));
        verify(userRoleMapper).insert(any(UserRole.class));
    }

    @Test
    @DisplayName("从用户移除角色成功")
    void testRemoveRoleFromUser_Success() {
        UserRole userRole = UserRole.builder()
                .id("ur-1")
                .userId("user-1")
                .roleId("role-2")
                .isPrimary(false)
                .build();

        when(userMapper.selectById("user-1")).thenReturn(testUser);
        when(roleMapper.selectById("role-2")).thenReturn(deptAdminRole);
        when(userRoleMapper.selectByUserIdAndRoleId("user-1", "role-2")).thenReturn(userRole);
        when(userRoleMapper.deleteById("ur-1")).thenReturn(1);

        assertDoesNotThrow(() -> roleService.removeRoleFromUser("user-1", "role-2"));
        verify(userRoleMapper).deleteById("ur-1");
    }

    @Test
    @DisplayName("从用户移除角色失败 - 最后一个主要角色")
    void testRemoveRoleFromUser_LastPrimaryRole() {
        UserRole primaryRole = UserRole.builder()
                .id("ur-1")
                .userId("user-1")
                .roleId("role-4")
                .isPrimary(true)
                .build();

        when(userMapper.selectById("user-1")).thenReturn(testUser);
        when(roleMapper.selectById("role-4")).thenReturn(deptUserRole);
        when(userRoleMapper.selectByUserIdAndRoleId("user-1", "role-4")).thenReturn(primaryRole);
        when(userRoleMapper.countByUserId("user-1")).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> roleService.removeRoleFromUser("user-1", "role-4"));

        assertEquals(ErrorCode.LAST_PRIMARY_ROLE_CANNOT_DELETE, exception.getErrorCode());
    }

    @Test
    @DisplayName("获取用户角色列表成功")
    void testGetUserRoles_Success() {
        UserRole userRole1 = UserRole.builder()
                .id("ur-1")
                .userId("user-1")
                .roleId("role-1")
                .isPrimary(true)
                .role(systemAdminRole)
                .build();

        UserRole userRole2 = UserRole.builder()
                .id("ur-2")
                .userId("user-1")
                .roleId("role-2")
                .isPrimary(false)
                .role(deptAdminRole)
                .build();

        when(userMapper.selectById("user-1")).thenReturn(testUser);
        when(userRoleMapper.selectByUserId("user-1")).thenReturn(Arrays.asList(userRole1, userRole2));
        when(roleMapper.countUsersByRoleId("role-1")).thenReturn(5L);
        when(roleMapper.countUsersByRoleId("role-2")).thenReturn(10L);

        List<RoleDTO> roles = roleService.getUserRoles("user-1");

        assertNotNull(roles);
        assertEquals(2, roles.size());
    }

    @Test
    @DisplayName("检查用户是否有角色")
    void testHasRole_Success() {
        UserRole userRole = UserRole.builder()
                .id("ur-1")
                .userId("user-1")
                .roleId("role-1")
                .isPrimary(true)
                .role(systemAdminRole)
                .build();

        when(userRoleMapper.selectByUserId("user-1")).thenReturn(Collections.singletonList(userRole));

        assertTrue(roleService.hasRole("user-1", "SYSTEM_ADMIN"));
        assertFalse(roleService.hasRole("user-1", "DEPT_ADMIN"));
    }

    @Test
    @DisplayName("检查用户是否有级别权限")
    void testHasRoleLevel_Success() {
        UserRole userRole = UserRole.builder()
                .id("ur-1")
                .userId("user-1")
                .roleId("role-2")
                .isPrimary(true)
                .role(deptAdminRole)
                .build();

        when(userRoleMapper.selectByUserId("user-1")).thenReturn(Collections.singletonList(userRole));

        assertTrue(roleService.hasRoleLevel("user-1", 2));
        assertTrue(roleService.hasRoleLevel("user-1", 3));
        assertFalse(roleService.hasRoleLevel("user-1", 1));
    }

    @Test
    @DisplayName("初始化默认角色 - 已初始化")
    void testInitializeDefaultRoles_AlreadyInitialized() {
        when(roleMapper.selectCount(any())).thenReturn(4L);

        assertDoesNotThrow(() -> roleService.initializeDefaultRoles());
        verify(roleMapper, never()).insert(any(Role.class));
    }

    @Test
    @DisplayName("初始化默认角色成功")
    void testInitializeDefaultRoles_Success() {
        when(roleMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.insert(any(Role.class))).thenReturn(1);

        assertDoesNotThrow(() -> roleService.initializeDefaultRoles());
        verify(roleMapper, times(4)).insert(any(Role.class));
    }
}
