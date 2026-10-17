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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 单元测试")
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private DepartmentMapper departmentMapper;

    @Mock
    private UserDepartmentMapper userDepartmentMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private CollectionUserPermissionMapper collectionUserPermissionMapper;

    @Mock
    private DocumentUserPermissionMapper documentUserPermissionMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private DefaultDepartmentProperties defaultDepartmentProperties;

    @Mock
    private DepartmentService departmentService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private PermissionCacheService permissionCacheService;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private User systemAdminUser;
    private Department defaultDepartment;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID().toString())
                .username("testuser")
                .email("test@example.com")
                .passwordHash("encodedPassword")
                .displayName("Test User")
                .role(UserRoleEnum.DEPT_USER)
                .status(UserStatusEnums.ACTIVE)
                .loginFailCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        systemAdminUser = User.builder()
                .id(UUID.randomUUID().toString())
                .username("admin")
                .email("admin@example.com")
                .passwordHash("encodedPassword")
                .displayName("System Admin")
                .role(UserRoleEnum.SYSTEM_ADMIN)
                .status(UserStatusEnums.ACTIVE)
                .loginFailCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        defaultDepartment = Department.builder()
                .id(UUID.randomUUID().toString())
                .code("DEFAULT")
                .name("默认部门")
                .isDefault(true)
                .build();
    }

    @Nested
    @DisplayName("获取用户列表测试")
    class GetUserListTests {

        @Test
        @DisplayName("获取用户列表 - 无过滤条件")
        void getUserList_WithoutFilters() {
            Page<User> userPage = new Page<>(1, 10);
            userPage.setRecords(Arrays.asList(testUser, systemAdminUser));
            userPage.setTotal(2);

            when(userMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(userPage);

            IPage<UserDTO> result = userService.getUserList(0, 10, null, null);

            assertNotNull(result);
            assertEquals(2, result.getTotal());
            verify(userMapper).selectPage(any(Page.class), any(QueryWrapper.class));
        }

        @Test
        @DisplayName("获取用户列表 - 按角色过滤")
        void getUserList_WithRoleFilter() {
            Page<User> userPage = new Page<>(1, 10);
            userPage.setRecords(Arrays.asList(testUser));
            userPage.setTotal(1);

            when(userMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(userPage);

            IPage<UserDTO> result = userService.getUserList(0, 10, UserRoleEnum.DEPT_USER, null);

            assertNotNull(result);
            assertEquals(1, result.getTotal());
        }

        @Test
        @DisplayName("获取用户列表 - 按状态过滤")
        void getUserList_WithStatusFilter() {
            testUser.setStatus(UserStatusEnums.ACTIVE);
            Page<User> userPage = new Page<>(1, 10);
            userPage.setRecords(Arrays.asList(testUser));
            userPage.setTotal(1);

            when(userMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(userPage);

            IPage<UserDTO> result = userService.getUserList(0, 10, null, UserStatusEnums.ACTIVE);

            assertNotNull(result);
            assertEquals(1, result.getTotal());
        }

        @Test
        @DisplayName("获取用户列表 - 按角色和状态过滤")
        void getUserList_WithBothFilters() {
            Page<User> userPage = new Page<>(1, 10);
            userPage.setRecords(Arrays.asList(testUser));
            userPage.setTotal(1);

            when(userMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(userPage);

            IPage<UserDTO> result = userService.getUserList(0, 10, UserRoleEnum.DEPT_USER, UserStatusEnums.ACTIVE);

            assertNotNull(result);
            assertEquals(1, result.getTotal());
        }
    }

    @Nested
    @DisplayName("获取单个用户测试")
    class GetUserByIdTests {

        @Test
        @DisplayName("获取用户成功")
        void getUserById_Success() {
            when(userMapper.selectById(testUser.getId())).thenReturn(testUser);

            UserDTO result = userService.getUserById(testUser.getId());

            assertNotNull(result);
            assertEquals(testUser.getUsername(), result.getUsername());
            assertEquals(testUser.getEmail(), result.getEmail());
        }

        @Test
        @DisplayName("获取用户失败 - 用户不存在")
        void getUserById_NotFound() {
            when(userMapper.selectById("nonexistent")).thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> userService.getUserById("nonexistent"));

            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("创建用户测试")
    class CreateUserTests {

        @Test
        @DisplayName("创建用户成功 - 默认角色")
        void createUser_Success_DefaultRole() {
            when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
            when(passwordEncoder.encode("Password123")).thenReturn("encodedPassword");
            when(userMapper.insert(any(User.class))).thenReturn(1);
            when(defaultDepartmentProperties.isEnabled()).thenReturn(false);

            UserDTO result = userService.createUser("newuser", "Password123", "new@example.com", null);

            assertNotNull(result);
            assertEquals("newuser", result.getUsername());
            assertEquals(UserRoleEnum.DEPT_USER, result.getRole());
            verify(userMapper).insert(any(User.class));
        }

        @Test
        @DisplayName("创建用户成功 - 指定系统管理员角色")
        void createUser_Success_SystemAdminRole() {
            when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
            when(passwordEncoder.encode("Password123")).thenReturn("encodedPassword");
            when(userMapper.insert(any(User.class))).thenReturn(1);
            when(defaultDepartmentProperties.isEnabled()).thenReturn(false);

            UserDTO result = userService.createUser("newadmin", "Password123", "newadmin@example.com", "SYSTEM_ADMIN");

            assertNotNull(result);
            assertEquals(UserRoleEnum.SYSTEM_ADMIN, result.getRole());
        }

        @Test
        @DisplayName("创建用户成功 - 自动分配到默认部门")
        void createUser_Success_AutoAssignToDefaultDepartment() {
            when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
            when(passwordEncoder.encode("Password123")).thenReturn("encodedPassword");
            when(userMapper.insert(any(User.class))).thenReturn(1);
            when(defaultDepartmentProperties.isEnabled()).thenReturn(true);
            when(defaultDepartmentProperties.isAutoAssignNewUsers()).thenReturn(true);
            when(departmentMapper.selectOne(any(QueryWrapper.class))).thenReturn(defaultDepartment);
            when(userDepartmentMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
            when(userDepartmentMapper.insert(any(UserDepartment.class))).thenReturn(1);

            UserDTO result = userService.createUser("newuser", "Password123", "new@example.com", null);

            assertNotNull(result);
            verify(userDepartmentMapper).insert(any(UserDepartment.class));
        }

        @Test
        @DisplayName("创建用户失败 - 用户名已存在")
        void createUser_Failure_UsernameExists() {
            when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

            BizException exception = assertThrows(BizException.class,
                    () -> userService.createUser("existinguser", "Password123", "new@example.com", null));

            assertEquals(ErrorCode.USER_ALREADY_EXISTS, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("用户名"));
        }

        @Test
        @DisplayName("创建用户失败 - 邮箱已存在")
        void createUser_Failure_EmailExists() {
            when(userMapper.selectCount(any(QueryWrapper.class)))
                    .thenReturn(0L)  // username check
                    .thenReturn(1L);  // email check

            BizException exception = assertThrows(BizException.class,
                    () -> userService.createUser("newuser", "Password123", "existing@example.com", null));

            assertEquals(ErrorCode.USER_ALREADY_EXISTS, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("邮箱"));
        }

        @Test
        @DisplayName("创建用户失败 - 无效角色")
        void createUser_Failure_InvalidRole() {
            when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

            BizException exception = assertThrows(BizException.class,
                    () -> userService.createUser("newuser", "Password123", "new@example.com", "INVALID_ROLE"));

            assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("无效的角色"));
        }
    }

    @Nested
    @DisplayName("更新密码测试")
    class UpdatePasswordTests {

        @Test
        @DisplayName("更新密码成功")
        void updatePassword_Success() {
            when(userMapper.selectById(testUser.getId())).thenReturn(testUser);
            when(passwordEncoder.matches("oldPassword", "encodedPassword")).thenReturn(true);
            when(passwordEncoder.encode("NewPass123")).thenReturn("newEncodedPassword");
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            assertDoesNotThrow(() -> userService.updatePassword(testUser.getId(), "oldPassword", "NewPass123"));
            verify(userMapper).updateById(any(User.class));
        }

        @Test
        @DisplayName("更新密码失败 - 用户不存在")
        void updatePassword_Failure_UserNotFound() {
            when(userMapper.selectById("nonexistent")).thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> userService.updatePassword("nonexistent", "oldPassword", "newPassword"));

            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("更新密码失败 - 原密码错误")
        void updatePassword_Failure_WrongOldPassword() {
            when(userMapper.selectById(testUser.getId())).thenReturn(testUser);
            when(passwordEncoder.matches("wrongPassword", "encodedPassword")).thenReturn(false);

            BizException exception = assertThrows(BizException.class,
                    () -> userService.updatePassword(testUser.getId(), "wrongPassword", "newPassword"));

            assertEquals(ErrorCode.AUTHENTICATION_FAILED, exception.getErrorCode());
        }

        @Test
        @DisplayName("更新密码失败 - 新密码太短")
        void updatePassword_Failure_NewPasswordTooShort() {
            when(userMapper.selectById(testUser.getId())).thenReturn(testUser);
            when(passwordEncoder.matches("oldPassword", "encodedPassword")).thenReturn(true);

            BizException exception = assertThrows(BizException.class,
                    () -> userService.updatePassword(testUser.getId(), "oldPassword", "12345"));

            assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("8位"));
        }

        @Test
        @DisplayName("更新密码成功 - 不验证原密码（管理员重置）")
        void updatePassword_Success_AdminReset() {
            when(userMapper.selectById(testUser.getId())).thenReturn(testUser);
            when(passwordEncoder.matches("adminOldPassword", testUser.getPasswordHash())).thenReturn(true);
            when(passwordEncoder.encode("NewPass123")).thenReturn("newEncodedPassword");
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            assertDoesNotThrow(() -> userService.updatePassword(testUser.getId(), "adminOldPassword", "NewPass123"));
        }

        @Test
        @DisplayName("更新密码失败 - 旧密码为空应拒绝")
        void updatePassword_Fail_NullOldPassword() {
            when(userMapper.selectById(testUser.getId())).thenReturn(testUser);

            assertThrows(com.clacjz.medical.rag.exception.BizException.class,
                    () -> userService.updatePassword(testUser.getId(), null, "newPassword"));
        }
    }

    @Nested
    @DisplayName("更新用户测试")
    class UpdateUserTests {

        @Test
        @DisplayName("更新用户成功 - 更新显示名称")
        void updateUser_Success_UpdateDisplayName() {
            UpdateUserRequest request = new UpdateUserRequest();
            request.setDisplayName("New Display Name");

            when(userMapper.selectById(testUser.getId())).thenReturn(testUser);
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            UserDTO result = userService.updateUser(testUser.getId(), request, UserRoleEnum.SYSTEM_ADMIN);

            assertNotNull(result);
            verify(userMapper).updateById(any(User.class));
        }

        @Test
        @DisplayName("更新用户成功 - 更新邮箱")
        void updateUser_Success_UpdateEmail() {
            UpdateUserRequest request = new UpdateUserRequest();
            request.setEmail("newemail@example.com");

            when(userMapper.selectById(testUser.getId())).thenReturn(testUser);
            when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            UserDTO result = userService.updateUser(testUser.getId(), request, UserRoleEnum.SYSTEM_ADMIN);

            assertNotNull(result);
        }

        @Test
        @DisplayName("更新用户成功 - 更新角色")
        void updateUser_Success_UpdateRole() {
            UpdateUserRequest request = new UpdateUserRequest();
            request.setRole(UserRoleEnum.DEPT_ADMIN);

            when(userMapper.selectById(testUser.getId())).thenReturn(testUser);
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            UserDTO result = userService.updateUser(testUser.getId(), request, UserRoleEnum.SYSTEM_ADMIN);

            assertNotNull(result);
        }

        @Test
        @DisplayName("更新用户成功 - 启用账户时重置登录失败次数")
        void updateUser_Success_EnableAccount() {
            testUser.setLoginFailCount(5);
            testUser.setStatus(UserStatusEnums.DISABLED);

            UpdateUserRequest request = new UpdateUserRequest();
            request.setStatus(UserStatusEnums.ACTIVE);

            when(userMapper.selectById(testUser.getId())).thenReturn(testUser);
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            userService.updateUser(testUser.getId(), request, UserRoleEnum.SYSTEM_ADMIN);

            assertEquals(0, testUser.getLoginFailCount());
        }

        @Test
        @DisplayName("更新用户失败 - 邮箱已被使用")
        void updateUser_Failure_EmailAlreadyUsed() {
            UpdateUserRequest request = new UpdateUserRequest();
            request.setEmail("existing@example.com");

            when(userMapper.selectById(testUser.getId())).thenReturn(testUser);
            when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

            BizException exception = assertThrows(BizException.class,
                    () -> userService.updateUser(testUser.getId(), request, UserRoleEnum.SYSTEM_ADMIN));

            assertEquals(ErrorCode.USER_ALREADY_EXISTS, exception.getErrorCode());
        }

        @Test
        @DisplayName("更新用户失败 - 用户不存在")
        void updateUser_Failure_UserNotFound() {
            UpdateUserRequest request = new UpdateUserRequest();
            request.setDisplayName("New Name");

            when(userMapper.selectById("nonexistent")).thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> userService.updateUser("nonexistent", request, UserRoleEnum.SYSTEM_ADMIN));

            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("非管理员无法修改用户角色 - 角色提升防护")
        void updateUser_Failure_RoleEscalationPrevention() {
            UpdateUserRequest request = new UpdateUserRequest();
            request.setRole(UserRoleEnum.SYSTEM_ADMIN);

            when(userMapper.selectById(testUser.getId())).thenReturn(testUser);

            BizException exception = assertThrows(BizException.class,
                    () -> userService.updateUser(testUser.getId(), request, UserRoleEnum.DEPT_USER));

            assertEquals(ErrorCode.PERMISSION_DENIED, exception.getErrorCode());
        }

        @Test
        @DisplayName("非管理员无法修改用户状态")
        void updateUser_Failure_StatusChangePrevention() {
            UpdateUserRequest request = new UpdateUserRequest();
            request.setStatus(UserStatusEnums.DISABLED);

            when(userMapper.selectById(testUser.getId())).thenReturn(testUser);

            BizException exception = assertThrows(BizException.class,
                    () -> userService.updateUser(testUser.getId(), request, UserRoleEnum.DEPT_USER));

            assertEquals(ErrorCode.PERMISSION_DENIED, exception.getErrorCode());
        }

        @Test
        @DisplayName("系统管理员可以修改用户角色")
        void updateUser_Success_AdminCanChangeRole() {
            UpdateUserRequest request = new UpdateUserRequest();
            request.setRole(UserRoleEnum.DEPT_ADMIN);

            when(userMapper.selectById(testUser.getId())).thenReturn(testUser);
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            assertDoesNotThrow(() -> userService.updateUser(testUser.getId(), request, UserRoleEnum.SYSTEM_ADMIN));
            verify(userMapper).updateById(any(User.class));
        }

        @Test
        @DisplayName("更新密码成功后应吊销所有已签发Token")
        void updatePassword_Success_ShouldRevokeAllTokens() {
            when(userMapper.selectById(testUser.getId())).thenReturn(testUser);
            when(passwordEncoder.matches("oldPassword", "encodedPassword")).thenReturn(true);
            when(passwordEncoder.encode("NewPass123")).thenReturn("newEncodedPassword");
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            userService.updatePassword(testUser.getId(), "oldPassword", "NewPass123");

            verify(tokenBlacklistService).revokeAllUserTokens(testUser.getId());
        }
    }

    @Nested
    @DisplayName("删除用户测试")
    class DeleteUserTests {

        @Test
        @DisplayName("删除用户成功")
        void deleteUser_Success() {
            when(userMapper.selectById(testUser.getId())).thenReturn(testUser);
            when(userMapper.deleteById(testUser.getId())).thenReturn(1);

            assertDoesNotThrow(() -> userService.deleteUser(testUser.getId()));
            verify(userMapper).deleteById(testUser.getId());
            verify(tokenBlacklistService).revokeAllUserTokens(testUser.getId());
        }

        @Test
        @DisplayName("删除用户成功后应清除权限缓存")
        void deleteUser_Success_ShouldEvictPermissionCache() {
            when(userMapper.selectById(testUser.getId())).thenReturn(testUser);
            when(userMapper.deleteById(testUser.getId())).thenReturn(1);

            userService.deleteUser(testUser.getId());

            verify(permissionCacheService).evictUserPermissionCache(testUser.getId());
        }

        @Test
        @DisplayName("删除用户失败 - 用户不存在")
        void deleteUser_Failure_UserNotFound() {
            when(userMapper.selectById("nonexistent")).thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> userService.deleteUser("nonexistent"));

            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("统计用户测试")
    class CountUsersTests {

        @Test
        @DisplayName("统计用户总数成功")
        void countUsers_Success() {
            when(userMapper.selectCount(null)).thenReturn(100L);

            long count = userService.countUsers();

            assertEquals(100L, count);
        }

        @Test
        @DisplayName("按角色统计用户数成功")
        void countUsersByRole_Success() {
            when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

            long count = userService.countUsersByRole(UserRoleEnum.DEPT_USER);

            assertEquals(1L, count);
        }
    }

    @Nested
    @DisplayName("按用户名查找用户测试")
    class FindByUsernameTests {

        @Test
        @DisplayName("按用户名查找用户成功")
        void findByUsername_Success() {
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(testUser);

            Optional<User> result = userService.findByUsername("testuser");

            assertTrue(result.isPresent());
            assertEquals("testuser", result.get().getUsername());
        }

        @Test
        @DisplayName("按用户名查找用户失败 - 用户不存在")
        void findByUsername_NotFound() {
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

            Optional<User> result = userService.findByUsername("nonexistent");

            assertFalse(result.isPresent());
        }
    }
}
