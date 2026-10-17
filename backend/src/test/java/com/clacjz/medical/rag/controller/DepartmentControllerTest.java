package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.*;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.DepartmentConfigService;
import com.clacjz.medical.rag.service.DepartmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DepartmentController 单元测试")
class DepartmentControllerTest {

    @Mock
    private DepartmentService departmentService;

    @Mock
    private DepartmentConfigService departmentConfigService;

    @InjectMocks
    private DepartmentController departmentController;

    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        userDetails = User.builder()
                .username("admin")
                .password("p")
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN")))
                .build();
    }

    private DepartmentDTO createDTO(String id, String name) {
        return DepartmentDTO.builder()
                .id(id)
                .name(name)
                .code("CODE-" + id)
                .description("Test")
                .userCount(10L)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("getDepartmentTree")
    class GetDepartmentTreeTests {

        @Test
        @DisplayName("返回部门树")
        void shouldReturnDepartmentTree() {
            when(departmentService.getDepartmentTree()).thenReturn(List.of(
                    createDTO("d-1", "内科")
            ));

            ResponseEntity<Result<List<DepartmentDTO>>> response = departmentController.getDepartmentTree();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(1, response.getBody().getData().size());
        }

        @Test
        @DisplayName("空列表")
        void shouldReturnEmptyList() {
            when(departmentService.getDepartmentTree()).thenReturn(List.of());

            ResponseEntity<Result<List<DepartmentDTO>>> response = departmentController.getDepartmentTree();

            assertEquals(0, response.getBody().getData().size());
        }
    }

    @Nested
    @DisplayName("getDefaultDepartment")
    class GetDefaultDepartmentTests {

        @Test
        @DisplayName("已设置默认部门时返回")
        void shouldReturnDefaultDepartment() {
            when(departmentConfigService.getDefaultDepartment()).thenReturn(createDTO("d-1", "默认部门"));

            ResponseEntity<Result<DepartmentDTO>> response = departmentController.getDefaultDepartment();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("默认部门", response.getBody().getData().getName());
        }

        @Test
        @DisplayName("未设置默认部门时返回 null 但不抛异常")
        void shouldReturnNullWhenNotSet() {
            when(departmentConfigService.getDefaultDepartment()).thenReturn(null);

            ResponseEntity<Result<DepartmentDTO>> response = departmentController.getDefaultDepartment();

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("setDefaultDepartment")
    class SetDefaultDepartmentTests {

        @Test
        @DisplayName("成功设置")
        void shouldSetDefaultDepartment() {
            SetDefaultDepartmentRequest req = new SetDefaultDepartmentRequest();
            req.setDepartmentId("d-1");
            when(departmentConfigService.setDefaultDepartment(eq("d-1"), eq("admin"), any(), any()))
                    .thenReturn(createDTO("d-1", "默认"));

            ResponseEntity<Result<DepartmentDTO>> response = departmentController.setDefaultDepartment(req, userDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("userDetails 为 null 抛 BizException")
        void shouldThrowWhenNoUser() {
            SetDefaultDepartmentRequest req = new SetDefaultDepartmentRequest();
            req.setDepartmentId("d-1");
            assertThrows(BizException.class,
                    () -> departmentController.setDefaultDepartment(req, null));
        }
    }

    @Nested
    @DisplayName("unsetDefaultDepartment")
    class UnsetDefaultDepartmentTests {

        @Test
        @DisplayName("成功取消")
        void shouldUnset() {
            doNothing().when(departmentConfigService).unsetDefaultDepartment(anyString(), anyString());

            ResponseEntity<Result<Void>> response = departmentController.unsetDefaultDepartment(userDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("getDepartmentById")
    class GetDepartmentByIdTests {

        @Test
        @DisplayName("成功")
        void shouldReturn() {
            when(departmentService.getDepartmentById("d-1")).thenReturn(createDTO("d-1", "内科"));

            ResponseEntity<Result<DepartmentDTO>> response = departmentController.getDepartmentById("d-1");

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("部门不存在抛异常")
        void shouldThrowWhenNotFound() {
            when(departmentService.getDepartmentById("missing"))
                    .thenThrow(new BizException(ErrorCode.DEPARTMENT_NOT_FOUND, "部门不存在"));

            assertThrows(BizException.class,
                    () -> departmentController.getDepartmentById("missing"));
        }
    }

    @Nested
    @DisplayName("checkDepartmentDelete")
    class CheckDepartmentDeleteTests {

        @Test
        @DisplayName("可以删除")
        void shouldReturnCanDelete() {
            DepartmentService.DepartmentDeleteCheckResult result = mock(DepartmentService.DepartmentDeleteCheckResult.class);
            when(departmentService.checkDepartmentDelete("d-1")).thenReturn(result);

            ResponseEntity<Result<DepartmentService.DepartmentDeleteCheckResult>> response = departmentController.checkDepartmentDelete("d-1");

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("createDepartment")
    class CreateDepartmentTests {

        @Test
        @DisplayName("成功创建")
        void shouldCreate() {
            CreateDepartmentRequest req = new CreateDepartmentRequest();
            req.setName("新部门");
            req.setCode("NEW");
            when(departmentService.createDepartment(any(), anyString())).thenReturn(createDTO("d-new", "新部门"));

            ResponseEntity<Result<DepartmentDTO>> response = departmentController.createDepartment(req, userDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("updateDepartment")
    class UpdateDepartmentTests {

        @Test
        @DisplayName("成功更新")
        void shouldUpdate() {
            UpdateDepartmentRequest req = new UpdateDepartmentRequest();
            req.setName("更新");
            when(departmentService.updateDepartment(eq("d-1"), any(), anyString())).thenReturn(createDTO("d-1", "更新"));

            ResponseEntity<Result<DepartmentDTO>> response = departmentController.updateDepartment("d-1", req, userDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("deleteDepartment")
    class DeleteDepartmentTests {

        @Test
        @DisplayName("成功删除")
        void shouldDelete() {
            doNothing().when(departmentService).deleteDepartment("d-1", "admin");

            ResponseEntity<Result<Void>> response = departmentController.deleteDepartment("d-1", userDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("getDepartmentUsers")
    class GetDepartmentUsersTests {

        @Test
        @DisplayName("成功")
        void shouldReturnUsers() {
            when(departmentService.getDepartmentUsers("d-1")).thenReturn(List.of());

            ResponseEntity<Result<List<UserDepartmentDTO>>> response = departmentController.getDepartmentUsers("d-1");

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("addUserToDepartment")
    class AddUserToDepartmentTests {

        @Test
        @DisplayName("成功")
        void shouldAddUser() {
            Map<String, Object> request = Map.of("userId", "u-1", "position", "医生");
            doNothing().when(departmentService).addUserToDepartment(eq("u-1"), eq("d-1"), eq(false), eq("医生"), eq("admin"));

            ResponseEntity<Result<Void>> response = departmentController.addUserToDepartment("d-1", request, userDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("removeUserFromDepartment")
    class RemoveUserTests {

        @Test
        @DisplayName("成功")
        void shouldRemove() {
            doNothing().when(departmentService).removeUserFromDepartment("u-1", "d-1", "admin");

            ResponseEntity<Result<Void>> response = departmentController.removeUserFromDepartment("d-1", "u-1", userDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("batchAssignUsersToDepartment")
    class BatchAssignTests {

        @Test
        @DisplayName("成功")
        void shouldBatchAssign() {
            BatchAssignUserRequest req = new BatchAssignUserRequest();
            req.setUserIds(List.of("u-1", "u-2"));
            when(departmentConfigService.batchAssignUsersToDepartment(eq("d-1"), any(), eq("admin")))
                    .thenReturn(Map.of("assigned", 2));

            ResponseEntity<Result<Map<String, Object>>> response =
                    departmentController.batchAssignUsersToDepartment("d-1", req, userDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("getUnassignedUsers")
    class GetUnassignedUsersTests {

        @Test
        @DisplayName("成功")
        void shouldReturn() {
            when(departmentConfigService.getUnassignedUsers()).thenReturn(List.of());

            ResponseEntity<Result<List<UserDTO>>> response = departmentController.getUnassignedUsers();

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("assignUnassignedUsersToDefault")
    class AssignUnassignedTests {

        @Test
        @DisplayName("成功")
        void shouldAssign() {
            when(departmentConfigService.assignUnassignedUsersToDefault("admin"))
                    .thenReturn(5);

            ResponseEntity<Result<Map<String, Integer>>> response =
                    departmentController.assignUnassignedUsersToDefault(userDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(5, response.getBody().getData().get("assignedCount"));
        }
    }

    @Nested
    @DisplayName("getConfigLogs")
    class GetConfigLogsTests {

        @Test
        @DisplayName("成功")
        void shouldReturn() {
            com.baomidou.mybatisplus.core.metadata.IPage<DepartmentConfigLogDTO> page =
                    mock(com.baomidou.mybatisplus.core.metadata.IPage.class);
            when(departmentConfigService.getConfigLogs(anyInt(), anyInt(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(page);

            ResponseEntity<Result<com.baomidou.mybatisplus.core.metadata.IPage<DepartmentConfigLogDTO>>> response =
                    departmentController.getConfigLogs(0, 20, "DEPT", "d-1", "admin", "2026-01-01");

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("getConfigLogsByDepartment")
    class GetConfigLogsByDeptTests {

        @Test
        @DisplayName("成功")
        void shouldReturn() {
            when(departmentConfigService.getConfigLogsByTarget(eq("DEPARTMENT"), eq("d-1"))).thenReturn(List.of());

            ResponseEntity<Result<List<DepartmentConfigLogDTO>>> response =
                    departmentController.getConfigLogsByDepartment("d-1");

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }
}
