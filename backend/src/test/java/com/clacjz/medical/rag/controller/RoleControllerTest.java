package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.AssignRoleRequest;
import com.clacjz.medical.rag.model.dto.CreateRoleRequest;
import com.clacjz.medical.rag.model.dto.RoleDTO;
import com.clacjz.medical.rag.model.dto.RolePermissionDTO;
import com.clacjz.medical.rag.model.dto.UpdateRoleRequest;
import com.clacjz.medical.rag.model.dto.UpdateRolePermissionsRequest;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RoleController 单元测试")
class RoleControllerTest {

    @Mock
    private RoleService roleService;

    @InjectMocks
    private RoleController roleController;

    private UserDetails adminUserDetails;
    private RoleDTO testRoleDTO;
    private RolePermissionDTO testRolePermissionDTO;

    @BeforeEach
    void setUp() {
        adminUserDetails = User.builder()
                .username("admin")
                .password("password")
                .authorities("ROLE_SYSTEM_ADMIN")
                .build();

        testRoleDTO = RoleDTO.builder()
                .id(UUID.randomUUID().toString())
                .code("TEST_ROLE")
                .name("测试角色")
                .level(3)
                .permissions("[\"document:read\"]")
                .description("Test role description")
                .active(true)
                .userCount(5L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testRolePermissionDTO = RolePermissionDTO.builder()
                .id(testRoleDTO.getId())
                .code(testRoleDTO.getCode())
                .name(testRoleDTO.getName())
                .level(testRoleDTO.getLevel())
                .permissions(testRoleDTO.getPermissions())
                .description(testRoleDTO.getDescription())
                .active(true)
                .userCount(5L)
                .build();
    }

    @Nested
    @DisplayName("getAllRoles")
    class GetAllRolesTests {

        @Test
        @DisplayName("应返回所有角色列表")
        void shouldReturnAllRoles() {
            List<RoleDTO> roles = List.of(testRoleDTO);
            when(roleService.getAllRoles()).thenReturn(roles);

            ResponseEntity<Result<List<RoleDTO>>> response = roleController.getAllRoles();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(200, response.getBody().getCode());
            assertEquals(1, response.getBody().getData().size());
            assertEquals("TEST_ROLE", response.getBody().getData().get(0).getCode());
            verify(roleService).getAllRoles();
        }

        @Test
        @DisplayName("角色列表为空时返回空列表")
        void shouldReturnEmptyListWhenNoRoles() {
            when(roleService.getAllRoles()).thenReturn(Collections.emptyList());

            ResponseEntity<Result<List<RoleDTO>>> response = roleController.getAllRoles();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().getData().isEmpty());
        }
    }

    @Nested
    @DisplayName("initializeDefaultRoles")
    class InitializeDefaultRolesTests {

        @Test
        @DisplayName("应成功初始化默认角色")
        void shouldInitializeDefaultRoles() {
            doNothing().when(roleService).initializeDefaultRoles();

            ResponseEntity<Result<String>> response = roleController.initializeDefaultRoles(adminUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(200, response.getBody().getCode());
            verify(roleService).initializeDefaultRoles();
        }

        @Test
        @DisplayName("用户信息为null时应抛出认证异常")
        void shouldThrowWhenUserDetailsIsNull() {
            assertThrows(BizException.class, () ->
                    roleController.initializeDefaultRoles(null));
            verify(roleService, never()).initializeDefaultRoles();
        }
    }

    @Nested
    @DisplayName("getAllActiveRoles")
    class GetAllActiveRolesTests {

        @Test
        @DisplayName("应返回所有启用的角色")
        void shouldReturnAllActiveRoles() {
            List<RoleDTO> roles = List.of(testRoleDTO);
            when(roleService.getAllActiveRoles()).thenReturn(roles);

            ResponseEntity<Result<List<RoleDTO>>> response = roleController.getAllActiveRoles();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(1, response.getBody().getData().size());
            verify(roleService).getAllActiveRoles();
        }
    }

    @Nested
    @DisplayName("getRoleById")
    class GetRoleByIdTests {

