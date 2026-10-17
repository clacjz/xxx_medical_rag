package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.*;
import com.clacjz.medical.rag.model.entity.*;
import com.clacjz.medical.rag.model.enums.PermissionLevelEnums;
import com.clacjz.medical.rag.model.enums.PermissionTypeEnums;
import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import com.clacjz.medical.rag.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentPermissionService 单元测试")
class DocumentPermissionServiceTest {

    @Mock
    private DocumentMapper documentRepository;

    @Mock
    private DocumentPermissionMapper documentPermissionRepository;

    @Mock
    private DocumentUserPermissionMapper documentUserPermissionRepository;

    @Mock
    private UserMapper userRepository;

    @Mock
    private DepartmentService departmentService;

    @Mock
    private PermissionAuditService permissionAuditService;

    @Mock
    private PermissionCacheService permissionCacheService;

    @InjectMocks
    private DocumentPermissionService documentPermissionService;

    private User systemAdmin;
    private User deptAdmin;
    private User regularUser;
    private User documentOwner;
    private Document testDocument;
    private DocumentPermission documentPermission;
    private DocumentUserPermission userPermission;

    @BeforeEach
    void setUp() {
        systemAdmin = User.builder()
                .id("sys-admin-1")
                .username("sysadmin")
                .role(UserRoleEnum.SYSTEM_ADMIN)
                .build();

        deptAdmin = User.builder()
                .id("dept-admin-1")
                .username("deptadmin")
                .role(UserRoleEnum.DEPT_ADMIN)
                .build();

        regularUser = User.builder()
                .id("user-1")
                .username("regularuser")
                .role(UserRoleEnum.DEPT_USER)
                .build();

        documentOwner = User.builder()
                .id("owner-1")
                .username("docowner")
                .role(UserRoleEnum.DEPT_USER)
                .build();

        testDocument = Document.builder()
                .id("doc-1")
                .title("测试文档")
                .ownerId("owner-1")
                .departmentId("dept-1")
                .build();

        documentPermission = DocumentPermission.builder()
                .id("perm-1")
                .documentId("doc-1")
                .permissionType(PermissionTypeEnums.DEPARTMENT_WIDE)
                .grantedBy("owner-1")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userPermission = DocumentUserPermission.builder()
                .id("user-perm-1")
                .documentId("doc-1")
                .userId("user-1")
                .permissionLevel(PermissionLevelEnums.EDIT)
                .grantedBy("owner-1")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
    }

    @Nested
    @DisplayName("检查文档访问权限测试")
    class CheckDocumentAccessTests {

        @Test
        @DisplayName("系统管理员可以访问任何文档")
        void checkDocumentAccess_SystemAdmin() {
            when(userRepository.selectById("sys-admin-1")).thenReturn(systemAdmin);
            when(documentRepository.selectById("doc-1")).thenReturn(testDocument);

            AccessCheckResult result = documentPermissionService.checkDocumentAccess("sys-admin-1", "doc-1");

            assertTrue(result.isHasAccess());
            assertEquals("系统管理员权限", result.getReason());
        }

        @Test
        @DisplayName("文档所有者可以访问")
        void checkDocumentAccess_DocumentOwner() {
            when(userRepository.selectById("owner-1")).thenReturn(documentOwner);
            when(documentRepository.selectById("doc-1")).thenReturn(testDocument);

            AccessCheckResult result = documentPermissionService.checkDocumentAccess("owner-1", "doc-1");

            assertTrue(result.isHasAccess());
            assertEquals(PermissionLevelEnums.EDIT, result.getPermissionLevel());
        }

        @Test
        @DisplayName("用户不存在时抛出异常")
        void checkDocumentAccess_UserNotFound() {
            when(userRepository.selectById("nonexistent")).thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> documentPermissionService.checkDocumentAccess("nonexistent", "doc-1"));

            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("文档不存在时抛出异常")
        void checkDocumentAccess_DocumentNotFound() {
            when(userRepository.selectById("user-1")).thenReturn(regularUser);
            when(documentRepository.selectById("nonexistent")).thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> documentPermissionService.checkDocumentAccess("user-1", "nonexistent"));

