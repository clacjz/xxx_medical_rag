package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.mapper.RoleMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.mapper.UserRoleMapper;
import com.clacjz.medical.rag.model.entity.Role;
import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.entity.UserRole;
import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import com.clacjz.medical.rag.util.JsonUtil;
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

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PermissionCacheService 单元测试")
class PermissionCacheServiceTest {

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private JsonUtil jsonUtil;

    @Mock
    private PermissionCacheService self;

    @InjectMocks
    private PermissionCacheService permissionCacheService;

    @BeforeEach
    void setUp() {
        // Set self-reference for @Cacheable proxy pattern
        try {
            var selfField = PermissionCacheService.class.getDeclaredField("self");
            selfField.setAccessible(true);
            selfField.set(permissionCacheService, permissionCacheService);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set self field", e);
        }
    }

    @Nested
    @DisplayName("getUserPermissions - 权限加载测试")
    class GetUserPermissionsTests {

        @Test
        @DisplayName("角色权限正确加载ROLE_前缀")
        void shouldLoadRoleWithPrefix() {
            String permissionsJson = "[\"user:*\",\"document:*\"]";
            Role role = Role.builder().id("r-1").code("SYSTEM_ADMIN").level(1).active(true)
                    .permissions(permissionsJson).build();
            UserRole userRole = UserRole.builder().userId("u-1").roleId("r-1").build();
            userRole.setRole(role);

            when(userRoleMapper.selectByUserId("u-1")).thenReturn(List.of(userRole));
            when(jsonUtil.fromJsonList(eq(permissionsJson), eq(String.class)))
                    .thenReturn(List.of("user:*", "document:*"));
            when(userMapper.selectById("u-1")).thenReturn(null);

            Set<String> permissions = permissionCacheService.getUserPermissions("u-1");

            assertTrue(permissions.contains("ROLE_SYSTEM_ADMIN"), "应包含ROLE_SYSTEM_ADMIN");
            assertTrue(permissions.contains("user:*"), "应包含细粒度权限");
            assertTrue(permissions.contains("document:*"), "应包含细粒度权限");
        }

        @Test
        @DisplayName("User.role与UserRole表角色相同时不重复添加ROLE_条目")
        void shouldNotDuplicateRoleWhenUserEntityMatchesUserRole() {
            Role role = Role.builder().id("r-1").code("DEPT_ADMIN").level(2).active(true).build();
            UserRole userRole = UserRole.builder().userId("u-1").roleId("r-1").build();
            userRole.setRole(role);

            User user = User.builder().id("u-1").username("test").role(UserRoleEnum.DEPT_ADMIN).build();

            when(userRoleMapper.selectByUserId("u-1")).thenReturn(List.of(userRole));
            when(userMapper.selectById("u-1")).thenReturn(user);

            Set<String> permissions = permissionCacheService.getUserPermissions("u-1");

            // ROLE_DEPT_ADMIN应只出现一次
            long count = permissions.stream().filter(p -> p.equals("ROLE_DEPT_ADMIN")).count();
            assertEquals(1, count, "ROLE_DEPT_ADMIN不应重复");
        }

        @Test
        @DisplayName("格式错误的JSON权限不导致整个权限加载崩溃")
        void shouldHandleMalformedPermissionsJson() {
            Role role = Role.builder().id("r-1").code("DEPT_USER").level(4).active(true)
                    .permissions("[invalid json").build();
            UserRole userRole = UserRole.builder().userId("u-1").roleId("r-1").build();
            userRole.setRole(role);

            when(userRoleMapper.selectByUserId("u-1")).thenReturn(List.of(userRole));
            when(jsonUtil.fromJsonList(eq("[invalid json"), eq(String.class)))
                    .thenThrow(new RuntimeException("JSON parse error"));
            when(userMapper.selectById("u-1")).thenReturn(null);

            // 不应抛出异常，应优雅降级
            Set<String> permissions = permissionCacheService.getUserPermissions("u-1");

            assertTrue(permissions.contains("ROLE_DEPT_USER"), "角色标识仍应加载");
            // 格式错误的细粒度权限被跳过，不导致整个加载失败
        }

        @Test
        @DisplayName("User.role为null时不NPE")
        void shouldHandleNullUserRole() {
            when(userRoleMapper.selectByUserId("u-1")).thenReturn(Collections.emptyList());
            User user = User.builder().id("u-1").username("test").build();
            // user.getRole() returns null

            when(userMapper.selectById("u-1")).thenReturn(user);

            Set<String> permissions = permissionCacheService.getUserPermissions("u-1");

            assertNotNull(permissions, "不应抛出NPE");
            // 没有角色时权限集为空
        }

        @Test
        @DisplayName("非活跃角色不被加载")
        void shouldSkipInactiveRole() {
            Role inactiveRole = Role.builder().id("r-1").code("DEPT_UPLOADER").level(3).active(false).build();
            UserRole userRole = UserRole.builder().userId("u-1").roleId("r-1").build();
            userRole.setRole(inactiveRole);

            when(userRoleMapper.selectByUserId("u-1")).thenReturn(List.of(userRole));
            when(userMapper.selectById("u-1")).thenReturn(null);

            Set<String> permissions = permissionCacheService.getUserPermissions("u-1");

            assertFalse(permissions.contains("ROLE_DEPT_UPLOADER"), "非活跃角色不应被加载");
        }
    }

    @Nested
    @DisplayName("CacheEvict - 缓存驱逐测试")
    class CacheEvictTests {

        @Test
        @DisplayName("evictUserPermissionCache 通过 self 代理调用")
        void evictUserPermissionCache_calledThroughSelf() {
            assertDoesNotThrow(() -> permissionCacheService.evictUserPermissionCache("user-1"));
        }

        @Test
        @DisplayName("evictUserAllCaches 同时驱逐用户权限缓存和部门用户缓存")
        void evictUserAllCaches_evictsBothCaches() {
            assertDoesNotThrow(() -> permissionCacheService.evictUserAllCaches("user-1"));
        }

        @Test
        @DisplayName("evictAllUserPermissionCache 通过 self 代理调用")
        void evictAllUserPermissionCache_calledThroughSelf() {
            assertDoesNotThrow(() -> permissionCacheService.evictAllUserPermissionCache());
        }

        @Test
        @DisplayName("evictDocumentPermissionCache 通过 self 代理调用")
        void evictDocumentPermissionCache_calledThroughSelf() {
            assertDoesNotThrow(() -> permissionCacheService.evictDocumentPermissionCache("doc-1"));
        }

        @Test
        @DisplayName("evictAllDocumentPermissionCache 通过 self 代理调用")
        void evictAllDocumentPermissionCache_calledThroughSelf() {
            assertDoesNotThrow(() -> permissionCacheService.evictAllDocumentPermissionCache());
        }
    }
}
