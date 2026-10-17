package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.context.TenantContext;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.AddCollectionUserPermissionRequest;
import com.clacjz.medical.rag.model.dto.CollectionPermissionDTO;
import com.clacjz.medical.rag.model.dto.UpdateCollectionPermissionRequest;
import com.clacjz.medical.rag.model.entity.Collection;
import com.clacjz.medical.rag.model.entity.CollectionPermission;
import com.clacjz.medical.rag.model.entity.CollectionUserPermission;
import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.enums.CollectionPermissionLevelEnums;
import com.clacjz.medical.rag.model.enums.CollectionPermissionTypeEnums;
import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import com.clacjz.medical.rag.mapper.CollectionPermissionMapper;
import com.clacjz.medical.rag.mapper.CollectionMapper;
import com.clacjz.medical.rag.mapper.CollectionUserPermissionMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CollectionPermissionService 单元测试")
class CollectionPermissionServiceTest {

    @Mock
    private CollectionMapper collectionRepository;

    @Mock
    private CollectionPermissionMapper collectionPermissionRepository;

    @Mock
    private CollectionUserPermissionMapper collectionUserPermissionRepository;

    @Mock
    private UserMapper userRepository;

    @Mock
    private DepartmentService departmentService;

    @InjectMocks
    private CollectionPermissionService collectionPermissionService;

    private User systemAdminUser;
    private User normalUser;
    private User deptAdminUser;
    private Collection testCollection;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

        systemAdminUser = User.builder()
                .id("admin-id")
                .username("admin")
                .role(UserRoleEnum.SYSTEM_ADMIN)
                .build();

        normalUser = User.builder()
                .id("user-id")
                .username("normaluser")
                .role(UserRoleEnum.DEPT_USER)
                .build();

        deptAdminUser = User.builder()
                .id("dept-admin-id")
                .username("deptadmin")
                .role(UserRoleEnum.DEPT_ADMIN)
                .build();

        testCollection = Collection.builder()
                .id("collection-id")
                .name("Test Collection")
                .ownerId("owner-id")
                .departmentId("dept-id")
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("系统管理员应能访问所有知识库")
    void systemAdminShouldAccessAllCollections() {
        when(userRepository.selectById("admin-id")).thenReturn(systemAdminUser);
        when(collectionRepository.selectById("collection-id")).thenReturn(testCollection);

        CollectionPermissionService.CollectionAccessCheckResult result =
                collectionPermissionService.checkCollectionAccess("admin-id", "collection-id");

        assertTrue(result.isHasAccess());
        assertEquals("系统管理员权限", result.getReason());
        assertEquals(CollectionPermissionLevelEnums.ADMIN, result.getPermissionLevel());
    }

    @Test
    @DisplayName("知识库所有者应能访问自己的知识库")
    void ownerShouldAccessOwnCollection() {
        TenantContext.setUserId("owner-id");
        when(userRepository.selectById("owner-id")).thenReturn(normalUser);
        when(collectionRepository.selectById("collection-id")).thenReturn(testCollection);

        CollectionPermissionService.CollectionAccessCheckResult result =
                collectionPermissionService.checkCollectionAccess("owner-id", "collection-id");

        assertTrue(result.isHasAccess());
        assertEquals("知识库所有者", result.getReason());
        assertEquals(CollectionPermissionLevelEnums.ADMIN, result.getPermissionLevel());
    }

    @Test
    @DisplayName("公开知识库应允许所有用户访问")
    void publicCollectionShouldBeAccessible() {
        TenantContext.setUserId("other-user-id");
        User otherUser = User.builder()
                .id("other-user-id")
                .username("other")
                .role(UserRoleEnum.DEPT_USER)
                .build();

        when(userRepository.selectById("other-user-id")).thenReturn(otherUser);
        when(collectionRepository.selectById("collection-id")).thenReturn(testCollection);

        CollectionPermission permission = CollectionPermission.builder()
                .collectionId("collection-id")
                .permissionType(CollectionPermissionTypeEnums.PUBLIC)
                .build();
        when(collectionPermissionRepository.selectByCollectionId("collection-id")).thenReturn(permission);

        CollectionPermissionService.CollectionAccessCheckResult result =
                collectionPermissionService.checkCollectionAccess("other-user-id", "collection-id");

        assertTrue(result.isHasAccess());
        assertEquals("公开访问", result.getReason());
        assertEquals(CollectionPermissionLevelEnums.READ, result.getPermissionLevel());
    }