            assertEquals(ErrorCode.DOCUMENT_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("部门管理员可以访问本部门文档")
        void checkDocumentAccess_DeptAdminOfSameDepartment() {
            when(userRepository.selectById("dept-admin-1")).thenReturn(deptAdmin);
            when(documentRepository.selectById("doc-1")).thenReturn(testDocument);
            when(documentPermissionRepository.selectByDocumentId("doc-1")).thenReturn(null);
            when(departmentService.getUserDepartmentIds("dept-admin-1")).thenReturn(Set.of("dept-1"));

            AccessCheckResult result = documentPermissionService.checkDocumentAccess("dept-admin-1", "doc-1");

            assertTrue(result.isHasAccess());
            assertEquals(PermissionLevelEnums.EDIT, result.getPermissionLevel());
        }

        @Test
        @DisplayName("同部门用户可以访问部门范围权限的文档")
        void checkDocumentAccess_SameDepartment() {
            when(userRepository.selectById("user-1")).thenReturn(regularUser);
            when(documentRepository.selectById("doc-1")).thenReturn(testDocument);
            when(documentPermissionRepository.selectByDocumentId("doc-1")).thenReturn(documentPermission);
            when(departmentService.getUserDepartmentIds("user-1")).thenReturn(Set.of("dept-1"));

            AccessCheckResult result = documentPermissionService.checkDocumentAccess("user-1", "doc-1");

            assertTrue(result.isHasAccess());
            assertEquals(PermissionLevelEnums.READ, result.getPermissionLevel());
        }

        @Test
        @DisplayName("不同部门用户不能访问部门范围权限的文档")
        void checkDocumentAccess_DifferentDepartment() {
            when(userRepository.selectById("user-1")).thenReturn(regularUser);
            when(documentRepository.selectById("doc-1")).thenReturn(testDocument);
            when(documentPermissionRepository.selectByDocumentId("doc-1")).thenReturn(documentPermission);
            when(departmentService.getUserDepartmentIds("user-1")).thenReturn(Set.of("other-dept"));

            AccessCheckResult result = documentPermissionService.checkDocumentAccess("user-1", "doc-1");

            assertFalse(result.isHasAccess());
        }

        @Test
        @DisplayName("全公司可查看权限")
        void checkDocumentAccess_CompanyWide() {
            documentPermission.setPermissionType(PermissionTypeEnums.COMPANY_WIDE);
            when(userRepository.selectById("user-1")).thenReturn(regularUser);
            when(documentRepository.selectById("doc-1")).thenReturn(testDocument);
            when(documentPermissionRepository.selectByDocumentId("doc-1")).thenReturn(documentPermission);

            AccessCheckResult result = documentPermissionService.checkDocumentAccess("user-1", "doc-1");

            assertTrue(result.isHasAccess());
            assertEquals(PermissionLevelEnums.READ, result.getPermissionLevel());
        }

        @Test
        @DisplayName("受限权限 - 用户在名单中且未过期")
        void checkDocumentAccess_Restricted_InList() {
            documentPermission.setPermissionType(PermissionTypeEnums.RESTRICTED);
            when(userRepository.selectById("user-1")).thenReturn(regularUser);
            when(documentRepository.selectById("doc-1")).thenReturn(testDocument);
            when(documentPermissionRepository.selectByDocumentId("doc-1")).thenReturn(documentPermission);
            when(documentUserPermissionRepository.selectByDocumentIdAndUserId("doc-1", "user-1"))
                    .thenReturn(userPermission);

            AccessCheckResult result = documentPermissionService.checkDocumentAccess("user-1", "doc-1");

            assertTrue(result.isHasAccess());
            assertEquals(PermissionLevelEnums.EDIT, result.getPermissionLevel());
        }

        @Test
        @DisplayName("受限权限 - 用户不在名单中")
        void checkDocumentAccess_Restricted_NotInList() {
            documentPermission.setPermissionType(PermissionTypeEnums.RESTRICTED);
            when(userRepository.selectById("user-1")).thenReturn(regularUser);
            when(documentRepository.selectById("doc-1")).thenReturn(testDocument);
            when(documentPermissionRepository.selectByDocumentId("doc-1")).thenReturn(documentPermission);
            when(documentUserPermissionRepository.selectByDocumentIdAndUserId("doc-1", "user-1"))
                    .thenReturn(null);

            AccessCheckResult result = documentPermissionService.checkDocumentAccess("user-1", "doc-1");

            assertFalse(result.isHasAccess());
            assertEquals("未在允许访问名单中", result.getReason());
        }

        @Test
        @DisplayName("受限权限 - 权限已过期")
        void checkDocumentAccess_Restricted_Expired() {
            documentPermission.setPermissionType(PermissionTypeEnums.RESTRICTED);
            userPermission.setExpiresAt(LocalDateTime.now().minusDays(1));
            when(userRepository.selectById("user-1")).thenReturn(regularUser);
            when(documentRepository.selectById("doc-1")).thenReturn(testDocument);
            when(documentPermissionRepository.selectByDocumentId("doc-1")).thenReturn(documentPermission);
            when(documentUserPermissionRepository.selectByDocumentIdAndUserId("doc-1", "user-1"))
                    .thenReturn(userPermission);

            AccessCheckResult result = documentPermissionService.checkDocumentAccess("user-1", "doc-1");

            assertFalse(result.isHasAccess());
            assertEquals("权限已过期", result.getReason());
        }

        @Test
        @DisplayName("无权限配置且非部门管理员")
        void checkDocumentAccess_NoPermission() {
            when(userRepository.selectById("user-1")).thenReturn(regularUser);
            when(documentRepository.selectById("doc-1")).thenReturn(testDocument);
            when(documentPermissionRepository.selectByDocumentId("doc-1")).thenReturn(null);

            AccessCheckResult result = documentPermissionService.checkDocumentAccess("user-1", "doc-1");

            assertFalse(result.isHasAccess());
            assertEquals("文档无权限配置", result.getReason());
        }
    }

