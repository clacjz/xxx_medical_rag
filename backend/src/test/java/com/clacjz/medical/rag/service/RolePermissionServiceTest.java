package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.RolePermissionDTO;
import com.clacjz.medical.rag.model.entity.Role;
import com.clacjz.medical.rag.mapper.RoleMapper;
import com.clacjz.medical.rag.mapper.UserRoleMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RolePermissionService 单元测试")
class RolePermissionServiceTest {

    @Mock
    private RoleMapper roleRepository;

    @Mock
    private UserRoleMapper userRoleRepository;

    @Mock
    private UserMapper userRepository;

    @InjectMocks
    private RoleService roleService;

    private Role systemRole;
    private Role customRole;

    @BeforeEach
    void setUp() {
        systemRole = Role.builder()
                .id("system-role-id")
                .code("SYSTEM_ADMIN")
                .name("系统管理员")
                .level(1)
                .permissions("[\"user:*\", \"document:*\"]")
                .active(true)
                .build();

        customRole = Role.builder()
                .id("custom-role-id")
                .code("CUSTOM_ROLE")
                .name("自定义角色")
                .level(3)
                .permissions("[\"document:read\"]")
                .active(true)
                .build();
    }

    @Test
    @DisplayName("应能获取角色权限配置")
    void shouldGetRolePermissions() {
        when(roleRepository.selectById("custom-role-id")).thenReturn(customRole);
        when(roleRepository.countUsersByRoleId("custom-role-id")).thenReturn(5L);

        RolePermissionDTO result = roleService.getRolePermissions("custom-role-id");

        assertNotNull(result);
        assertEquals("custom-role-id", result.getId());
        assertEquals("CUSTOM_ROLE", result.getCode());
        assertEquals("自定义角色", result.getName());
        assertEquals(3, result.getLevel());
        assertEquals("[\"document:read\"]", result.getPermissions());
        assertEquals(5L, result.getUserCount());
    }

    @Test
    @DisplayName("应能更新自定义角色权限")
    void shouldUpdateCustomRolePermissions() {
        String newPermissions = "[\"document:*\", \"collection:read\"]";
        when(roleRepository.selectById("custom-role-id")).thenReturn(customRole);
        when(roleRepository.updateById(any(Role.class))).thenReturn(1);
        when(roleRepository.countUsersByRoleId("custom-role-id")).thenReturn(5L);

        RolePermissionDTO result = roleService.updateRolePermissions("custom-role-id", newPermissions);

        assertNotNull(result);
        assertEquals(newPermissions, result.getPermissions());
        verify(roleRepository).updateById(any(Role.class));
    }

    @Test
    @DisplayName("不应允许修改系统角色权限")
    void shouldNotModifySystemRolePermissions() {
        when(roleRepository.selectById("system-role-id")).thenReturn(systemRole);

        BizException exception = assertThrows(BizException.class, () ->
                roleService.updateRolePermissions("system-role-id", "[\"new:permission\"]"));

        assertEquals(ErrorCode.CANNOT_MODIFY_SYSTEM_ROLE_PERMISSIONS, exception.getErrorCode());
        verify(roleRepository, never()).updateById(any());
    }

    @Test
    @DisplayName("应能获取所有角色权限列表")
    void shouldGetAllRolePermissions() {
        when(roleRepository.selectList(null)).thenReturn(List.of(systemRole, customRole));
        when(roleRepository.countUsersByRoleIds()).thenReturn(List.of(
                Map.of("role_id", "system-role-id", "cnt", 2L),
                Map.of("role_id", "custom-role-id", "cnt", 5L)
        ));

        List<RolePermissionDTO> result = roleService.getAllRolePermissions();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("SYSTEM_ADMIN", result.get(0).getCode());
        assertEquals("CUSTOM_ROLE", result.get(1).getCode());
    }

    @Test
    @DisplayName("获取不存在的角色权限应抛出异常")
    void shouldThrowExceptionForNonExistentRole() {
        when(roleRepository.selectById("non-existent")).thenReturn(null);

        BizException exception = assertThrows(BizException.class, () ->
                roleService.getRolePermissions("non-existent"));

        assertEquals(ErrorCode.ROLE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("更新不存在的角色权限应抛出异常")
    void shouldThrowExceptionWhenUpdatingNonExistentRole() {
        when(roleRepository.selectById("non-existent")).thenReturn(null);

        BizException exception = assertThrows(BizException.class, () ->
                roleService.updateRolePermissions("non-existent", "[\"test\"]"));

        assertEquals(ErrorCode.ROLE_NOT_FOUND, exception.getErrorCode());
    }
}
