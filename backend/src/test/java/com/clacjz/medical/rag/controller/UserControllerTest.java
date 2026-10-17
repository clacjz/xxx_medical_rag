package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.UpdateUserRequest;
import com.clacjz.medical.rag.model.dto.UserDTO;
import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import com.clacjz.medical.rag.model.enums.UserStatusEnums;
import com.clacjz.medical.rag.model.vo.PageResult;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.UserService;
import com.clacjz.medical.rag.util.RsaCryptoUtil;
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
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserController 单元测试")
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private com.clacjz.medical.rag.service.RoleService roleService;

    @Mock
    private RsaCryptoUtil rsaCryptoUtil;

    @InjectMocks
    private UserController userController;

    private UserDTO testUser;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        testUser = UserDTO.builder()
                .id(UUID.randomUUID().toString())
                .username("testuser")
                .email("test@example.com")
                .displayName("Test User")
                .role(UserRoleEnum.DEPT_USER)
                .status(UserStatusEnums.ACTIVE)
                .build();

        userDetails = User.builder()
                .username("admin")
                .password("p")
                .authorities("ROLE_SYSTEM_ADMIN")
                .build();
    }

    @Nested
    @DisplayName("getUserList")
    class GetUserListTests {

        @Test
        @DisplayName("获取用户列表成功")
        void shouldReturnUserList() {
            Page<UserDTO> page = new Page<>(1, 10);
            page.setRecords(List.of(testUser));
            page.setTotal(1);
            IPage<UserDTO> userPage = page;
            when(userService.getUserList(0, 10, null, null)).thenReturn(userPage);

            ResponseEntity<Result<PageResult<UserDTO>>> response = userController.getUserList(0, 10, null, null);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(200, response.getBody().getCode());
        }

        @Test
        @DisplayName("空结果返回 200 + 空列表")
        void shouldReturnEmptyList() {
            Page<UserDTO> page = new Page<>(1, 10);
            when(userService.getUserList(anyInt(), anyInt(), any(), any())).thenReturn(page);

            ResponseEntity<Result<PageResult<UserDTO>>> response = userController.getUserList(0, 10, null, null);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("带 keyword 和 role 过滤")
        void shouldPassFilters() {
            Page<UserDTO> page = new Page<>(1, 10);
            when(userService.getUserList(0, 10, UserRoleEnum.DEPT_USER, UserStatusEnums.ACTIVE)).thenReturn(page);

            ResponseEntity<Result<PageResult<UserDTO>>> response =
                    userController.getUserList(0, 10, UserRoleEnum.DEPT_USER, UserStatusEnums.ACTIVE);

            verify(userService).getUserList(0, 10, UserRoleEnum.DEPT_USER, UserStatusEnums.ACTIVE);
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("getUserById")
    class GetUserByIdTests {

        @Test
        @DisplayName("成功")
        void shouldReturnUser() {
            String userId = testUser.getId();
            when(userService.getUserById(userId)).thenReturn(testUser);

            ResponseEntity<Result<UserDTO>> response = userController.getUserById(userId);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("testuser", response.getBody().getData().getUsername());
        }

        @Test
        @DisplayName("失败时抛 BizException")
        void shouldThrowWhenNotFound() {
            when(userService.getUserById("missing"))
                    .thenThrow(new BizException(ErrorCode.USER_NOT_FOUND, "用户不存在"));

            assertThrows(BizException.class, () -> userController.getUserById("missing"));
        }
    }

    @Nested
    @DisplayName("createUser")
    class CreateUserTests {

        @Test
        @DisplayName("使用明文密码创建")
        void shouldCreateUserWithPlainPassword() {
            Map<String, String> request = Map.of(
                    "username", "newuser",
                    "password", "plain",
                    "email", "new@example.com",
                    "role", "DEPT_USER"
            );
            when(rsaCryptoUtil.isEncrypted("plain")).thenReturn(false);
            when(userService.createUser("newuser", "plain", "new@example.com", "DEPT_USER"))
                    .thenReturn(testUser);

            ResponseEntity<Result<UserDTO>> response = userController.createUser(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(userService).createUser("newuser", "plain", "new@example.com", "DEPT_USER");
        }

        @Test
        @DisplayName("使用加密密码创建时自动解密")
        void shouldDecryptEncryptedPassword() {
            Map<String, String> request = Map.of(
                    "username", "newuser",
                    "password", "encrypted",
                    "email", "new@example.com",
                    "role", "DEPT_USER"
            );
            when(rsaCryptoUtil.isEncrypted("encrypted")).thenReturn(true);
            when(rsaCryptoUtil.decrypt("encrypted")).thenReturn("decrypted");
            when(userService.createUser("newuser", "decrypted", "new@example.com", "DEPT_USER"))
                    .thenReturn(testUser);

            ResponseEntity<Result<UserDTO>> response = userController.createUser(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(rsaCryptoUtil).decrypt("encrypted");
        }

        @Test
        @DisplayName("RSA解密失败时拒绝请求而非静默降级为密文")
        void shouldRejectWhenRsaDecryptFailsForCreate() {
            Map<String, String> request = Map.of(
                    "username", "newuser",
                    "password", "encrypted",
                    "email", "new@example.com",
                    "role", "DEPT_USER"
            );
            when(rsaCryptoUtil.isEncrypted("encrypted")).thenReturn(true);
            when(rsaCryptoUtil.decrypt("encrypted")).thenThrow(new RuntimeException("Decryption failed"));

            BizException ex = assertThrows(BizException.class,
                    () -> userController.createUser(request));
            assertEquals(ErrorCode.INVALID_PARAMETER, ex.getErrorCode());
            // 验证userService.createUser从未被调用（密码未被静默降级为密文）
            verify(userService, never()).createUser(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("assignRole")
    class AssignRoleTests {

        @Test
        @DisplayName("成功分配角色")
        void shouldAssignRole() {
            String userId = "u-1";
            Map<String, String> request = Map.of("roleId", "r-1");
            doNothing().when(roleService).assignRoleToUser(userId, "r-1", false);

            ResponseEntity<Result<Void>> response = userController.assignRole(userId, request, userDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(roleService).assignRoleToUser(userId, "r-1", false);
        }

        @Test
        @DisplayName("userDetails 为 null 时抛异常")
        void shouldThrowWhenNoUserDetails() {
            Map<String, String> request = Map.of("roleId", "r-1");
            assertThrows(BizException.class,
                    () -> userController.assignRole("u-1", request, null));
        }
    }

    @Nested
    @DisplayName("updateUser")
    class UpdateUserTests {

        @Test
        @DisplayName("成功更新")
        void shouldUpdateUser() {
            UpdateUserRequest req = new UpdateUserRequest();
            req.setDisplayName("Updated");
            when(userService.updateUser("u-1", req, UserRoleEnum.SYSTEM_ADMIN)).thenReturn(testUser);

            // Mock UserDetails with SYSTEM_ADMIN authority
            org.springframework.security.core.userdetails.UserDetails mockUserDetails =
                    org.springframework.security.core.userdetails.User.builder()
                            .username("admin")
                            .password("pass")
                            .authorities("ROLE_SYSTEM_ADMIN")
                            .build();

            ResponseEntity<Result<UserDTO>> response = userController.updateUser("u-1", req, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("updatePassword")
    class UpdatePasswordTests {

        @Test
        @DisplayName("使用明文密码更新")
        void shouldUpdatePasswordWithPlain() {
            Map<String, String> request = Map.of(
                    "oldPassword", "old",
                    "newPassword", "new"
            );
            when(rsaCryptoUtil.isEncrypted("old")).thenReturn(false);
            doNothing().when(userService).updatePassword("u-1", "old", "new");

            ResponseEntity<Result<Void>> response = userController.updatePassword("u-1", request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("使用加密密码更新时自动解密")
        void shouldDecryptAndUpdate() {
            Map<String, String> request = Map.of(
                    "oldPassword", "oldEnc",
                    "newPassword", "newEnc"
            );
            when(rsaCryptoUtil.isEncrypted("oldEnc")).thenReturn(true);
            when(rsaCryptoUtil.isEncrypted("newEnc")).thenReturn(true);
            when(rsaCryptoUtil.decrypt("oldEnc")).thenReturn("oldPlain");
            when(rsaCryptoUtil.decrypt("newEnc")).thenReturn("newPlain");
            doNothing().when(userService).updatePassword("u-1", "oldPlain", "newPlain");

            ResponseEntity<Result<Void>> response = userController.updatePassword("u-1", request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(userService).updatePassword("u-1", "oldPlain", "newPlain");
        }

        @Test
        @DisplayName("旧密码RSA解密失败时拒绝请求而非静默降级")
        void shouldRejectWhenOldPasswordRsaDecryptFails() {
            Map<String, String> request = Map.of(
                    "oldPassword", "oldEnc",
                    "newPassword", "newPlain"
            );
            when(rsaCryptoUtil.isEncrypted("oldEnc")).thenReturn(true);
            when(rsaCryptoUtil.decrypt("oldEnc")).thenThrow(new RuntimeException("Decryption failed"));
            when(rsaCryptoUtil.isEncrypted("newPlain")).thenReturn(false);

            BizException ex = assertThrows(BizException.class,
                    () -> userController.updatePassword("u-1", request));
            assertEquals(ErrorCode.INVALID_PARAMETER, ex.getErrorCode());
            verify(userService, never()).updatePassword(any(), any(), any());
        }

        @Test
        @DisplayName("新密码RSA解密失败时拒绝请求而非静默降级")
        void shouldRejectWhenNewPasswordRsaDecryptFails() {
            Map<String, String> request = Map.of(
                    "oldPassword", "oldPlain",
                    "newPassword", "newEnc"
            );
            when(rsaCryptoUtil.isEncrypted("oldPlain")).thenReturn(false);
            when(rsaCryptoUtil.isEncrypted("newEnc")).thenReturn(true);
            when(rsaCryptoUtil.decrypt("newEnc")).thenThrow(new RuntimeException("Decryption failed"));

            BizException ex = assertThrows(BizException.class,
                    () -> userController.updatePassword("u-1", request));
            assertEquals(ErrorCode.INVALID_PARAMETER, ex.getErrorCode());
            verify(userService, never()).updatePassword(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("deleteUser")
    class DeleteUserTests {

        @Test
        @DisplayName("成功删除")
        void shouldDeleteUser() {
            doNothing().when(userService).deleteUser("u-1");

            ResponseEntity<Result<Void>> response = userController.deleteUser("u-1");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("删除成功", response.getBody().getMessage());
        }
    }

    @Nested
    @DisplayName("assignDepartments")
    class AssignDepartmentsTests {

        @Test
        @DisplayName("成功分配部门")
        void shouldAssignDepartments() {
            doNothing().when(userService).assignDepartments("u-1", List.of("d-1", "d-2"));

            ResponseEntity<Result<Void>> response = userController.assignDepartments(
                    "u-1", List.of("d-1", "d-2"), userDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(userService).assignDepartments("u-1", List.of("d-1", "d-2"));
        }

        @Test
        @DisplayName("userDetails 为 null 时抛异常")
        void shouldThrowWhenNoUserDetails() {
            assertThrows(BizException.class,
                    () -> userController.assignDepartments("u-1", List.of("d-1"), null));
        }
    }
}
