package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.AuditLogDTO;
import com.clacjz.medical.rag.model.enums.AuditActionEnums;
import com.clacjz.medical.rag.model.enums.AuditLevelEnums;
import com.clacjz.medical.rag.model.vo.PageResult;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.AuditService;
import com.clacjz.medical.rag.service.ExportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditController 单元测试")
class AuditControllerTest {

    @Mock
    private AuditService auditService;

    @Mock
    private ExportService exportService;

    @InjectMocks
    private AuditController auditController;

    private MockHttpServletRequest mockRequest;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mockRequest = new MockHttpServletRequest();
        ServletRequestAttributes attributes = new ServletRequestAttributes(mockRequest);
        RequestContextHolder.setRequestAttributes(attributes);
    }

    @Nested
    @DisplayName("获取审计日志列表测试")
    class ListAuditLogsTests {

        @Test
        @DisplayName("获取审计日志列表成功")
        void listAuditLogs_Success() {
            AuditLogDTO log = AuditLogDTO.builder()
                    .id(UUID.randomUUID().toString())
                    .userId("user-123")
                    .action(AuditActionEnums.LOGIN)
                    .level(AuditLevelEnums.INFO)
                    .build();

            Page<AuditLogDTO> page = new Page<>(1, 20);
            page.setRecords(List.of(log));
            page.setTotal(1);
            IPage<AuditLogDTO> ipage = page;
            long startTime = System.currentTimeMillis() - 86400000;
            long endTime = System.currentTimeMillis();

            when(auditService.getAuditLogs(eq(0), eq(20), isNull(), isNull(), isNull(),
                    any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(ipage);

            ResponseEntity<Result<PageResult<AuditLogDTO>>> response =
                    auditController.listAuditLogs(0, 20, null, null, null, startTime, endTime);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(200, response.getBody().getCode());
            assertEquals(1, response.getBody().getData().getTotal());
        }

        @Test
        @DisplayName("获取审计日志列表 - 按用户ID筛选")
        void listAuditLogs_WithUserIdFilter() {
            Page<AuditLogDTO> page = new Page<>(1, 20);
            page.setRecords(List.of());
            page.setTotal(0);
            IPage<AuditLogDTO> ipage = page;
            long startTime = System.currentTimeMillis() - 86400000;
            long endTime = System.currentTimeMillis();

            when(auditService.getAuditLogs(eq(0), eq(20), eq("user-123"), isNull(), isNull(),
                    any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(ipage);

            ResponseEntity<Result<PageResult<AuditLogDTO>>> response =
                    auditController.listAuditLogs(0, 20, "user-123", null, null, startTime, endTime);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("获取审计日志列表 - 按操作类型筛选")
        void listAuditLogs_WithActionFilter() {
            Page<AuditLogDTO> page = new Page<>(1, 20);
            page.setRecords(List.of());
            page.setTotal(0);
            IPage<AuditLogDTO> ipage = page;
            long startTime = System.currentTimeMillis() - 86400000;
            long endTime = System.currentTimeMillis();

            when(auditService.getAuditLogs(eq(0), eq(20), isNull(), eq("LOGIN"), isNull(),
                    any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(ipage);

            ResponseEntity<Result<PageResult<AuditLogDTO>>> response =
                    auditController.listAuditLogs(0, 20, null, "LOGIN", null, startTime, endTime);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("获取审计日志列表 - 按审计级别筛选")
        void listAuditLogs_WithLevelFilter() {
            Page<AuditLogDTO> page = new Page<>(1, 20);
            page.setRecords(List.of());
            page.setTotal(0);
            IPage<AuditLogDTO> ipage = page;
            long startTime = System.currentTimeMillis() - 86400000;
            long endTime = System.currentTimeMillis();

            when(auditService.getAuditLogs(eq(0), eq(20), isNull(), isNull(), eq(AuditLevelEnums.WARNING),
                    any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(ipage);

            ResponseEntity<Result<PageResult<AuditLogDTO>>> response =
                    auditController.listAuditLogs(0, 20, null, null, AuditLevelEnums.WARNING, startTime, endTime);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("获取审计日志列表返回空结果")
        void listAuditLogs_EmptyResult() {
            Page<AuditLogDTO> emptyPage = new Page<>(1, 20);
            emptyPage.setRecords(List.of());
            emptyPage.setTotal(0);
            IPage<AuditLogDTO> ipage = emptyPage;
            long startTime = System.currentTimeMillis() - 86400000;
            long endTime = System.currentTimeMillis();

            when(auditService.getAuditLogs(anyInt(), anyInt(), any(), any(), any(),
                    any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(ipage);

            ResponseEntity<Result<PageResult<AuditLogDTO>>> response =
                    auditController.listAuditLogs(0, 20, null, null, null, startTime, endTime);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(0, response.getBody().getData().getTotal());
        }
    }

    @Nested
    @DisplayName("获取审计日志详情测试")
    class GetAuditLogTests {

        @Test
        @DisplayName("获取审计日志详情成功")
        void getAuditLog_Success() {
            String logId = UUID.randomUUID().toString();
            AuditLogDTO log = AuditLogDTO.builder()
                    .id(logId)
                    .userId("user-123")
                    .action(AuditActionEnums.LOGIN)
                    .level(AuditLevelEnums.INFO)
                    .build();

            when(auditService.getAuditLogById(logId)).thenReturn(log);

            ResponseEntity<Result<AuditLogDTO>> response =
                    auditController.getAuditLog(logId);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(logId, response.getBody().getData().getId());
        }

        @Test
        @DisplayName("获取审计日志详情失败 - 日志不存在")
        void getAuditLog_Failure_NotFound() {
            String logId = "nonexistent";
            when(auditService.getAuditLogById(logId)).thenThrow(
                    new BizException(ErrorCode.AUDIT_LOG_NOT_FOUND, "审计日志不存在")
            );

            assertThrows(BizException.class, () -> auditController.getAuditLog(logId));
        }
    }

    @Nested
    @DisplayName("导出审计日志测试")
    class ExportAuditLogsTests {

        @Test
        @DisplayName("导出审计日志成功 - CSV格式")
        void exportAuditLogs_Success_CsvFormat() {
            LocalDateTime startTime = LocalDateTime.now().minusDays(1);
            LocalDateTime endTime = LocalDateTime.now();
            String csvContent = "ID,用户ID,用户名,显示名称,操作,级别,资源类型,资源ID,IP地址,时间,状态,详情\n1,user-123,testuser,测试用户,LOGIN,INFO,AUTH,,192.168.1.1,2026-04-08,success,";

            when(exportService.exportAuditLogsToCsv(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(csvContent);

            ResponseEntity<?> response =
                    auditController.exportAuditLogs(startTime, endTime, "csv");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody() instanceof String);
            assertTrue(((String) response.getBody()).contains("ID,用户ID,用户名,显示名称"));
            
            HttpHeaders headers = response.getHeaders();
            assertTrue(headers.getContentType().toString().contains("text/csv"));
        }

        @Test
        @DisplayName("导出审计日志成功 - JSON格式")
        void exportAuditLogs_Success_JsonFormat() {
            LocalDateTime startTime = LocalDateTime.now().minusDays(1);
            LocalDateTime endTime = LocalDateTime.now();
            String jsonContent = "{\"exportTime\":\"2026-04-08\",\"totalCount\":2,\"logs\":[]}";

            when(exportService.exportAuditLogsToJson(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(jsonContent);

            ResponseEntity<?> response =
                    auditController.exportAuditLogs(startTime, endTime, "json");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody() instanceof String);
            assertTrue(((String) response.getBody()).contains("\"exportTime\""));
            assertTrue(((String) response.getBody()).contains("\"totalCount\""));
            
            HttpHeaders headers = response.getHeaders();
            assertTrue(headers.getContentType().toString().contains("application/json"));
        }

        @Test
        @DisplayName("导出审计日志成功 - 默认CSV格式")
        void exportAuditLogs_Success_DefaultFormat() {
            LocalDateTime startTime = LocalDateTime.now().minusDays(1);
            LocalDateTime endTime = LocalDateTime.now();
            String csvContent = "data";

            when(exportService.exportAuditLogsToCsv(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(csvContent);

            ResponseEntity<?> response =
                    auditController.exportAuditLogs(startTime, endTime, "csv");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(exportService).exportAuditLogsToCsv(startTime, endTime);
        }

        @Test
        @DisplayName("导出审计日志忽略大小写格式参数")
        void exportAuditLogs_CaseInsensitiveFormat() {
            LocalDateTime startTime = LocalDateTime.now().minusDays(1);
            LocalDateTime endTime = LocalDateTime.now();
            String jsonContent = "{\"exportTime\":\"2026-04-08\"}";

            when(exportService.exportAuditLogsToJson(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(jsonContent);

            ResponseEntity<?> response =
                    auditController.exportAuditLogs(startTime, endTime, "JSON");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(exportService).exportAuditLogsToJson(startTime, endTime);
        }

        @Test
        @DisplayName("导出审计日志失败 - 服务异常")
        void exportAuditLogs_Failure_ServiceException() {
            LocalDateTime startTime = LocalDateTime.now().minusDays(1);
            LocalDateTime endTime = LocalDateTime.now();

            when(exportService.exportAuditLogsToCsv(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenThrow(new RuntimeException("数据库连接失败"));

            assertThrows(RuntimeException.class,
                    () -> auditController.exportAuditLogs(startTime, endTime, "csv"));
        }
    }

    @Nested
    @DisplayName("获取审计动作类型测试")
    class GetAuditActionsTests {

        @Test
        @DisplayName("获取所有审计动作类型")
        void getAuditActions_Success() {
            ResponseEntity<Result<Object>> response = auditController.getAuditActions();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertArrayEquals(AuditActionEnums.values(), (AuditActionEnums[]) response.getBody().getData());
        }
    }

    @Nested
    @DisplayName("获取审计级别测试")
    class GetAuditLevelsTests {

        @Test
        @DisplayName("获取所有审计级别")
        void getAuditLevels_Success() {
            ResponseEntity<Result<Object>> response = auditController.getAuditLevels();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertArrayEquals(AuditLevelEnums.values(), (AuditLevelEnums[]) response.getBody().getData());
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class BoundaryTests {

        @Test
        @DisplayName("负数页码")
        void listAuditLogs_NegativePage() {
            Page<AuditLogDTO> page = new Page<>(1, 20);
            page.setRecords(List.of());
            page.setTotal(0);
            IPage<AuditLogDTO> ipage = page;
            long startTime = System.currentTimeMillis() - 86400000;
            long endTime = System.currentTimeMillis();

            when(auditService.getAuditLogs(eq(-1), eq(20), any(), any(), any(),
                    any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(ipage);

            ResponseEntity<Result<PageResult<AuditLogDTO>>> response =
                    auditController.listAuditLogs(-1, 20, null, null, null, startTime, endTime);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("零页大小")
        void listAuditLogs_ZeroPageSize() {
            Page<AuditLogDTO> page = new Page<>(1, 20);
            page.setRecords(List.of());
            page.setTotal(0);
            IPage<AuditLogDTO> ipage = page;
            long startTime = System.currentTimeMillis() - 86400000;
            long endTime = System.currentTimeMillis();

            when(auditService.getAuditLogs(eq(0), eq(0), any(), any(), any(),
                    any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(ipage);

            ResponseEntity<Result<PageResult<AuditLogDTO>>> response =
                    auditController.listAuditLogs(0, 0, null, null, null, startTime, endTime);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("Unix时间戳转换正确")
        void listAuditLogs_UnixTimestampConversion() {
            Page<AuditLogDTO> page = new Page<>(1, 20);
            page.setRecords(List.of());
            page.setTotal(0);
            IPage<AuditLogDTO> ipage = page;
            long startTimeMillis = 1609459200000L;
            long endTimeMillis = 1609545600000L;

            when(auditService.getAuditLogs(anyInt(), anyInt(), any(), any(), any(),
                    any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(ipage);

            auditController.listAuditLogs(0, 20, null, null, null, startTimeMillis, endTimeMillis);

            verify(auditService).getAuditLogs(eq(0), eq(20), isNull(), isNull(), isNull(),
                    eq(LocalDateTime.ofEpochSecond(1609459200, 0, ZoneOffset.of("+8"))),
                    eq(LocalDateTime.ofEpochSecond(1609545600, 0, ZoneOffset.of("+8"))));
        }
    }
}