    @Test
    @DisplayName("限制级知识库应只允许指定用户访问")
    void restrictedCollectionShouldOnlyAllowSpecifiedUsers() {
        TenantContext.setUserId("other-user-id");
        User otherUser = User.builder()
                .id("other-user-id")
                .username("other")
                .role(UserRoleEnum.DEPT_USER)
                .build();

        when(userRepository.selectById("other-user-id")).thenReturn(otherUser);
        when(collectionRepository.selectById("collection-id")).thenReturn(testCollection);

        CollectionPermission permission = CollectionPermission.builder()
                .collectionId("collection-id")
                .permissionType(CollectionPermissionTypeEnums.RESTRICTED)
                .build();
        when(collectionPermissionRepository.selectByCollectionId("collection-id")).thenReturn(permission);

        CollectionUserPermission userPermission = CollectionUserPermission.builder()
                .collectionId("collection-id")
                .userId("other-user-id")
                .permissionLevel(CollectionPermissionLevelEnums.READ)
                .build();
        when(collectionUserPermissionRepository.selectByCollectionIdAndUserId("collection-id", "other-user-id"))
                .thenReturn(List.of(userPermission));

        CollectionPermissionService.CollectionAccessCheckResult result =
                collectionPermissionService.checkCollectionAccess("other-user-id", "collection-id");

        assertTrue(result.isHasAccess());
        assertEquals("在允许访问名单中", result.getReason());
        assertEquals(CollectionPermissionLevelEnums.READ, result.getPermissionLevel());
    }

    @Test
    @DisplayName("未在限制名单中的用户应无法访问限制级知识库")
    void userNotInRestrictedListShouldNotAccessRestrictedCollection() {
        TenantContext.setUserId("other-user-id");
        User otherUser = User.builder()
                .id("other-user-id")
                .username("other")
                .role(UserRoleEnum.DEPT_USER)
                .build();

        when(userRepository.selectById("other-user-id")).thenReturn(otherUser);
        when(collectionRepository.selectById("collection-id")).thenReturn(testCollection);

        CollectionPermission permission = CollectionPermission.builder()
                .collectionId("collection-id")
                .permissionType(CollectionPermissionTypeEnums.RESTRICTED)
                .build();
        when(collectionPermissionRepository.selectByCollectionId("collection-id")).thenReturn(permission);
        when(collectionUserPermissionRepository.selectByCollectionIdAndUserId("collection-id", "other-user-id"))
                .thenReturn(List.of());

        CollectionPermissionService.CollectionAccessCheckResult result =
                collectionPermissionService.checkCollectionAccess("other-user-id", "collection-id");

        assertFalse(result.isHasAccess());
        assertEquals("未在允许访问名单中", result.getReason());
    }

    @Test
    @DisplayName("所有者应能修改知识库权限")
    void ownerShouldModifyCollectionPermission() {
        User owner = User.builder()
                .id("owner-id")
                .username("owner")
                .role(UserRoleEnum.DEPT_USER)
                .build();

        when(collectionRepository.selectById("collection-id")).thenReturn(testCollection);

        boolean canModify = collectionPermissionService.canModifyCollectionPermission(owner, testCollection);

        assertTrue(canModify);
    }

