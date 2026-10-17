package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.context.TenantContext;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.AddCollectionUserPermissionRequest;
import com.clacjz.medical.rag.model.dto.CollectionPermissionDTO;
import com.clacjz.medical.rag.model.dto.UpdateCollectionPermissionRequest;
import com.clacjz.medical.rag.model.entity.Collection;
import com.clacjz.medical.rag.model.enums.CollectionPermissionLevelEnums;
import com.clacjz.medical.rag.model.enums.CollectionPermissionTypeEnums;
import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.CollectionPermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CollectionPermissionController 单元测试")
class CollectionPermissionControllerTest {

    @Mock
    private CollectionPermissionService collectionPermissionService;

    private CollectionPermissionController controller;

    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        controller = new CollectionPermissionController(collectionPermissionService);
        userDetails = User.builder()
                .username("alice")
                .password("p")
                .authorities("ROLE_USER")
                .build();
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("GET /collections/{id}/permissions")
    class GetTests {

        @Test
        @DisplayName("成功返回权限配置（有权限的用户）")
        void shouldReturnPermission() {
            CollectionPermissionDTO dto = new CollectionPermissionDTO();
            dto.setCollectionId("col-1");
            when(collectionPermissionService.getCollectionPermission("col-1")).thenReturn(dto);

            // 模拟权限校验通过
            com.clacjz.medical.rag.model.entity.User operator = com.clacjz.medical.rag.model.entity.User.builder().id("user-1").role(UserRoleEnum.SYSTEM_ADMIN).build();
            Collection collection = Collection.builder().id("col-1").ownerId("user-1").build();
            when(collectionPermissionService.getOperatorWithCheck(any())).thenReturn(operator);
            when(collectionPermissionService.getCollectionWithCheck("col-1")).thenReturn(collection);
            when(collectionPermissionService.canModifyCollectionPermission(operator, collection)).thenReturn(true);

            ResponseEntity<Result<CollectionPermissionDTO>> response = controller.getCollectionPermission("col-1", userDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(200, response.getBody().getCode());
            assertEquals("col-1", response.getBody().getData().getCollectionId());
        }

        @Test
        @DisplayName("无权限用户查询时抛 BizException")
        void shouldDenyWithoutPermission() {
            com.clacjz.medical.rag.model.entity.User operator = com.clacjz.medical.rag.model.entity.User.builder().id("user-1").role(UserRoleEnum.DEPT_USER).build();
            Collection collection = Collection.builder().id("col-1").ownerId("other-user").build();
            when(collectionPermissionService.getOperatorWithCheck(any())).thenReturn(operator);
            when(collectionPermissionService.getCollectionWithCheck("missing")).thenReturn(collection);
            when(collectionPermissionService.canModifyCollectionPermission(operator, collection)).thenReturn(false);

            BizException ex = assertThrows(BizException.class,
                    () -> controller.getCollectionPermission("missing", userDetails));
            assertEquals(ErrorCode.PERMISSION_DENIED, ex.getErrorCode());
        }

        @Test
        @DisplayName("service 抛 BizException 时原样向上抛")
        void shouldPropagateBizException() {
            when(collectionPermissionService.getCollectionWithCheck("missing"))
                    .thenThrow(new BizException(ErrorCode.COLLECTION_NOT_FOUND, "知识库不存在"));

            BizException ex = assertThrows(BizException.class,
                    () -> controller.getCollectionPermission("missing", userDetails));
            assertEquals(ErrorCode.COLLECTION_NOT_FOUND, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("PUT /collections/{id}/permissions")
    class UpdateTests {

        @Test
        @DisplayName("成功更新权限")
        void shouldUpdatePermission() {
            UpdateCollectionPermissionRequest req = new UpdateCollectionPermissionRequest();
            req.setPermissionType(CollectionPermissionTypeEnums.RESTRICTED);
            CollectionPermissionDTO dto = new CollectionPermissionDTO();
            dto.setCollectionId("col-1");
            when(collectionPermissionService.updateCollectionPermission(eq("col-1"), any())).thenReturn(dto);

            ResponseEntity<Result<CollectionPermissionDTO>> response =
                    controller.updateCollectionPermission("col-1", req, userDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("col-1", response.getBody().getData().getCollectionId());
        }

        @Test
        @DisplayName("userDetails 为 null 时抛 BizException")
        void shouldThrowWhenUserDetailsIsNull() {
            UpdateCollectionPermissionRequest req = new UpdateCollectionPermissionRequest();

            BizException ex = assertThrows(BizException.class,
                    () -> controller.updateCollectionPermission("col-1", req, null));
            assertEquals(ErrorCode.AUTHENTICATION_FAILED, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("POST /collections/{id}/permissions/users")
    class AddUserTests {

        @Test
        @DisplayName("成功添加用户权限")
        void shouldAddUserPermission() {
            AddCollectionUserPermissionRequest req = new AddCollectionUserPermissionRequest();
            req.setUserId("user-2");
            req.setPermissionLevel(CollectionPermissionLevelEnums.READ);

            ResponseEntity<Result<Void>> response =
                    controller.addUserPermission("col-1", req, userDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(collectionPermissionService).addUserPermission(eq("col-1"), eq(req));
        }

        @Test
        @DisplayName("userDetails 为 null 时抛 BizException")
        void shouldThrowWhenUserDetailsIsNull() {
            AddCollectionUserPermissionRequest req = new AddCollectionUserPermissionRequest();
            req.setUserId("user-2");

            assertThrows(BizException.class,
                    () -> controller.addUserPermission("col-1", req, null));
        }
    }

    @Nested
    @DisplayName("DELETE /collections/{id}/permissions/users/{userId}")
    class RemoveUserTests {

        @Test
        @DisplayName("成功移除用户权限")
        void shouldRemoveUserPermission() {
            ResponseEntity<Result<Void>> response =
                    controller.removeUserPermission("col-1", "user-2", userDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(collectionPermissionService).removeUserPermission("col-1", "user-2");
        }

        @Test
        @DisplayName("userDetails 为 null 时抛 BizException")
        void shouldThrowWhenUserDetailsIsNull() {
            assertThrows(BizException.class,
                    () -> controller.removeUserPermission("col-1", "user-2", null));
        }
    }

    @Nested
    @DisplayName("GET /collections/{id}/permissions/check")
    class CheckAccessTests {

        @Test
        @DisplayName("从 TenantContext 取 userId 后委托给 service")
        void shouldUseTenantContextUserId() {
            TenantContext.setUserId("user-42");
            CollectionPermissionService.CollectionAccessCheckResult result =
                    CollectionPermissionService.CollectionAccessCheckResult.allowed("ok", CollectionPermissionLevelEnums.READ);
            when(collectionPermissionService.checkCollectionAccess("user-42", "col-1")).thenReturn(result);

            ResponseEntity<Result<CollectionPermissionService.CollectionAccessCheckResult>> response =
                    controller.checkAccess("col-1");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(collectionPermissionService).checkCollectionAccess("user-42", "col-1");
        }

        @Test
        @DisplayName("TenantContext 无 userId 时也能正常调用（传 null）")
        void shouldHandleMissingTenantContext() {
            CollectionPermissionService.CollectionAccessCheckResult result =
                    CollectionPermissionService.CollectionAccessCheckResult.denied("no user");
            when(collectionPermissionService.checkCollectionAccess(isNull(), eq("col-1"))).thenReturn(result);

            ResponseEntity<Result<CollectionPermissionService.CollectionAccessCheckResult>> response =
                    controller.checkAccess("col-1");

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }
}