        @Test
        @DisplayName("应根据ID返回角色")
        void shouldReturnRoleById() {
            when(roleService.getRoleById(testRoleDTO.getId())).thenReturn(testRoleDTO);

            ResponseEntity<Result<RoleDTO>> response = roleController.getRoleById(testRoleDTO.getId());

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("TEST_ROLE", response.getBody().getData().getCode());
            verify(roleService).getRoleById(testRoleDTO.getId());
        }

        @Test
        @DisplayName("角色不存在时应抛出BizException")
        void shouldThrowWhenRoleNotFound() {
            when(roleService.getRoleById("non-existent")).thenThrow(new BizException(ErrorCode.ROLE_NOT_FOUND));

            assertThrows(BizException.class, () -> roleController.getRoleById("non-existent"));
        }
    }

    @Nested
    @DisplayName("getRoleByCode")
    class GetRoleByCodeTests {

        @Test
        @DisplayName("应根据代码返回角色")
        void shouldReturnRoleByCode() {
            when(roleService.getRoleByCode("SYSTEM_ADMIN")).thenReturn(testRoleDTO);

            ResponseEntity<Result<RoleDTO>> response = roleController.getRoleByCode("SYSTEM_ADMIN");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("TEST_ROLE", response.getBody().getData().getCode());
            verify(roleService).getRoleByCode("SYSTEM_ADMIN");
        }

        @Test
        @DisplayName("角色代码不存在时应抛出BizException")
        void shouldThrowWhenCodeNotFound() {
            when(roleService.getRoleByCode("INVALID")).thenThrow(new BizException(ErrorCode.ROLE_NOT_FOUND));

            assertThrows(BizException.class, () -> roleController.getRoleByCode("INVALID"));
        }
    }

    @Nested
    @DisplayName("getRolesByLevel")
    class GetRolesByLevelTests {

        @Test
        @DisplayName("级别1应正常返回")
        void shouldReturnRolesForLevel1() {
            when(roleService.getRolesByLevel(1)).thenReturn(List.of(testRoleDTO));

            ResponseEntity<Result<List<RoleDTO>>> response = roleController.getRolesByLevel(1);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(roleService).getRolesByLevel(1);
        }

        @Test
        @DisplayName("级别4应正常返回")
        void shouldReturnRolesForLevel4() {
            when(roleService.getRolesByLevel(4)).thenReturn(List.of(testRoleDTO));

            ResponseEntity<Result<List<RoleDTO>>> response = roleController.getRolesByLevel(4);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(roleService).getRolesByLevel(4);
        }

        @Test
        @DisplayName("级别0应抛出参数异常")
        void shouldThrowForLevel0() {
            assertThrows(BizException.class, () -> roleController.getRolesByLevel(0));
            verify(roleService, never()).getRolesByLevel(anyInt());
        }

        @Test
        @DisplayName("级别5应抛出参数异常")
        void shouldThrowForLevel5() {
            assertThrows(BizException.class, () -> roleController.getRolesByLevel(5));
            verify(roleService, never()).getRolesByLevel(anyInt());
        }

        @Test
        @DisplayName("负数级别应抛出参数异常")
        void shouldThrowForNegativeLevel() {
            assertThrows(BizException.class, () -> roleController.getRolesByLevel(-1));
        }
    }

    @Nested
    @DisplayName("createRole")
    class CreateRoleTests {

        @Test
        @DisplayName("应成功创建角色")
        void shouldCreateRole() {
            CreateRoleRequest request = CreateRoleRequest.builder()
                    .code("NEW_ROLE")
                    .name("新角色")
                    .level(3)
                    .permissions("[\"document:read\"]")
                    .description("New role")
                    .active(true)
                    .build();
            when(roleService.createRole(request)).thenReturn(testRoleDTO);

            ResponseEntity<Result<RoleDTO>> response = roleController.createRole(request, adminUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(200, response.getBody().getCode());
            verify(roleService).createRole(request);
        }

        @Test
        @DisplayName("用户信息为null时应抛出认证异常")
        void shouldThrowWhenUserDetailsNull() {
            CreateRoleRequest request = CreateRoleRequest.builder()
                    .code("NEW_ROLE").name("新角色").build();

            assertThrows(BizException.class, () ->
                    roleController.createRole(request, null));
            verify(roleService, never()).createRole(any());
        }

        @Test
        @DisplayName("角色代码已存在时应抛出BizException")
        void shouldThrowWhenCodeExists() {
            CreateRoleRequest request = CreateRoleRequest.builder()
                    .code("SYSTEM_ADMIN").name("重复角色").build();
            when(roleService.createRole(request)).thenThrow(new BizException(ErrorCode.ROLE_ALREADY_EXISTS));

            assertThrows(BizException.class, () ->
                    roleController.createRole(request, adminUserDetails));
        }
    }