    @Nested
    @DisplayName("批量检查访问权限测试")
    class BatchCheckAccessTests {

        @Test
        @DisplayName("批量检查成功")
        void batchCheckAccess_Success() {
            testDocument.setOwnerId("user-1");
            when(userRepository.selectById("user-1")).thenReturn(regularUser);
            when(documentRepository.selectById("doc-1")).thenReturn(testDocument);
            when(documentRepository.selectById("doc-2")).thenReturn(testDocument);

            BatchAccessCheckResult result = documentPermissionService.batchCheckAccess(
                    "user-1", Arrays.asList("doc-1", "doc-2"));

            assertNotNull(result);
            assertEquals(2, result.getResults().size());
            assertTrue(result.getResults().get(0).isHasAccess());
            assertTrue(result.getResults().get(1).isHasAccess());
        }

        @Test
        @DisplayName("批量检查 - 用户不存在")
        void batchCheckAccess_UserNotFound() {
            when(userRepository.selectById("nonexistent")).thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> documentPermissionService.batchCheckAccess("nonexistent", Arrays.asList("doc-1")));

            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("获取文档权限测试")
    class GetDocumentPermissionTests {

        @Test
        @DisplayName("获取文档权限 - 无权限配置")
        void getDocumentPermission_NoPermission() {
            when(documentRepository.selectById("doc-1")).thenReturn(testDocument);
            when(userRepository.selectById("owner-1")).thenReturn(documentOwner);
            when(documentPermissionRepository.selectByDocumentId("doc-1")).thenReturn(null);

            DocumentPermissionDTO result = documentPermissionService.getDocumentPermission("doc-1");

            assertNotNull(result);
            assertEquals("doc-1", result.getDocumentId());
            assertNull(result.getPermissionType());
        }

        @Test
        @DisplayName("获取文档权限 - 有限制权限并包含用户列表")
        void getDocumentPermission_RestrictedWithUsers() {
            documentPermission.setPermissionType(PermissionTypeEnums.RESTRICTED);
            when(documentRepository.selectById("doc-1")).thenReturn(testDocument);
            when(userRepository.selectById("owner-1")).thenReturn(documentOwner);
            when(documentPermissionRepository.selectByDocumentId("doc-1")).thenReturn(documentPermission);
            // 批量查询用户信息（selectBatchIds替代逐个selectById）
            when(userRepository.selectBatchIds(anyList())).thenReturn(Arrays.asList(regularUser));
            when(documentUserPermissionRepository.selectByDocumentId("doc-1"))
                    .thenReturn(Arrays.asList(userPermission));

            DocumentPermissionDTO result = documentPermissionService.getDocumentPermission("doc-1");

            assertNotNull(result);
            assertEquals(PermissionTypeEnums.RESTRICTED, result.getPermissionType());
            assertNotNull(result.getAllowedUsers());
            assertEquals(1, result.getAllowedUsers().size());
        }
    }

    @Nested
    @DisplayName("更新文档权限测试")
    class UpdateDocumentPermissionTests {

