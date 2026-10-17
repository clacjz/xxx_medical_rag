package com.clacjz.medical.rag.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.LoginRequest;
import com.clacjz.medical.rag.model.dto.LoginResponse;
import com.clacjz.medical.rag.model.dto.RegisterRequest;
import com.clacjz.medical.rag.model.dto.UserDTO;
import com.clacjz.medical.rag.model.entity.Department;
import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.entity.UserDepartment;
import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import com.clacjz.medical.rag.model.enums.UserStatusEnums;
import com.clacjz.medical.rag.mapper.DepartmentMapper;
import com.clacjz.medical.rag.mapper.UserDepartmentMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.security.JwtTokenProvider;
import com.clacjz.medical.rag.util.RequestUtils;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 单元测试")
class AuthServiceTest {

    @Mock
    private UserMapper userRepository;

    @Mock
    private DepartmentMapper departmentRepository;

    @Mock
    private UserDepartmentMapper userDepartmentMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private AuditService auditService;

    @Mock
    private RequestUtils requestUtils;

    @InjectMocks
    private AuthService authService;

    private User activeUser;
    private User disabledUser;
    private User lockedUser;
    private LoginRequest loginRequest;
    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .id(UUID.randomUUID().toString())
                .username("testuser")
                .email("test@example.com")
                .passwordHash("encodedPassword")
                .displayName("Test User")
                .role(UserRoleEnum.DEPT_USER)
                .status(UserStatusEnums.ACTIVE)
                .loginFailCount(0)
                .build();

        disabledUser = User.builder()
                .id(UUID.randomUUID().toString())
                .username("disableduser")
                .email("disabled@example.com")
                .passwordHash("encodedPassword")
                .displayName("Disabled User")
                .role(UserRoleEnum.DEPT_USER)
                .status(UserStatusEnums.DISABLED)
                .loginFailCount(0)
                .build();

        lockedUser = User.builder()
                .id(UUID.randomUUID().toString())
                .username("lockeduser")
                .email("locked@example.com")
                .passwordHash("encodedPassword")
                .displayName("Locked User")
                .role(UserRoleEnum.DEPT_USER)
                .status(UserStatusEnums.ACTIVE)
                .loginFailCount(5)
                .lockUntil(LocalDateTime.now().plusMinutes(30))
                .build();

        loginRequest = LoginRequest.builder()
                .username("testuser")
                .password("password123")
                .build();

