package com.clacjz.medical.rag.security;

import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import com.clacjz.medical.rag.model.enums.UserStatusEnums;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.service.PermissionCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomUserDetailsService 单元测试")
class CustomUserDetailsServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PermissionCacheService permissionCacheService;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    private User activeUser;
    private User lockedUser;
    private User disabledUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .id("user-1")
                .username("testuser")
                .passwordHash("hashedPassword123")
                .displayName("Test User")
                .role(UserRoleEnum.DEPT_ADMIN)
                .status(UserStatusEnums.ACTIVE)
                .lockUntil(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        lockedUser = User.builder()
                .id("user-2")
                .username("lockeduser")
                .passwordHash("hashedPassword456")
                .displayName("Locked User")
                .role(UserRoleEnum.DEPT_USER)
                .status(UserStatusEnums.ACTIVE)
                .lockUntil(LocalDateTime.now().plusMinutes(30))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        disabledUser = User.builder()
                .id("user-3")
                .username("disableduser")
                .passwordHash("hashedPassword789")
                .displayName("Disabled User")
                .role(UserRoleEnum.DEPT_USER)
                .status(UserStatusEnums.DISABLED)
                .lockUntil(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("loadUserByUsername 测试")
    class LoadUserByUsernameTests {

        @Test
        @DisplayName("成功加载活跃用户")
        void loadUserByUsername_Success() {
            when(userMapper.selectByUsername("testuser")).thenReturn(activeUser);
            when(permissionCacheService.getUserPermissions("user-1")).thenReturn(Set.of("ROLE_DEPT_ADMIN"));

            UserDetails userDetails = userDetailsService.loadUserByUsername("testuser");

            assertNotNull(userDetails);
            assertEquals("testuser", userDetails.getUsername());
            assertEquals("hashedPassword123", userDetails.getPassword());
            assertTrue(userDetails.isEnabled());
            assertTrue(userDetails.isAccountNonLocked());
            assertEquals(1, userDetails.getAuthorities().size());
            assertTrue(userDetails.getAuthorities()
                    .contains(new SimpleGrantedAuthority("ROLE_DEPT_ADMIN")));
        }

        @Test
        @DisplayName("用户不存在抛出异常")
        void loadUserByUsername_UserNotFound() {
            when(userMapper.selectByUsername("nonexistent")).thenReturn(null);

            UsernameNotFoundException exception = assertThrows(
                    UsernameNotFoundException.class,
                    () -> userDetailsService.loadUserByUsername("nonexistent")
            );

            assertTrue(exception.getMessage().contains("用户不存在"));
            assertTrue(exception.getMessage().contains("nonexistent"));
        }

        @Test
        @DisplayName("加载已锁定用户 - 账户被锁定")
        void loadUserByUsername_LockedUser() {
            when(userMapper.selectByUsername("lockeduser")).thenReturn(lockedUser);
            when(permissionCacheService.getUserPermissions("user-2")).thenReturn(Set.of("ROLE_DEPT_USER"));

            UserDetails userDetails = userDetailsService.loadUserByUsername("lockeduser");

            assertNotNull(userDetails);
            assertFalse(userDetails.isAccountNonLocked());
        }

        @Test
        @DisplayName("加载已禁用用户 - 账户被禁用")
        void loadUserByUsername_DisabledUser() {
            when(userMapper.selectByUsername("disableduser")).thenReturn(disabledUser);
            when(permissionCacheService.getUserPermissions("user-3")).thenReturn(Set.of("ROLE_DEPT_USER"));

            UserDetails userDetails = userDetailsService.loadUserByUsername("disableduser");

            assertNotNull(userDetails);
            assertFalse(userDetails.isEnabled());
            assertTrue(userDetails.isAccountNonLocked());
        }

        @Test
        @DisplayName("不同角色的用户加载")
        void loadUserByUsername_DifferentRoles() {
            User systemAdmin = User.builder()
                    .id("admin-1")
                    .username("admin")
                    .passwordHash("adminHash")
                    .role(UserRoleEnum.SYSTEM_ADMIN)
                    .status(UserStatusEnums.ACTIVE)
                    .lockUntil(null)
                    .build();

            when(userMapper.selectByUsername("admin")).thenReturn(systemAdmin);
            when(permissionCacheService.getUserPermissions("admin-1")).thenReturn(Set.of("ROLE_SYSTEM_ADMIN"));

            UserDetails userDetails = userDetailsService.loadUserByUsername("admin");

            assertTrue(userDetails.getAuthorities()
                    .contains(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN")));
        }
    }

    @Nested
    @DisplayName("loadUserEntityByUsername 测试")
    class LoadUserEntityByUsernameTests {

        @Test
        @DisplayName("成功加载用户实体")
        void loadUserEntityByUsername_Success() {
            when(userMapper.selectByUsername("testuser")).thenReturn(activeUser);

            User user = userDetailsService.loadUserEntityByUsername("testuser");

            assertNotNull(user);
            assertEquals("testuser", user.getUsername());
            assertEquals("hashedPassword123", user.getPasswordHash());
        }

        @Test
        @DisplayName("用户不存在抛出异常")
        void loadUserEntityByUsername_UserNotFound() {
            when(userMapper.selectByUsername("nonexistent")).thenReturn(null);

            UsernameNotFoundException exception = assertThrows(
                    UsernameNotFoundException.class,
                    () -> userDetailsService.loadUserEntityByUsername("nonexistent")
            );

            assertTrue(exception.getMessage().contains("nonexistent"));
        }

        @Test
        @DisplayName("加载锁定用户实体")
        void loadUserEntityByUsername_LockedUser() {
            when(userMapper.selectByUsername("lockeduser")).thenReturn(lockedUser);

            User user = userDetailsService.loadUserEntityByUsername("lockeduser");

            assertNotNull(user);
            assertTrue(user.isLocked());
        }
    }

    @Nested
    @DisplayName("边界值和异常测试")
    class BoundaryTests {

        @Test
        @DisplayName("空用户名")
        void loadUserByUsername_EmptyUsername() {
            when(userMapper.selectByUsername("")).thenReturn(null);

            UsernameNotFoundException exception = assertThrows(
                    UsernameNotFoundException.class,
                    () -> userDetailsService.loadUserByUsername("")
            );

            assertNotNull(exception);
        }

        @Test
        @DisplayName("特殊字符用户名")
        void loadUserByUsername_SpecialCharacters() {
            String specialUsername = "user@example.com";
            User emailUser = User.builder()
                    .id("user-email")
                    .username(specialUsername)
                    .passwordHash("hash")
                    .role(UserRoleEnum.DEPT_USER)
                    .status(UserStatusEnums.ACTIVE)
                    .lockUntil(null)
                    .build();

            when(userMapper.selectByUsername(specialUsername)).thenReturn(emailUser);
            when(permissionCacheService.getUserPermissions("user-email")).thenReturn(Set.of("ROLE_DEPT_USER"));

            UserDetails userDetails = userDetailsService.loadUserByUsername(specialUsername);

            assertNotNull(userDetails);
            assertEquals(specialUsername, userDetails.getUsername());
        }

        @Test
        @DisplayName("Repository 抛出异常")
        void loadUserByUsername_RepositoryException() {
            when(userMapper.selectByUsername("testuser"))
                    .thenThrow(new RuntimeException("Database connection failed"));

            assertThrows(
                    RuntimeException.class,
                    () -> userDetailsService.loadUserByUsername("testuser")
            );
        }
    }
}