        @Test
        @DisplayName("更新文档权限成功 - 新建权限")
        void updateDocumentPermission_Success_NewPermission() {
            UpdateDocumentPermissionRequest request = UpdateDocumentPermissionRequest.builder()
                    .permissionType(PermissionTypeEnums.COMPANY_WIDE)
                    .build();

            when(documentRepository.selectById("doc-1")).thenReturn(testDocument);
            when(userRepository.selectById("owner-1")).thenReturn(documentOwner);
            when(documentPermissionRepository.selectByDocumentId("doc-1")).thenReturn(null);
            when(documentPermissionRepository.insert(any(DocumentPermission.class))).thenReturn(1);

            DocumentPermissionDTO result = documentPermissionService.updateDocumentPermission(
                    "doc-1", request, "owner-1", "127.0.0.1", "Mozilla/5.0");

            assertNotNull(result);
            verify(documentPermissionRepository).insert(any(DocumentPermission.class));
            verify(permissionAuditService).logPermissionChange(anyString(), any(), any(), anyString(), any(), any(), anyString(), anyString());
            verify(permissionCacheService).evictDocumentPermissionCache("doc-1");
        }

        @Test
        @DisplayName("更新文档权限成功 - 更新为受限权限并设置用户")
        void updateDocumentPermission_Success_RestrictedWithUsers() {
            UpdateDocumentPermissionRequest request = UpdateDocumentPermissionRequest.builder()
                    .permissionType(PermissionTypeEnums.RESTRICTED)
                    .allowedUserIds(Arrays.asList("user-1", "user-2"))
                    .build();

            when(documentRepository.selectById("doc-1")).thenReturn(testDocument);
            when(userRepository.selectById("owner-1")).thenReturn(documentOwner);
            when(documentPermissionRepository.selectByDocumentId("doc-1")).thenReturn(documentPermission);
            when(documentUserPermissionRepository.insert(any(DocumentUserPermission.class))).thenReturn(1);

            DocumentPermissionDTO result = documentPermissionService.updateDocumentPermission(
                    "doc-1", request, "owner-1", "127.0.0.1", "Mozilla/5.0");

            assertNotNull(result);
            verify(documentUserPermissionRepository).deleteByDocumentId("doc-1");
            verify(documentUserPermissionRepository, atLeastOnce()).insert(any(DocumentUserPermission.class));
            verify(permissionCacheService).evictDocumentPermissionCache("doc-1");
        }