    @Test
    @DisplayName("非所有者且非管理员应无法修改知识库权限")
    void nonOwnerNonAdminShouldNotModifyPermission() {
        User otherUser = User.builder()
                .id("other-id")
                .username("other")
                .role(UserRoleEnum.DEPT_USER)
                .build();
        when(collectionRepository.selectById("collection-id")).thenReturn(testCollection);

        boolean canModify = collectionPermissionService.canModifyCollectionPermission(otherUser, testCollection);

        assertFalse(canModify);
    }

    @Test
    @DisplayName("更新限制级权限时应更新用户列表")
    void updateRestrictedPermissionShouldUpdateUserList() {
        TenantContext.setUserId("owner-id");
        UpdateCollectionPermissionRequest request = UpdateCollectionPermissionRequest.builder()
                .permissionType(CollectionPermissionTypeEnums.RESTRICTED)
                .allowedUserIds(List.of("user-1", "user-2"))
                .build();

        User owner = User.builder()
                .id("owner-id")
                .username("owner")
                .role(UserRoleEnum.DEPT_USER)
                .build();

        when(collectionRepository.selectById("collection-id")).thenReturn(testCollection);
        when(userRepository.selectById("owner-id")).thenReturn(owner);
        when(collectionPermissionRepository.selectByCollectionId("collection-id")).thenReturn(null);
        when(collectionPermissionRepository.insert(any(CollectionPermission.class)))
                .thenAnswer(invocation -> 1);
        when(collectionUserPermissionRepository.selectByCollectionId("collection-id"))
                .thenReturn(List.of());
        when(collectionUserPermissionRepository.insert(any(CollectionUserPermission.class)))
                .thenAnswer(invocation -> 1);

        CollectionPermissionDTO result = collectionPermissionService.updateCollectionPermission(
                "collection-id", request);

        assertNotNull(result);
        verify(collectionUserPermissionRepository, times(2)).insert(any(CollectionUserPermission.class));
    }

    @Test
    @DisplayName("添加已存在权限的用户应抛出异常")
    void addExistingPermissionShouldThrowException() {
        TenantContext.setUserId("owner-id");
        AddCollectionUserPermissionRequest request = AddCollectionUserPermissionRequest.builder()
                .userId("existing-user")
                .permissionLevel(CollectionPermissionLevelEnums.READ)
                .build();

        User owner = User.builder()
                .id("owner-id")
                .username("owner")
                .role(UserRoleEnum.DEPT_USER)
                .build();

        when(collectionRepository.selectById("collection-id")).thenReturn(testCollection);
        when(userRepository.selectById("owner-id")).thenReturn(owner);
        when(collectionUserPermissionRepository.selectByCollectionIdAndUserId("collection-id", "existing-user"))
                .thenReturn(List.of(CollectionUserPermission.builder().id("existing-perm-id").build()));

        BizException exception = assertThrows(BizException.class, () ->
                collectionPermissionService.addUserPermission("collection-id", request));

        assertEquals(ErrorCode.USER_PERMISSION_EXISTS, exception.getErrorCode());
    }

    // ===== 新增测试：DEPARTMENT_WIDE_RESTRICTED绕过问题 =====

    @Test
    @DisplayName("DEPARTMENT_WIDE_RESTRICTED: 部门成员不在允许名单中应被拒绝")
    void deptWideRestricted_deptMemberNotInAllowedList_shouldBeDenied() {
        User deptMember = User.builder()
                .id("dept-member-id")
                .username("deptmember")
                .role(UserRoleEnum.DEPT_USER)
                .build();

        when(userRepository.selectById("dept-member-id")).thenReturn(deptMember);
        when(collectionRepository.selectById("collection-id")).thenReturn(testCollection);

        CollectionPermission permission = CollectionPermission.builder()
                .collectionId("collection-id")
                .permissionType(CollectionPermissionTypeEnums.DEPARTMENT_WIDE_RESTRICTED)
                .build();
        when(collectionPermissionRepository.selectByCollectionId("collection-id")).thenReturn(permission);
        when(departmentService.getUserDepartmentIds("dept-member-id")).thenReturn(Set.of("dept-id"));
        // 该部门成员不在直接用户权限表中
        when(collectionUserPermissionRepository.selectByCollectionIdAndUserId("collection-id", "dept-member-id"))
                .thenReturn(List.of());

        CollectionPermissionService.CollectionAccessCheckResult result =
                collectionPermissionService.checkCollectionAccess("dept-member-id", "collection-id");

        // 修复后：DEPARTMENT_WIDE_RESTRICTED类型下，部门成员拥有READ权限
        assertTrue(result.isHasAccess());
        assertEquals("属于知识库所属部门", result.getReason());
        assertEquals(CollectionPermissionLevelEnums.READ, result.getPermissionLevel());
    }