    @Nested
    @DisplayName("updateRole")
    class UpdateRoleTests {

        @Test
        @DisplayName("应成功更新角色")
        void shouldUpdateRole() {
            UpdateRoleRequest request = UpdateRoleRequest.builder()
                    .name("更新名称")
                    .level(2)
                    .build();
            when(roleService.updateRole(testRoleDTO.getId(), request)).thenReturn(testRoleDTO);

            ResponseEntity<Result<RoleDTO>> response = roleController.updateRole(testRoleDTO.getId(), request, adminUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(roleService).updateRole(testRoleDTO.getId(), request);
        }

        @Test
        @DisplayName("用户信息为null时应抛出认证异常")
        void shouldThrowWhenUserDetailsNull() {
            UpdateRoleRequest request = UpdateRoleRequest.builder().name("更新").build();

            assertThrows(BizException.class, () ->
                    roleController.updateRole("id", request, null));
        }

        @Test
        @DisplayName("角色不存在时应抛出BizException")
        void shouldThrowWhenRoleNotFound() {
            UpdateRoleRequest request = UpdateRoleRequest.builder().name("更新").build();
            when(roleService.updateRole("non-existent", request))
                    .thenThrow(new BizException(ErrorCode.ROLE_NOT_FOUND));

            assertThrows(BizException.class, () ->
                    roleController.updateRole("non-existent", request, adminUserDetails));
        }
    }

    @Nested
    @DisplayName("deleteRole")
    class DeleteRoleTests {

        @Test
        @DisplayName("应成功删除角色")
        void shouldDeleteRole() {
            doNothing().when(roleService).deleteRole(testRoleDTO.getId());

            ResponseEntity<Result<Void>> response = roleController.deleteRole(testRoleDTO.getId(), adminUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(roleService).deleteRole(testRoleDTO.getId());
        }

        @Test
        @DisplayName("用户信息为null时应抛出认证异常")
        void shouldThrowWhenUserDetailsNull() {
            assertThrows(BizException.class, () ->
                    roleController.deleteRole("id", null));
        }

        @Test
        @DisplayName("删除系统角色应抛出BizException")
        void shouldThrowWhenDeletingSystemRole() {
            doThrow(new BizException(ErrorCode.CANNOT_DELETE_SYSTEM_ROLE))
                    .when(roleService).deleteRole("system-role-id");

            assertThrows(BizException.class, () ->
                    roleController.deleteRole("system-role-id", adminUserDetails));
        }

        @Test
        @DisplayName("删除有用户的角色应抛出BizException")
        void shouldThrowWhenRoleHasUsers() {
            doThrow(new BizException(ErrorCode.ROLE_HAS_USERS))
                    .when(roleService).deleteRole("role-with-users");

            assertThrows(BizException.class, () ->
                    roleController.deleteRole("role-with-users", adminUserDetails));
        }
    }

    @Nested
    @DisplayName("getUserRoles")
    class GetUserRolesTests {

        @Test
        @DisplayName("应返回用户角色列表")
        void shouldReturnUserRoles() {
            List<RoleDTO> roles = List.of(testRoleDTO);
            when(roleService.getUserRoles("userId")).thenReturn(roles);

            ResponseEntity<Result<List<RoleDTO>>> response = roleController.getUserRoles("userId");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(1, response.getBody().getData().size());
            verify(roleService).getUserRoles("userId");
        }

        @Test
        @DisplayName("用户不存在时应抛出BizException")
        void shouldThrowWhenUserNotFound() {
            when(roleService.getUserRoles("non-existent"))
                    .thenThrow(new BizException(ErrorCode.USER_NOT_FOUND));

            assertThrows(BizException.class, () ->
                    roleController.getUserRoles("non-existent"));
        }
    }

