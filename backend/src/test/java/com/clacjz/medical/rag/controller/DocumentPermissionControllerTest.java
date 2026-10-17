package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.model.dto.*;
import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import com.clacjz.medical.rag.model.enums.UserStatusEnums;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.DocumentPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DocumentPermissionController 单元测试")
class DocumentPermissionControllerTest {

    @Mock
    private DocumentPermissionService documentPermissionService;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private DocumentPermissionController documentPermissionController;

    private UserDetails mockUserDetails;
    private static final String TEST_USER_ID = "user-id-123";

    @BeforeEach
    void setUp() {
        mockUserDetails = User.builder()
                .username("testuser")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        // 模拟userMapper.selectByUsername返回用户实体（resolveUserId需要）
        com.clacjz.medical.rag.model.entity.User mockUser = com.clacjz.medical.rag.model.entity.User.builder()
                .id(TEST_USER_ID)
                .username("testuser")
                .role(UserRoleEnum.DEPT_USER)
                .status(UserStatusEnums.ACTIVE)
                .build();
        when(userMapper.selectByUsername("testuser")).thenReturn(mockUser);
    }

    @Test
    @DisplayName("检查文档访问权限成功")
    void checkAccess_Success() {
        String documentId = "doc-123";
        AccessCheckResult result = AccessCheckResult.builder()
                .hasAccess(true)
                .build();

        when(documentPermissionService.checkDocumentAccess(TEST_USER_ID, documentId))
                .thenReturn(result);

        ResponseEntity<Result<AccessCheckResult>> response =
                documentPermissionController.checkAccess(documentId, mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getData().isHasAccess());
    }

    @Test
    @DisplayName("批量检查访问权限成功")
    void batchCheckAccess_Success() {
        BatchAccessCheckRequest request = BatchAccessCheckRequest.builder()
                .documentIds(List.of("doc-1", "doc-2"))
                .build();

        BatchAccessCheckResult result = BatchAccessCheckResult.builder()
                .results(List.of())
                .build();

        when(documentPermissionService.batchCheckAccess(eq(TEST_USER_ID), anyList()))
                .thenReturn(result);

        ResponseEntity<Result<BatchAccessCheckResult>> response =
                documentPermissionController.batchCheckAccess(request, mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("获取文档权限配置成功（有权限的用户）")
    void getDocumentPermission_Success() {
        String documentId = "doc-123";
        DocumentPermissionDTO permission = DocumentPermissionDTO.builder()
                .documentId(documentId)
                .build();

        when(documentPermissionService.getDocumentPermission(documentId))
                .thenReturn(permission);

        // 模拟权限校验通过
        com.clacjz.medical.rag.model.entity.User operator = com.clacjz.medical.rag.model.entity.User.builder()
                .id(TEST_USER_ID).role(UserRoleEnum.SYSTEM_ADMIN).build();
        when(userMapper.selectById(TEST_USER_ID)).thenReturn(operator);
        com.clacjz.medical.rag.model.entity.Document document = com.clacjz.medical.rag.model.entity.Document.builder()
                .id(documentId).ownerId(TEST_USER_ID).build();
        when(documentPermissionService.getDocumentWithCheck(documentId)).thenReturn(document);
        when(documentPermissionService.canModifyDocumentPermission(operator, document)).thenReturn(true);

        ResponseEntity<Result<DocumentPermissionDTO>> response =
                documentPermissionController.getDocumentPermission(documentId, mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("删除用户权限成功")
    void removeUserPermission_Success() {
        String documentId = "doc-123";
        String targetUserId = "user-456";

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "Test Agent");

        doNothing().when(documentPermissionService)
                .removeUserPermission(eq(documentId), eq(targetUserId), eq(TEST_USER_ID), any(), any());

        ResponseEntity<Result<Void>> response =
                documentPermissionController.removeUserPermission(
                        documentId, targetUserId, mockUserDetails, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("用户权限移除成功", response.getBody().getMessage());
    }

    @Test
    @DisplayName("检查访问权限时 userDetails 为 null 抛异常")
    void checkAccess_NoUserDetails_Throws() {
        assertThrows(BizException.class,
                () -> documentPermissionController.checkAccess("doc-1", null));
    }

    @Test
    @DisplayName("批量检查时 userDetails 为 null 抛异常")
    void batchCheckAccess_NoUserDetails_Throws() {
        BatchAccessCheckRequest req = BatchAccessCheckRequest.builder()
                .documentIds(List.of("d-1")).build();
        assertThrows(BizException.class,
                () -> documentPermissionController.batchCheckAccess(req, null));
    }

    @Test
    @DisplayName("获取文档权限时 userDetails 为 null 抛异常")
    void getDocumentPermission_NoUserDetails_Throws() {
        assertThrows(BizException.class,
                () -> documentPermissionController.getDocumentPermission("doc-1", null));
    }

    @Test
    @DisplayName("更新文档权限成功")
    void updateDocumentPermission_Success() {
        String documentId = "doc-1";
        UpdateDocumentPermissionRequest req = new UpdateDocumentPermissionRequest();
        req.setPermissionType(com.clacjz.medical.rag.model.enums.PermissionTypeEnums.DEPARTMENT_WIDE);

        DocumentPermissionDTO dto = new DocumentPermissionDTO();
        dto.setDocumentId(documentId);

        when(documentPermissionService.updateDocumentPermission(
                eq(documentId), any(), eq(TEST_USER_ID), any(), any()))
                .thenReturn(dto);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("127.0.0.1");

        ResponseEntity<Result<DocumentPermissionDTO>> response =
                documentPermissionController.updateDocumentPermission(
                        documentId, req, mockUserDetails, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("文档权限更新成功", response.getBody().getMessage());
    }

    @Test
    @DisplayName("更新文档权限时 userDetails 为 null 抛异常")
    void updateDocumentPermission_NoUserDetails_Throws() {
        UpdateDocumentPermissionRequest req = new UpdateDocumentPermissionRequest();
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();

        assertThrows(BizException.class,
                () -> documentPermissionController.updateDocumentPermission(
                        "doc-1", req, null, httpRequest));
    }

    @Test
    @DisplayName("添加用户权限成功")
    void addUserPermission_Success() {
        String documentId = "doc-1";
        AddDocumentUserPermissionRequest req = new AddDocumentUserPermissionRequest();
        req.setUserId("u-1");
        req.setPermissionLevel(com.clacjz.medical.rag.model.enums.PermissionLevelEnums.READ);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("127.0.0.1");

        doNothing().when(documentPermissionService)
                .addUserPermission(eq(documentId), eq(req), eq(TEST_USER_ID), any(), any());

        ResponseEntity<Result<Void>> response =
                documentPermissionController.addUserPermission(documentId, req, mockUserDetails, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("用户权限添加成功", response.getBody().getMessage());
    }

    @Test
    @DisplayName("添加用户权限时 userDetails 为 null 抛异常")
    void addUserPermission_NoUserDetails_Throws() {
        AddDocumentUserPermissionRequest req = new AddDocumentUserPermissionRequest();
        req.setUserId("u-1");
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();

        assertThrows(BizException.class,
                () -> documentPermissionController.addUserPermission(
                        "doc-1", req, null, httpRequest));
    }

    @Test
    @DisplayName("用户不存在时抛USER_NOT_FOUND异常")
    void checkAccess_UserNotFound_Throws() {
        when(userMapper.selectByUsername("testuser")).thenReturn(null);
        assertThrows(BizException.class,
                () -> documentPermissionController.checkAccess("doc-1", mockUserDetails));
    }
}