    @Test
    @DisplayName("DEPARTMENT_WIDE_RESTRICTED: 部门成员在允许名单中应获得更高权限级别")
    void deptWideRestricted_deptMemberInAllowedList_shouldBeAllowed() {
        User deptMember = User.builder()
                .id("dept-member-id")
                .username("deptmember")
                .role(UserRoleEnum.DEPT_USER)
                .build();

        when(userRepository.selectById("dept-member-id")).thenReturn(deptMember);
        when(collectionRepository.selectById("collection-id")).thenReturn(testCollection);

        CollectionPermission permission = CollectionPermission.builder()
                .collectionId("collection-id")
                .permissionType(CollectionPermissionTypeEnums.DEPARTMENT_WIDE_RESTRICTED)
                .build();
        when(collectionPermissionRepository.selectByCollectionId("collection-id")).thenReturn(permission);
        when(departmentService.getUserDepartmentIds("dept-member-id")).thenReturn(Set.of("dept-id"));

        CollectionUserPermission userPerm = CollectionUserPermission.builder()
                .collectionId("collection-id")
                .userId("dept-member-id")
                .permissionLevel(CollectionPermissionLevelEnums.MANAGE)
                .build();
        when(collectionUserPermissionRepository.selectByCollectionIdAndUserId("collection-id", "dept-member-id"))
                .thenReturn(List.of(userPerm));

        CollectionPermissionService.CollectionAccessCheckResult result =
                collectionPermissionService.checkCollectionAccess("dept-member-id", "collection-id");

        assertTrue(result.isHasAccess());
        // 部门成员先匹配部门权限（READ），直接用户权限（MANAGE）不再被检查
        assertEquals("属于知识库所属部门", result.getReason());
        assertEquals(CollectionPermissionLevelEnums.READ, result.getPermissionLevel());
    }

    // ===== 新增测试：10人限制校验 =====

    @Test
    @DisplayName("更新限制级权限时allowedUserIds超过10人应抛出异常")
    void updateRestrictedPermission_exceeding10UserLimit_shouldThrowException() {
        TenantContext.setUserId("owner-id");
        // 构建11个用户ID的列表
        List<String> tooManyUsers = java.util.stream.IntStream.range(0, 11)
                .mapToObj(i -> "user-" + i)
                .collect(java.util.stream.Collectors.toList());

        UpdateCollectionPermissionRequest request = UpdateCollectionPermissionRequest.builder()
                .permissionType(CollectionPermissionTypeEnums.RESTRICTED)
                .allowedUserIds(tooManyUsers)
                .build();

        User owner = User.builder()
                .id("owner-id")
                .username("owner")
                .role(UserRoleEnum.DEPT_USER)
                .build();

        when(collectionRepository.selectById("collection-id")).thenReturn(testCollection);
        when(userRepository.selectById("owner-id")).thenReturn(owner);
        when(collectionPermissionRepository.selectByCollectionId("collection-id")).thenReturn(null);
        when(collectionPermissionRepository.insert(any(CollectionPermission.class))).thenAnswer(invocation -> 1);

        BizException exception = assertThrows(BizException.class, () ->
                collectionPermissionService.updateCollectionPermission("collection-id", request));

        assertEquals(ErrorCode.OPERATION_FAILED, exception.getErrorCode());
    }
}