    @Nested
    @DisplayName("assignRoleToUser")
    class AssignRoleToUserTests {

        @Test
        @DisplayName("应成功分配角色")
        void shouldAssignRole() {
            AssignRoleRequest request = AssignRoleRequest.builder()
                    .roleId("role-123")
                    .isPrimary(true)
                    .build();
            doNothing().when(roleService).assignRoleToUser("userId", request);

            ResponseEntity<Result<Void>> response = roleController.assignRoleToUser("userId", request, adminUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(roleService).assignRoleToUser("userId", request);
        }

        @Test
        @DisplayName("用户信息为null时应抛出认证异常")
        void shouldThrowWhenUserDetailsNull() {
            AssignRoleRequest request = AssignRoleRequest.builder().roleId("role-123").build();

            assertThrows(BizException.class, () ->
                    roleController.assignRoleToUser("userId", request, null));
        }

        @Test
        @DisplayName("用户已拥有该角色时应抛出BizException")
        void shouldThrowWhenRoleAlreadyAssigned() {
            AssignRoleRequest request = AssignRoleRequest.builder().roleId("role-123").build();
            doThrow(new BizException(ErrorCode.USER_ROLE_ALREADY_EXISTS))
                    .when(roleService).assignRoleToUser("userId", request);

            assertThrows(BizException.class, () ->
                    roleController.assignRoleToUser("userId", request, adminUserDetails));
        }
    }

    @Nested
    @DisplayName("assignRoleToUserSimple")
    class AssignRoleToUserSimpleTests {