        @Test
        @DisplayName("更新文档权限失败 - 无权修改")
        void updateDocumentPermission_Failure_NoPermission() {
            UpdateDocumentPermissionRequest request = UpdateDocumentPermissionRequest.builder()
                    .permissionType(PermissionTypeEnums.COMPANY_WIDE)
                    .build();

            when(documentRepository.selectById("doc-1")).thenReturn(testDocument);
            when(userRepository.selectById("user-1")).thenReturn(regularUser);

            BizException exception = assertThrows(BizException.class,
                    () -> documentPermissionService.updateDocumentPermission(
                            "doc-1", request, "user-1", "127.0.0.1", "Mozilla/5.0"));

            assertEquals(ErrorCode.PERMISSION_DENIED, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("添加用户权限测试")
    class AddUserPermissionTests {

        @Test
        @DisplayName("添加用户权限成功")
        void addUserPermission_Success() {
            AddDocumentUserPermissionRequest request = AddDocumentUserPermissionRequest.builder()
                    .userId("user-1")
                    .permissionLevel(PermissionLevelEnums.READ)
                    .expiresAt(LocalDateTime.now().plusDays(7))
                    .build();

            when(documentRepository.selectById("doc-1")).thenReturn(testDocument);
            when(userRepository.selectById("owner-1")).thenReturn(documentOwner);
            when(documentUserPermissionRepository.existsByDocumentIdAndUserId("doc-1", "user-1")).thenReturn(false);
            when(documentUserPermissionRepository.insert(any(DocumentUserPermission.class)))
                    .thenReturn(1);

            assertDoesNotThrow(() -> documentPermissionService.addUserPermission(
                    "doc-1", request, "owner-1", "127.0.0.1", "Mozilla/5.0"));

            verify(documentUserPermissionRepository).insert(any(DocumentUserPermission.class));
            verify(permissionAuditService).logPermissionChange(anyString(), any(), any(), anyString(), isNull(), any(), anyString(), anyString());
            verify(permissionCacheService).evictDocumentPermissionCache("doc-1");
        }

        @Test
        @DisplayName("添加用户权限失败 - 用户已有权限")
        void addUserPermission_Failure_AlreadyExists() {
            AddDocumentUserPermissionRequest request = AddDocumentUserPermissionRequest.builder()
                    .userId("user-1")
                    .permissionLevel(PermissionLevelEnums.READ)
                    .build();

            when(documentRepository.selectById("doc-1")).thenReturn(testDocument);
            when(userRepository.selectById("owner-1")).thenReturn(documentOwner);
            when(documentUserPermissionRepository.existsByDocumentIdAndUserId("doc-1", "user-1")).thenReturn(true);

            BizException exception = assertThrows(BizException.class,
                    () -> documentPermissionService.addUserPermission(
                            "doc-1", request, "owner-1", "127.0.0.1", "Mozilla/5.0"));

            assertEquals(ErrorCode.USER_PERMISSION_EXISTS, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("移除用户权限测试")
    class RemoveUserPermissionTests {

        @Test
        @DisplayName("移除用户权限成功")
        void removeUserPermission_Success() {
            when(documentRepository.selectById("doc-1")).thenReturn(testDocument);
            when(userRepository.selectById("owner-1")).thenReturn(documentOwner);
            when(documentUserPermissionRepository.selectByDocumentIdAndUserId("doc-1", "user-1"))
                    .thenReturn(userPermission);

            assertDoesNotThrow(() -> documentPermissionService.removeUserPermission(
                    "doc-1", "user-1", "owner-1", "127.0.0.1", "Mozilla/5.0"));

            verify(documentUserPermissionRepository).deleteByDocumentIdAndUserId("doc-1", "user-1");
            verify(permissionAuditService).logPermissionChange(anyString(), any(), any(), anyString(), any(), isNull(), anyString(), anyString());
            verify(permissionCacheService).evictDocumentPermissionCache("doc-1");
        }

        @Test
        @DisplayName("移除用户权限失败 - 权限不存在")
        void removeUserPermission_Failure_NotFound() {
            when(documentRepository.selectById("doc-1")).thenReturn(testDocument);
            when(userRepository.selectById("owner-1")).thenReturn(documentOwner);
            when(documentUserPermissionRepository.selectByDocumentIdAndUserId("doc-1", "nonexistent"))
                    .thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> documentPermissionService.removeUserPermission(
                            "doc-1", "nonexistent", "owner-1", "127.0.0.1", "Mozilla/5.0"));

            assertEquals(ErrorCode.USER_PERMISSION_NOT_FOUND, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("权限修改检查测试")
    class CanModifyPermissionTests {

        @Test
        @DisplayName("系统管理员可以修改权限")
        void canModifyDocumentPermission_SystemAdmin() {
            assertTrue(documentPermissionService.canModifyDocumentPermission(systemAdmin, testDocument));
        }

        @Test
        @DisplayName("文档所有者可以修改权限")
        void canModifyDocumentPermission_DocumentOwner() {
            assertTrue(documentPermissionService.canModifyDocumentPermission(documentOwner, testDocument));
        }

        @Test
        @DisplayName("部门管理员可以修改本部门文档权限")
        void canModifyDocumentPermission_DeptAdmin() {
            when(departmentService.getUserDepartmentIds("dept-admin-1")).thenReturn(Set.of("dept-1"));
            assertTrue(documentPermissionService.canModifyDocumentPermission(deptAdmin, testDocument));
        }

        @Test
        @DisplayName("普通用户不能修改权限")
        void canModifyDocumentPermission_RegularUser() {
            assertFalse(documentPermissionService.canModifyDocumentPermission(regularUser, testDocument));
        }
    }

    @Nested
    @DisplayName("文档删除权限检查测试")
    class CanDeleteDocumentTests {

        @Test
        @DisplayName("系统管理员可以删除文档")
        void canDeleteDocument_SystemAdmin() {
            assertTrue(documentPermissionService.canDeleteDocument(systemAdmin, testDocument));
        }

        @Test
        @DisplayName("文档所有者可以删除文档")
        void canDeleteDocument_DocumentOwner() {
            assertTrue(documentPermissionService.canDeleteDocument(documentOwner, testDocument));
        }

        @Test
        @DisplayName("普通用户不能删除文档")
        void canDeleteDocument_RegularUser() {
            assertFalse(documentPermissionService.canDeleteDocument(regularUser, testDocument));
        }
    }
}
