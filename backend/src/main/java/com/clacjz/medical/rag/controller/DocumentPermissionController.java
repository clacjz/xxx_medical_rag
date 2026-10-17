package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.*;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.DocumentPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.model.entity.User;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@Tag(name = "Document Permission API", description = "文档权限相关接口")
public class DocumentPermissionController {

    private final DocumentPermissionService documentPermissionService;
    private final UserMapper userMapper;

    /**
     * 从UserDetails解析出用户ID（UUID主键），用于传递给Service层。
     * UserDetails.getUsername()返回的是用户名字符串，而非数据库主键ID，
     * 直接传给Service会导致userMapper.selectById()查不到记录。
     */
    private String resolveUserId(UserDetails userDetails, String location) {
        String username = Optional.ofNullable(userDetails)
                .map(UserDetails::getUsername)
                .orElseThrow(() -> new BizException(ErrorCode.AUTHENTICATION_FAILED,
                        String.format("[%s] 用户信息不存在，请重新登录", location)));
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND,
                    String.format("[%s] 用户不存在: %s", location, username));
        }
        return user.getId();
    }

    @GetMapping("/{documentId}/access-check")
    @Operation(summary = "检查文档访问权限", description = "检查当前用户对指定文档的访问权限")
    public ResponseEntity<Result<AccessCheckResult>> checkAccess(
            @Parameter(description = "文档ID") @PathVariable String documentId,
            @AuthenticationPrincipal UserDetails userDetails) {

        // NPE防护：从UserDetails解析userId（UUID主键），而非username字符串
        String userId = resolveUserId(userDetails, "DocumentPermissionController.checkAccess");

        AccessCheckResult result = documentPermissionService.checkDocumentAccess(userId, documentId);
        return ResponseEntity.ok(Result.success(result));
    }

    @PostMapping("/batch-access-check")
    @Operation(summary = "批量检查文档访问权限", description = "批量检查当前用户对多个文档的访问权限")
    public ResponseEntity<Result<BatchAccessCheckResult>> batchCheckAccess(
            @Valid @RequestBody BatchAccessCheckRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        // NPE防护：确保用户信息不为null，解析userId
        String userId = resolveUserId(userDetails, "DocumentPermissionController.batchCheckAccess");

        log.info("Batch access check request: documentIds={}, user={}",
                request.getDocumentIds().size(), userId);
        BatchAccessCheckResult result = documentPermissionService.batchCheckAccess(
                userId, request.getDocumentIds());
        return ResponseEntity.ok(Result.success(result));
    }

    @GetMapping("/{documentId}/permissions")
    @Operation(summary = "获取文档权限配置", description = "获取指定文档的权限配置信息，需要所有者或管理员权限")
    public ResponseEntity<Result<DocumentPermissionDTO>> getDocumentPermission(
            @Parameter(description = "文档ID") @PathVariable String documentId,
            @AuthenticationPrincipal UserDetails userDetails) {

        // NPE防护：确保用户信息不为null，解析userId
        String userId = resolveUserId(userDetails, "DocumentPermissionController.getDocumentPermission");

        // 权限校验：只有所有者或管理员可查看权限配置详情（包含用户列表等敏感信息）
        User operator = userMapper.selectById(userId);
        if (operator == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        com.clacjz.medical.rag.model.entity.Document document = documentPermissionService.getDocumentWithCheck(documentId);
        if (!documentPermissionService.canModifyDocumentPermission(operator, document)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "无权查看此文档权限配置");
        }

        log.info("Get document permission: documentId={}, user={}", documentId, userId);
        DocumentPermissionDTO permission = documentPermissionService.getDocumentPermission(documentId);
        return ResponseEntity.ok(Result.success(permission));
    }

    @PostMapping("/{documentId}/permissions")
    @Operation(summary = "更新文档权限", description = "更新文档的访问权限配置")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<DocumentPermissionDTO>> updateDocumentPermission(
            @Parameter(description = "文档ID") @PathVariable String documentId,
            @Valid @RequestBody UpdateDocumentPermissionRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {

        // NPE防护：确保用户信息不为null，解析userId
        String userId = resolveUserId(userDetails, "DocumentPermissionController.updateDocumentPermission");

        log.info("Update document permission: documentId={}, type={}, user={}",
                documentId, request.getPermissionType(), userId);

        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        DocumentPermissionDTO permission = documentPermissionService.updateDocumentPermission(
                documentId, request, userId, ipAddress, userAgent);
        return ResponseEntity.ok(Result.success("文档权限更新成功", permission));
    }

    @PostMapping("/{documentId}/permissions/users")
    @Operation(summary = "添加文档指定用户权限", description = "为文档添加指定用户的访问权限")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<Void>> addUserPermission(
            @Parameter(description = "文档ID") @PathVariable String documentId,
            @Valid @RequestBody AddDocumentUserPermissionRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {

        // NPE防护：确保用户信息不为null，解析userId
        String userId = resolveUserId(userDetails, "DocumentPermissionController.addUserPermission");

        log.info("Add user permission: documentId={}, userId={}, level={}, operator={}",
                documentId, request.getUserId(), request.getPermissionLevel(), userId);

        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        documentPermissionService.addUserPermission(
                documentId, request, userId, ipAddress, userAgent);
        return ResponseEntity.ok(Result.success("用户权限添加成功", null));
    }

    @DeleteMapping("/{documentId}/permissions/users/{targetUserId}")
    @Operation(summary = "移除文档指定用户权限", description = "移除文档指定用户的访问权限")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<Void>> removeUserPermission(
            @Parameter(description = "文档ID") @PathVariable String documentId,
            @Parameter(description = "目标用户ID") @PathVariable String targetUserId,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {

        // NPE防护：确保用户信息不为null，解析userId
        String userId = resolveUserId(userDetails, "DocumentPermissionController.removeUserPermission");

        log.info("Remove user permission: documentId={}, targetUserId={}, operator={}",
                documentId, targetUserId, userId);

        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        documentPermissionService.removeUserPermission(
                documentId, targetUserId, userId, ipAddress, userAgent);
        return ResponseEntity.ok(Result.success("用户权限移除成功", null));
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}