        @Test
        @DisplayName("应成功分配角色（快捷方式，默认非首选）")
        void shouldAssignRoleSimpleDefaultNotPrimary() {
            doNothing().when(roleService).assignRoleToUser("userId", "roleId", false);

            ResponseEntity<Result<Void>> response = roleController.assignRoleToUserSimple(
                    "userId", "roleId", false, adminUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(roleService).assignRoleToUser("userId", "roleId", false);
        }

        @Test
        @DisplayName("应成功分配角色（快捷方式，设为首选）")
        void shouldAssignRoleSimpleAsPrimary() {
            doNothing().when(roleService).assignRoleToUser("userId", "roleId", true);

            ResponseEntity<Result<Void>> response = roleController.assignRoleToUserSimple(
                    "userId", "roleId", true, adminUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(roleService).assignRoleToUser("userId", "roleId", true);
        }

        @Test
        @DisplayName("用户信息为null时应抛出认证异常")
        void shouldThrowWhenUserDetailsNull() {
            assertThrows(BizException.class, () ->
                    roleController.assignRoleToUserSimple("userId", "roleId", false, null));
        }
    }

    @Nested
    @DisplayName("removeRoleFromUser")
    class RemoveRoleFromUserTests {

        @Test
        @DisplayName("应成功移除用户角色")
        void shouldRemoveRoleFromUser() {
            doNothing().when(roleService).removeRoleFromUser("userId", "roleId");

            ResponseEntity<Result<Void>> response = roleController.removeRoleFromUser("userId", "roleId", adminUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(roleService).removeRoleFromUser("userId", "roleId");
        }

        @Test
        @DisplayName("用户信息为null时应抛出认证异常")
        void shouldThrowWhenUserDetailsNull() {
            assertThrows(BizException.class, () ->
                    roleController.removeRoleFromUser("userId", "roleId", null));
        }

        @Test
        @DisplayName("用户角色不存在时应抛出BizException")
        void shouldThrowWhenUserRoleNotFound() {
            doThrow(new BizException(ErrorCode.USER_ROLE_NOT_FOUND))
                    .when(roleService).removeRoleFromUser("userId", "roleId");

            assertThrows(BizException.class, () ->
                    roleController.removeRoleFromUser("userId", "roleId", adminUserDetails));
        }

        @Test
        @DisplayName("移除最后一个首选角色应抛出BizException")
        void shouldThrowWhenRemovingLastPrimaryRole() {
            doThrow(new BizException(ErrorCode.LAST_PRIMARY_ROLE_CANNOT_DELETE))
                    .when(roleService).removeRoleFromUser("userId", "roleId");

            assertThrows(BizException.class, () ->
                    roleController.removeRoleFromUser("userId", "roleId", adminUserDetails));
        }
    }

    @Nested
    @DisplayName("getAllRolePermissions")
    class GetAllRolePermissionsTests {

        @Test
        @DisplayName("应返回所有角色权限配置")
        void shouldReturnAllRolePermissions() {
            List<RolePermissionDTO> permissions = List.of(testRolePermissionDTO);
            when(roleService.getAllRolePermissions()).thenReturn(permissions);

            ResponseEntity<Result<List<RolePermissionDTO>>> response = roleController.getAllRolePermissions();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(1, response.getBody().getData().size());
            verify(roleService).getAllRolePermissions();
        }
    }

    @Nested
    @DisplayName("getRolePermissions")
    class GetRolePermissionsTests {

        @Test
        @DisplayName("应返回指定角色的权限配置")
        void shouldReturnRolePermissions() {
            when(roleService.getRolePermissions(testRoleDTO.getId())).thenReturn(testRolePermissionDTO);

            ResponseEntity<Result<RolePermissionDTO>> response = roleController.getRolePermissions(testRoleDTO.getId());

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("TEST_ROLE", response.getBody().getData().getCode());
            verify(roleService).getRolePermissions(testRoleDTO.getId());
        }

        @Test
        @DisplayName("角色不存在时应抛出BizException")
        void shouldThrowWhenRoleNotFound() {
            when(roleService.getRolePermissions("non-existent"))
                    .thenThrow(new BizException(ErrorCode.ROLE_NOT_FOUND));

            assertThrows(BizException.class, () ->
                    roleController.getRolePermissions("non-existent"));
        }
    }

    @Nested
    @DisplayName("updateRolePermissions")
    class UpdateRolePermissionsTests {

        @Test
        @DisplayName("应成功更新角色权限")
        void shouldUpdateRolePermissions() {
            UpdateRolePermissionsRequest request = UpdateRolePermissionsRequest.builder()
                    .permissions("[\"document:read\", \"document:write\"]")
                    .build();
            when(roleService.updateRolePermissions(testRoleDTO.getId(), request.getPermissions()))
                    .thenReturn(testRolePermissionDTO);

            ResponseEntity<Result<RolePermissionDTO>> response = roleController.updateRolePermissions(
                    testRoleDTO.getId(), request, adminUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(roleService).updateRolePermissions(testRoleDTO.getId(), request.getPermissions());
        }

        @Test
        @DisplayName("用户信息为null时应抛出认证异常")
        void shouldThrowWhenUserDetailsNull() {
            UpdateRolePermissionsRequest request = UpdateRolePermissionsRequest.builder()
                    .permissions("[\"doc:read\"]")
                    .build();

            assertThrows(BizException.class, () ->
                    roleController.updateRolePermissions("id", request, null));
        }

        @Test
        @DisplayName("修改系统角色权限应抛出BizException")
        void shouldThrowWhenModifyingSystemRolePermissions() {
            UpdateRolePermissionsRequest request = UpdateRolePermissionsRequest.builder()
                    .permissions("[\"doc:read\"]")
                    .build();
            when(roleService.updateRolePermissions("system-role-id", request.getPermissions()))
                    .thenThrow(new BizException(ErrorCode.CANNOT_MODIFY_SYSTEM_ROLE_PERMISSIONS));

            assertThrows(BizException.class, () ->
                    roleController.updateRolePermissions("system-role-id", request, adminUserDetails));
        }
    }

    @Nested
    @DisplayName("getUsernameSafely")
    class GetUsernameSafelyTests {

        @Test
        @DisplayName("null UserDetails应抛出认证异常")
        void shouldThrowForNullUserDetails() {
            // Via initializeDefaultRoles which calls getUsernameSafely
            assertThrows(BizException.class, () ->
                    roleController.initializeDefaultRoles(null));
        }

        @Test
        @DisplayName("异常消息应为'用户信息不存在，请重新登录'")
        void shouldHaveCorrectErrorMessage() {
            BizException ex = assertThrows(BizException.class, () ->
                    roleController.initializeDefaultRoles(null));
            assertEquals("用户信息不存在，请重新登录", ex.getMessage());
        }
    }
}