        registerRequest = RegisterRequest.builder()
                .username("newuser")
                .email("new@example.com")
                .password("password123")
                .build();
    }

    @Nested
    @DisplayName("登录测试")
    class LoginTests {

        @Test
        @DisplayName("登录成功 - 正常凭据")
        void login_Success_WithValidCredentials() {
            when(requestUtils.getClientIp()).thenReturn("127.0.0.1");
            when(requestUtils.getUserAgent()).thenReturn("Mozilla/5.0");
            when(userRepository.selectByUsernameWithRoles("testuser")).thenReturn(activeUser);
            when(passwordEncoder.matches("password123", "encodedPassword")).thenReturn(true);
            when(userRepository.update(any(), any(UpdateWrapper.class))).thenReturn(1);
            when(jwtTokenProvider.generateAccessToken(anyString(), anyString(), anyString()))
                    .thenReturn("accessToken");
            when(jwtTokenProvider.generateRefreshToken(anyString())).thenReturn("refreshToken");
            when(jwtTokenProvider.getExpiration()).thenReturn(7200000L);

            LoginResponse response = authService.login(loginRequest);

            assertNotNull(response);
            assertEquals("testuser", response.getUsername());
            assertEquals("accessToken", response.getToken());
            assertEquals("refreshToken", response.getRefreshToken());
            verify(tokenBlacklistService).revokeAllUserTokens(activeUser.getId());
            verify(auditService).logLogin(eq(activeUser.getId()), eq("testuser"), eq("Test User"),
                    eq("127.0.0.1"), eq("Mozilla/5.0"), eq(true), anyString());
        }

        @Test
        @DisplayName("登录失败 - 用户不存在")
        void login_Failure_UserNotFound() {
            when(requestUtils.getClientIp()).thenReturn("127.0.0.1");
            when(requestUtils.getUserAgent()).thenReturn("Mozilla/5.0");
            when(userRepository.selectByUsernameWithRoles("nonexistent")).thenReturn(null);

            loginRequest.setUsername("nonexistent");
            BizException exception = assertThrows(BizException.class,
                    () -> authService.login(loginRequest));

            assertEquals(ErrorCode.AUTHENTICATION_FAILED, exception.getErrorCode());
            verify(auditService).logLogin(isNull(), eq("nonexistent"), isNull(),
                    eq("127.0.0.1"), eq("Mozilla/5.0"), eq(false), anyString());
        }

        @Test
        @DisplayName("登录失败 - 账户已禁用")
        void login_Failure_AccountDisabled() {
            when(requestUtils.getClientIp()).thenReturn("127.0.0.1");
            when(requestUtils.getUserAgent()).thenReturn("Mozilla/5.0");
            when(userRepository.selectByUsernameWithRoles("disableduser")).thenReturn(disabledUser);

            loginRequest.setUsername("disableduser");
            BizException exception = assertThrows(BizException.class,
                    () -> authService.login(loginRequest));

            assertEquals(ErrorCode.ACCOUNT_DISABLED, exception.getErrorCode());
        }

        @Test
        @DisplayName("登录失败 - 账户已锁定")
        void login_Failure_AccountLocked() {
            when(requestUtils.getClientIp()).thenReturn("127.0.0.1");
            when(requestUtils.getUserAgent()).thenReturn("Mozilla/5.0");
            when(userRepository.selectByUsernameWithRoles("lockeduser")).thenReturn(lockedUser);

            loginRequest.setUsername("lockeduser");
            BizException exception = assertThrows(BizException.class,
                    () -> authService.login(loginRequest));

            assertEquals(ErrorCode.ACCOUNT_LOCKED, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("30分钟"));
        }

        @Test
        @DisplayName("登录失败 - 密码错误")
        void login_Failure_WrongPassword() {
            when(requestUtils.getClientIp()).thenReturn("127.0.0.1");
            when(requestUtils.getUserAgent()).thenReturn("Mozilla/5.0");
            when(userRepository.selectByUsernameWithRoles("testuser")).thenReturn(activeUser);
            when(passwordEncoder.matches("wrongpassword", "encodedPassword")).thenReturn(false);
            when(userRepository.updateById(any(User.class))).thenReturn(1);

            loginRequest.setPassword("wrongpassword");
            BizException exception = assertThrows(BizException.class,
                    () -> authService.login(loginRequest));

            assertEquals(ErrorCode.AUTHENTICATION_FAILED, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("剩余"));
        }

        @Test
        @DisplayName("登录失败 - 连续5次密码错误后账户被锁定")
        void login_Failure_AccountLockedAfterFiveAttempts() {
            activeUser.setLoginFailCount(4);
            when(requestUtils.getClientIp()).thenReturn("127.0.0.1");
            when(requestUtils.getUserAgent()).thenReturn("Mozilla/5.0");
            when(userRepository.selectByUsernameWithRoles("testuser")).thenReturn(activeUser);
            when(passwordEncoder.matches("wrongpassword", "encodedPassword")).thenReturn(false);
            when(userRepository.updateById(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                return 1;
            });

            loginRequest.setPassword("wrongpassword");
            BizException exception = assertThrows(BizException.class,
                    () -> authService.login(loginRequest));

            assertEquals(ErrorCode.ACCOUNT_LOCKED, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("锁定30分钟"));
        }

        @Test
        @DisplayName("锁定时间到期后登录成功")
        void login_Success_AfterLockExpires() {
            lockedUser.setLockUntil(LocalDateTime.now().minusMinutes(1));
            lockedUser.setLoginFailCount(5);

            when(requestUtils.getClientIp()).thenReturn("127.0.0.1");
            when(requestUtils.getUserAgent()).thenReturn("Mozilla/5.0");
            when(userRepository.selectByUsernameWithRoles("lockeduser")).thenReturn(lockedUser);
            when(passwordEncoder.matches("password123", "encodedPassword")).thenReturn(true);
            when(userRepository.update(any(), any(UpdateWrapper.class))).thenReturn(1);
            when(jwtTokenProvider.generateAccessToken(anyString(), anyString(), anyString()))
                    .thenReturn("accessToken");
            when(jwtTokenProvider.generateRefreshToken(anyString())).thenReturn("refreshToken");
            when(jwtTokenProvider.getExpiration()).thenReturn(7200000L);

            loginRequest.setUsername("lockeduser");
            LoginResponse response = authService.login(loginRequest);

            assertNotNull(response);
            // 使用UpdateWrapper进行选择性更新（loginSuccess时1次）
            verify(userRepository, times(1)).update(any(), any(UpdateWrapper.class));
        }
    }

    @Nested
    @DisplayName("注册测试")
    class RegisterTests {

        @Test
        @DisplayName("注册成功 - 新用户")
        void register_Success_NewUser() {
            when(requestUtils.getClientIp()).thenReturn("127.0.0.1");
            when(requestUtils.getUserAgent()).thenReturn("Mozilla/5.0");
            when(userRepository.existsByUsername("newuser")).thenReturn(0);
            when(userRepository.existsByEmail("new@example.com")).thenReturn(0);
            when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
            when(userRepository.insert(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(UUID.randomUUID().toString());
                return 1;
            });
            when(departmentRepository.selectByIsDefaultTrue()).thenReturn(null);
            when(jwtTokenProvider.generateAccessToken(anyString(), anyString(), anyString()))
                    .thenReturn("accessToken");
            when(jwtTokenProvider.generateRefreshToken(anyString())).thenReturn("refreshToken");
            when(jwtTokenProvider.getExpiration()).thenReturn(7200000L);

            LoginResponse response = authService.register(registerRequest);

            assertNotNull(response);
            assertEquals("newuser", response.getUsername());
            assertEquals("accessToken", response.getToken());
            verify(auditService).logRegister(anyString(), eq("newuser"), anyString(),
                    eq("127.0.0.1"), eq("Mozilla/5.0"), eq(true));
        }

        @Test
        @DisplayName("注册成功 - 自动分配到默认部门")
        void register_Success_AutoAssignToDefaultDepartment() {
            Department defaultDept = Department.builder()
                    .id(UUID.randomUUID().toString())
                    .code("DEFAULT")
                    .name("默认部门")
                    .isDefault(true)
                    .build();

            when(requestUtils.getClientIp()).thenReturn("127.0.0.1");
            when(requestUtils.getUserAgent()).thenReturn("Mozilla/5.0");
            when(userRepository.existsByUsername("newuser")).thenReturn(0);
            when(userRepository.existsByEmail("new@example.com")).thenReturn(0);
            when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
            when(userRepository.insert(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(UUID.randomUUID().toString());
                return 1;
            });
            when(departmentRepository.selectByIsDefaultTrue()).thenReturn(defaultDept);
            when(userDepartmentMapper.insert(any(UserDepartment.class))).thenAnswer(invocation -> 1);
            when(jwtTokenProvider.generateAccessToken(anyString(), anyString(), anyString()))
                    .thenReturn("accessToken");
            when(jwtTokenProvider.generateRefreshToken(anyString())).thenReturn("refreshToken");
            when(jwtTokenProvider.getExpiration()).thenReturn(7200000L);

            authService.register(registerRequest);

            verify(userDepartmentMapper).insert(any(UserDepartment.class));
        }

        @Test
        @DisplayName("注册失败 - 用户名已存在")
        void register_Failure_UsernameExists() {
            when(requestUtils.getClientIp()).thenReturn("127.0.0.1");
            when(requestUtils.getUserAgent()).thenReturn("Mozilla/5.0");
            when(userRepository.existsByUsername("existinguser")).thenReturn(1);

            registerRequest.setUsername("existinguser");
            BizException exception = assertThrows(BizException.class,
                    () -> authService.register(registerRequest));

            assertEquals(ErrorCode.USER_ALREADY_EXISTS, exception.getErrorCode());
        }

        @Test
        @DisplayName("注册失败 - 邮箱已存在")
        void register_Failure_EmailExists() {
            when(requestUtils.getClientIp()).thenReturn("127.0.0.1");
            when(requestUtils.getUserAgent()).thenReturn("Mozilla/5.0");
            when(userRepository.existsByUsername("newuser")).thenReturn(0);
            when(userRepository.existsByEmail("existing@example.com")).thenReturn(1);

            registerRequest.setEmail("existing@example.com");
            BizException exception = assertThrows(BizException.class,
                    () -> authService.register(registerRequest));

            assertEquals(ErrorCode.USER_ALREADY_EXISTS, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("邮箱"));
        }
    }

    @Nested
    @DisplayName("Token刷新测试")
    class RefreshTokenTests {

        @Test
        @DisplayName("刷新Token成功")
        void refreshToken_Success() {
            String refreshToken = "validRefreshToken";
            when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
            when(jwtTokenProvider.isRefreshToken(refreshToken)).thenReturn(true);
            when(jwtTokenProvider.getJtiFromToken(refreshToken)).thenReturn("jti-123");
            when(tokenBlacklistService.isRefreshTokenBlacklisted("jti-123")).thenReturn(false);
            when(jwtTokenProvider.getUserIdFromToken(refreshToken)).thenReturn(activeUser.getId());
            when(jwtTokenProvider.getExpirationMillisFromToken(refreshToken)).thenReturn(3600000L);
            when(userRepository.selectByIdWithRoles(activeUser.getId())).thenReturn(activeUser);
            when(jwtTokenProvider.generateAccessToken(anyString(), anyString(), anyString()))
                    .thenReturn("newAccessToken");
            when(jwtTokenProvider.generateRefreshToken(anyString())).thenReturn("newRefreshToken");
            when(jwtTokenProvider.getExpiration()).thenReturn(7200000L);

            LoginResponse response = authService.refreshToken(refreshToken);

            assertNotNull(response);
            assertEquals("newAccessToken", response.getToken());
            verify(tokenBlacklistService).blacklistRefreshToken(eq("jti-123"), anyLong());
        }

        @Test
        @DisplayName("刷新Token失败 - Token无效")
        void refreshToken_Failure_InvalidToken() {
            when(jwtTokenProvider.validateToken("invalidToken")).thenReturn(false);

            BizException exception = assertThrows(BizException.class,
                    () -> authService.refreshToken("invalidToken"));

            assertEquals(ErrorCode.INVALID_TOKEN, exception.getErrorCode());
        }

        @Test
        @DisplayName("刷新Token失败 - 不是RefreshToken")
        void refreshToken_Failure_NotRefreshToken() {
            when(jwtTokenProvider.validateToken("accessToken")).thenReturn(true);
            when(jwtTokenProvider.isRefreshToken("accessToken")).thenReturn(false);

            BizException exception = assertThrows(BizException.class,
                    () -> authService.refreshToken("accessToken"));

            assertEquals(ErrorCode.INVALID_TOKEN, exception.getErrorCode());
        }

        @Test
        @DisplayName("刷新Token失败 - Token已被使用")
        void refreshToken_Failure_TokenReused() {
            when(jwtTokenProvider.validateToken("reusedToken")).thenReturn(true);
            when(jwtTokenProvider.isRefreshToken("reusedToken")).thenReturn(true);
            when(jwtTokenProvider.getJtiFromToken("reusedToken")).thenReturn("jti-reused");
            when(tokenBlacklistService.isRefreshTokenBlacklisted("jti-reused")).thenReturn(true);

            BizException exception = assertThrows(BizException.class,
                    () -> authService.refreshToken("reusedToken"));

            assertEquals(ErrorCode.INVALID_TOKEN, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("已被使用"));
        }

        @Test
        @DisplayName("刷新Token失败 - 用户不存在")
        void refreshToken_Failure_UserNotFound() {
            when(jwtTokenProvider.validateToken("validToken")).thenReturn(true);
            when(jwtTokenProvider.isRefreshToken("validToken")).thenReturn(true);
            when(jwtTokenProvider.getJtiFromToken("validToken")).thenReturn("jti-123");
            when(tokenBlacklistService.isRefreshTokenBlacklisted("jti-123")).thenReturn(false);
            when(jwtTokenProvider.getUserIdFromToken("validToken")).thenReturn("nonexistent");
            when(userRepository.selectByIdWithRoles("nonexistent")).thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> authService.refreshToken("validToken"));

            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("刷新Token失败 - 账户已禁用")
        void refreshToken_Failure_AccountDisabled() {
            disabledUser.setId("user-disabled");
            when(jwtTokenProvider.validateToken("validToken")).thenReturn(true);
            when(jwtTokenProvider.isRefreshToken("validToken")).thenReturn(true);
            when(jwtTokenProvider.getJtiFromToken("validToken")).thenReturn("jti-123");
            when(tokenBlacklistService.isRefreshTokenBlacklisted("jti-123")).thenReturn(false);
            when(jwtTokenProvider.getUserIdFromToken("validToken")).thenReturn("user-disabled");
            when(userRepository.selectByIdWithRoles("user-disabled")).thenReturn(disabledUser);

            BizException exception = assertThrows(BizException.class,
                    () -> authService.refreshToken("validToken"));

            assertEquals(ErrorCode.ACCOUNT_DISABLED, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("获取当前用户测试")
    class GetCurrentUserTests {

        @Test
        @DisplayName("获取当前用户成功")
        void getCurrentUser_Success() {
            when(userRepository.selectByIdWithRoles(activeUser.getId())).thenReturn(activeUser);

            UserDTO userDTO = authService.getCurrentUser(activeUser.getId());

            assertNotNull(userDTO);
            assertEquals("testuser", userDTO.getUsername());
        }

        @Test
        @DisplayName("获取当前用户失败 - 用户不存在")
        void getCurrentUser_Failure_UserNotFound() {
            when(userRepository.selectByIdWithRoles("nonexistent")).thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> authService.getCurrentUser("nonexistent"));

            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("登出测试")
    class LogoutTests {

        @Test
        @DisplayName("登出成功")
        void logout_Success() {
            String accessToken = "validAccessToken";
            String refreshToken = "validRefreshToken";

            when(requestUtils.getClientIp()).thenReturn("127.0.0.1");
            when(requestUtils.getUserAgent()).thenReturn("Mozilla/5.0");
            when(userRepository.selectById(activeUser.getId())).thenReturn(activeUser);
            when(jwtTokenProvider.validateToken(accessToken)).thenReturn(true);
            when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
            when(jwtTokenProvider.isAccessToken(accessToken)).thenReturn(true);
            when(jwtTokenProvider.isRefreshToken(refreshToken)).thenReturn(true);
            when(jwtTokenProvider.getJtiFromToken(accessToken)).thenReturn("access-jti");
            when(jwtTokenProvider.getJtiFromToken(refreshToken)).thenReturn("refresh-jti");
            when(jwtTokenProvider.getExpirationMillisFromToken(accessToken)).thenReturn(3600000L);
            when(jwtTokenProvider.getExpirationMillisFromToken(refreshToken)).thenReturn(604800000L);

            assertDoesNotThrow(() -> authService.logout(activeUser.getId(), accessToken, refreshToken));

            verify(tokenBlacklistService).blacklistAccessToken("access-jti", 3600000L);
            verify(tokenBlacklistService).blacklistRefreshToken("refresh-jti", 604800000L);
            verify(auditService).logLogout(eq(activeUser.getId()), eq("testuser"), eq("Test User"),
                    eq("127.0.0.1"), eq("Mozilla/5.0"));
        }

        @Test
        @DisplayName("登出成功 - 用户不存在")
        void logout_Success_UserNotExists() {
            when(requestUtils.getClientIp()).thenReturn("127.0.0.1");
            when(requestUtils.getUserAgent()).thenReturn("Mozilla/5.0");
            when(userRepository.selectById("nonexistent")).thenReturn(null);

            assertDoesNotThrow(() -> authService.logout("nonexistent", null, null));

            verify(auditService).logLogout(eq("nonexistent"), eq("nonexistent"), isNull(),
                    eq("127.0.0.1"), eq("Mozilla/5.0"));
        }

        @Test
        @DisplayName("登出成功 - 无效Token")
        void logout_Success_InvalidTokens() {
            when(requestUtils.getClientIp()).thenReturn("127.0.0.1");
            when(requestUtils.getUserAgent()).thenReturn("Mozilla/5.0");
            when(userRepository.selectById(activeUser.getId())).thenReturn(activeUser);
            when(jwtTokenProvider.validateToken("invalidAccess")).thenReturn(false);
            when(jwtTokenProvider.validateToken("invalidRefresh")).thenReturn(false);

            assertDoesNotThrow(() -> authService.logout(activeUser.getId(), "invalidAccess", "invalidRefresh"));

            verify(tokenBlacklistService, never()).blacklistAccessToken(anyString(), anyLong());
            verify(tokenBlacklistService, never()).blacklistRefreshToken(anyString(), anyLong());
        }

        @Test
        @DisplayName("登出成功 - 部分Token有效")
        void logout_Success_PartialValidTokens() {
            when(requestUtils.getClientIp()).thenReturn("127.0.0.1");
            when(requestUtils.getUserAgent()).thenReturn("Mozilla/5.0");
            when(userRepository.selectById(activeUser.getId())).thenReturn(activeUser);
            when(jwtTokenProvider.validateToken("validAccess")).thenReturn(true);
            when(jwtTokenProvider.validateToken("invalidRefresh")).thenReturn(false);
            when(jwtTokenProvider.isAccessToken("validAccess")).thenReturn(true);
            when(jwtTokenProvider.getJtiFromToken("validAccess")).thenReturn("access-jti");
            when(jwtTokenProvider.getExpirationMillisFromToken("validAccess")).thenReturn(3600000L);

            assertDoesNotThrow(() -> authService.logout(activeUser.getId(), "validAccess", "invalidRefresh"));

            verify(tokenBlacklistService).blacklistAccessToken("access-jti", 3600000L);
            verify(tokenBlacklistService, never()).blacklistRefreshToken(anyString(), anyLong());
        }

        @Test
        @DisplayName("登出成功 - 过期accessToken的jti从自定义claim读取")
        void logout_ExpiredAccessToken_ReadsJtiFromCustomClaim() {
            when(requestUtils.getClientIp()).thenReturn("127.0.0.1");
            when(requestUtils.getUserAgent()).thenReturn("Mozilla/5.0");
            when(userRepository.selectById(activeUser.getId())).thenReturn(activeUser);

            // 模拟validateToken抛出ExpiredJwtException，claims中jti存储为自定义claim
            java.util.Map<String, Object> claimsMap = new java.util.HashMap<>();
            claimsMap.put("jti", "custom-jti-123");
            claimsMap.put("type", "access");
            claimsMap.put(io.jsonwebtoken.Claims.EXPIRATION, new java.util.Date(System.currentTimeMillis() - 60000));
            io.jsonwebtoken.impl.DefaultClaims expiredClaims = new io.jsonwebtoken.impl.DefaultClaims(claimsMap);
            io.jsonwebtoken.ExpiredJwtException expiredEx = new io.jsonwebtoken.ExpiredJwtException(
                    null, expiredClaims, "Token expired", null);

            when(jwtTokenProvider.validateToken("expiredAccess")).thenThrow(expiredEx);
            when(jwtTokenProvider.validateToken("validRefresh")).thenReturn(true);
            when(jwtTokenProvider.isRefreshToken("validRefresh")).thenReturn(true);
            when(jwtTokenProvider.getJtiFromToken("validRefresh")).thenReturn("refresh-jti");
            when(jwtTokenProvider.getExpirationMillisFromToken("validRefresh")).thenReturn(604800000L);

            assertDoesNotThrow(() -> authService.logout(activeUser.getId(), "expiredAccess", "validRefresh"));

            // 验证从自定义claim读取的jti被正确传递给黑名单方法
            verify(tokenBlacklistService).blacklistAccessToken(eq("custom-jti-123"), anyLong());
            verify(tokenBlacklistService).blacklistRefreshToken("refresh-jti", 604800000L);
        }
    }

    @Nested
    @DisplayName("LOCKED状态枚举值登录测试")
    class LockedStatusEnumLoginTests {

        @Test
        @DisplayName("status=LOCKED的用户应被拒绝登录")
        void login_Failure_StatusLocked() {
            User statusLockedUser = User.builder()
                    .id(UUID.randomUUID().toString())
                    .username("statuslocked")
                    .email("statuslocked@example.com")
                    .passwordHash("encodedPassword")
                    .displayName("Status Locked User")
                    .role(UserRoleEnum.DEPT_USER)
                    .status(UserStatusEnums.LOCKED)
                    .loginFailCount(0)
                    .build();

            when(requestUtils.getClientIp()).thenReturn("127.0.0.1");
            when(requestUtils.getUserAgent()).thenReturn("Mozilla/5.0");
            when(userRepository.selectByUsernameWithRoles("statuslocked")).thenReturn(statusLockedUser);

            LoginRequest req = LoginRequest.builder()
                    .username("statuslocked")
                    .password("password123")
                    .build();

            BizException exception = assertThrows(BizException.class,
                    () -> authService.login(req));

            assertEquals(ErrorCode.ACCOUNT_LOCKED, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("密码重置复杂度校验测试")
    class ResetPasswordComplexityTests {

        @Test
        @DisplayName("resetPassword应校验新密码复杂度")
        void resetPassword_ShouldValidateComplexity() {
            BizException exception = assertThrows(BizException.class,
                    () -> authService.resetPassword("user-id", "1"));

            assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("8位"));
        }
    }
}
