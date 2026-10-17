package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.PermissionAuditLogDTO;
import com.clacjz.medical.rag.model.enums.AuditOperationTypeEnums;
import com.clacjz.medical.rag.model.enums.AuditTargetTypeEnums;
import com.clacjz.medical.rag.model.vo.PageResult;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.PermissionAuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogController 单元测试")
class AuditLogControllerTest {

    @Mock
    private PermissionAuditService permissionAuditService;

    @InjectMocks
    private AuditLogController auditLogController;

    @Nested
    @DisplayName("查询权限变更日志测试")
    class GetPermissionAuditLogsTests {

        @Test
        @DisplayName("查询权限变更日志成功")
        void getPermissionAuditLogs_Success() {
            PermissionAuditLogDTO log = PermissionAuditLogDTO.builder()
                    .id(UUID.randomUUID().toString())
                    .operator(PermissionAuditLogDTO.OperatorDTO.builder()
                            .id("operator-123")
                            .username("testuser")
                            .displayName("Test User")
                            .build())
                    .targetType(AuditTargetTypeEnums.DOCUMENT)
                    .operationType(AuditOperationTypeEnums.GRANT)
                    .build();

            Page<PermissionAuditLogDTO> page = new Page<>(1, 20);
            page.setRecords(List.of(log));
            page.setTotal(1);
            IPage<PermissionAuditLogDTO> ipage = page;
            long startTime = System.currentTimeMillis() - 86400000;
            long endTime = System.currentTimeMillis();

            when(permissionAuditService.getAuditLogs(eq(0), eq(20), isNull(), isNull(), isNull(), isNull(),
                    any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(ipage);

            ResponseEntity<Result<PageResult<PermissionAuditLogDTO>>> response =
                    auditLogController.getPermissionAuditLogs(0, 20, null, null, null, null, startTime, endTime);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(200, response.getBody().getCode());
            assertEquals(1, response.getBody().getData().getTotal());
        }

        @Test
        @DisplayName("查询权限变更日志 - 按操作人筛选")
        void getPermissionAuditLogs_WithOperatorFilter() {
            Page<PermissionAuditLogDTO> page = new Page<>(1, 20);
            page.setRecords(List.of());
            page.setTotal(0);
            IPage<PermissionAuditLogDTO> ipage = page;
            long startTime = System.currentTimeMillis() - 86400000;
            long endTime = System.currentTimeMillis();

            when(permissionAuditService.getAuditLogs(eq(0), eq(20), eq("operator-123"), isNull(), isNull(), isNull(),
                    any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(ipage);

            ResponseEntity<Result<PageResult<PermissionAuditLogDTO>>> response =
                    auditLogController.getPermissionAuditLogs(0, 20, "operator-123", null, null, null, startTime, endTime);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("查询权限变更日志 - 按目标类型筛选")
        void getPermissionAuditLogs_WithTargetTypeFilter() {
            Page<PermissionAuditLogDTO> page = new Page<>(1, 20);
            page.setRecords(List.of());
            page.setTotal(0);
            IPage<PermissionAuditLogDTO> ipage = page;
            long startTime = System.currentTimeMillis() - 86400000;
            long endTime = System.currentTimeMillis();

            when(permissionAuditService.getAuditLogs(eq(0), eq(20), isNull(), eq(AuditTargetTypeEnums.DOCUMENT), isNull(), isNull(),
                    any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(ipage);

            ResponseEntity<Result<PageResult<PermissionAuditLogDTO>>> response =
                    auditLogController.getPermissionAuditLogs(0, 20, null, AuditTargetTypeEnums.DOCUMENT, null, null, startTime, endTime);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("查询权限变更日志 - 按操作类型筛选")
        void getPermissionAuditLogs_WithOperationTypeFilter() {
            Page<PermissionAuditLogDTO> page = new Page<>(1, 20);
            page.setRecords(List.of());
            page.setTotal(0);
            IPage<PermissionAuditLogDTO> ipage = page;
            long startTime = System.currentTimeMillis() - 86400000;
            long endTime = System.currentTimeMillis();

            when(permissionAuditService.getAuditLogs(eq(0), eq(20), isNull(), isNull(), isNull(), eq(AuditOperationTypeEnums.GRANT),
                    any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(ipage);

            ResponseEntity<Result<PageResult<PermissionAuditLogDTO>>> response =
                    auditLogController.getPermissionAuditLogs(0, 20, null, null, null, AuditOperationTypeEnums.GRANT, startTime, endTime);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("查询权限变更日志失败 - 开始时间为空")
        void getPermissionAuditLogs_Failure_StartTimeNull() {
            assertThrows(BizException.class,
                    () -> auditLogController.getPermissionAuditLogs(0, 20, null, null, null, null, null, System.currentTimeMillis()));
        }

        @Test
        @DisplayName("查询权限变更日志失败 - 结束时间为空")
        void getPermissionAuditLogs_Failure_EndTimeNull() {
            assertThrows(BizException.class,
                    () -> auditLogController.getPermissionAuditLogs(0, 20, null, null, null, null, System.currentTimeMillis(), null));
        }

        @Test
        @DisplayName("查询权限变更日志返回空结果")
        void getPermissionAuditLogs_EmptyResult() {
            Page<PermissionAuditLogDTO> emptyPage = new Page<>(1, 20);
            emptyPage.setRecords(List.of());
            emptyPage.setTotal(0);
            IPage<PermissionAuditLogDTO> ipage = emptyPage;
            long startTime = System.currentTimeMillis() - 86400000;
            long endTime = System.currentTimeMillis();

            when(permissionAuditService.getAuditLogs(anyInt(), anyInt(), any(), any(), any(), any(),
                    any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(ipage);

            ResponseEntity<Result<PageResult<PermissionAuditLogDTO>>> response =
                    auditLogController.getPermissionAuditLogs(0, 20, null, null, null, null, startTime, endTime);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(0, response.getBody().getData().getTotal());
        }
    }

    @Nested
    @DisplayName("获取最近权限变更记录测试")
    class GetRecentAuditLogsTests {

        @Test
        @DisplayName("获取最近权限变更记录成功")
        void getRecentAuditLogs_Success() {
            PermissionAuditLogDTO log = PermissionAuditLogDTO.builder()
                    .id(UUID.randomUUID().toString())
                    .operationType(AuditOperationTypeEnums.GRANT)
                    .build();

            when(permissionAuditService.getRecentAuditLogs(eq("DOCUMENT"), eq("doc-123"), eq(10)))
                    .thenReturn(List.of(log));

            ResponseEntity<Result<List<PermissionAuditLogDTO>>> response =
                    auditLogController.getRecentAuditLogs(AuditTargetTypeEnums.DOCUMENT, "doc-123", 10);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(1, response.getBody().getData().size());
        }

        @Test
        @DisplayName("获取最近权限变更记录 - 默认限制")
        void getRecentAuditLogs_DefaultLimit() {
            when(permissionAuditService.getRecentAuditLogs(eq("DOCUMENT"), eq("doc-123"), eq(10)))
                    .thenReturn(List.of());

            ResponseEntity<Result<List<PermissionAuditLogDTO>>> response =
                    auditLogController.getRecentAuditLogs(AuditTargetTypeEnums.DOCUMENT, "doc-123", 10);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("获取最近权限变更记录返回空结果")
        void getRecentAuditLogs_EmptyResult() {
            when(permissionAuditService.getRecentAuditLogs(anyString(), anyString(), anyInt()))
                    .thenReturn(List.of());

            ResponseEntity<Result<List<PermissionAuditLogDTO>>> response =
                    auditLogController.getRecentAuditLogs(AuditTargetTypeEnums.DOCUMENT, "doc-123", 10);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(0, response.getBody().getData().size());
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class BoundaryTests {

        @Test
        @DisplayName("负数页码")
        void getPermissionAuditLogs_NegativePage() {
            Page<PermissionAuditLogDTO> page = new Page<>(1, 20);
            page.setRecords(List.of());
            page.setTotal(0);
            IPage<PermissionAuditLogDTO> ipage = page;
            long startTime = System.currentTimeMillis() - 86400000;
            long endTime = System.currentTimeMillis();

            when(permissionAuditService.getAuditLogs(eq(-1), eq(20), any(), any(), any(), any(),
                    any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(ipage);

            ResponseEntity<Result<PageResult<PermissionAuditLogDTO>>> response =
                    auditLogController.getPermissionAuditLogs(-1, 20, null, null, null, null, startTime, endTime);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("零页大小")
        void getPermissionAuditLogs_ZeroPageSize() {
            Page<PermissionAuditLogDTO> page = new Page<>(1, 20);
            page.setRecords(List.of());
            page.setTotal(0);
            IPage<PermissionAuditLogDTO> ipage = page;
            long startTime = System.currentTimeMillis() - 86400000;
            long endTime = System.currentTimeMillis();

            when(permissionAuditService.getAuditLogs(eq(0), eq(0), any(), any(), any(), any(),
                    any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(ipage);

            ResponseEntity<Result<PageResult<PermissionAuditLogDTO>>> response =
                    auditLogController.getPermissionAuditLogs(0, 0, null, null, null, null, startTime, endTime);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("零限制获取最近记录")
        void getRecentAuditLogs_ZeroLimit() {
            when(permissionAuditService.getRecentAuditLogs(anyString(), anyString(), eq(0)))
                    .thenReturn(List.of());

            ResponseEntity<Result<List<PermissionAuditLogDTO>>> response =
                    auditLogController.getRecentAuditLogs(AuditTargetTypeEnums.DOCUMENT, "doc-123", 0);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("Unix时间戳毫秒级转换")
        void getPermissionAuditLogs_MillisecondTimestampConversion() {
            Page<PermissionAuditLogDTO> page = new Page<>(1, 20);
            page.setRecords(List.of());
            page.setTotal(0);
            IPage<PermissionAuditLogDTO> ipage = page;
            long startTimeMillis = 1609459200000L;
            long endTimeMillis = 1609545600000L;

            when(permissionAuditService.getAuditLogs(anyInt(), anyInt(), any(), any(), any(), any(),
                    any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(ipage);

            auditLogController.getPermissionAuditLogs(0, 20, null, null, null, null, startTimeMillis, endTimeMillis);

            verify(permissionAuditService).getAuditLogs(eq(0), eq(20), isNull(), isNull(), isNull(), isNull(),
                    eq(LocalDateTime.ofEpochSecond(1609459200, 0, ZoneOffset.ofHours(8))),
                    eq(LocalDateTime.ofEpochSecond(1609545600, 0, ZoneOffset.ofHours(8))));
        }
    }

    @Nested
    @DisplayName("响应结构验证测试")
    class ResponseStructureTests {

        @Test
        @DisplayName("分页查询响应包含分页信息")
        void getPermissionAuditLogs_ResponseContainsPaginationInfo() {
            PermissionAuditLogDTO log = PermissionAuditLogDTO.builder()
                    .id(UUID.randomUUID().toString())
                    .build();

            Page<PermissionAuditLogDTO> page = new Page<>(2, 20);
            page.setRecords(List.of(log));
            page.setTotal(50);
            IPage<PermissionAuditLogDTO> ipage = page;
            long startTime = System.currentTimeMillis() - 86400000;
            long endTime = System.currentTimeMillis();

            when(permissionAuditService.getAuditLogs(anyInt(), anyInt(), any(), any(), any(), any(),
                    any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(ipage);

            ResponseEntity<Result<PageResult<PermissionAuditLogDTO>>> response =
                    auditLogController.getPermissionAuditLogs(1, 20, null, null, null, null, startTime, endTime);

            PageResult<PermissionAuditLogDTO> pageResult = response.getBody().getData();
            assertEquals(50, pageResult.getTotal());
            assertEquals(2, pageResult.getPage());
            assertEquals(20, pageResult.getPageSize());
        }
    }
}
