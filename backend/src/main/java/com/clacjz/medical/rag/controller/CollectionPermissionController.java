package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.context.TenantContext;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.AddCollectionUserPermissionRequest;
import com.clacjz.medical.rag.model.dto.CollectionPermissionDTO;
import com.clacjz.medical.rag.model.dto.UpdateCollectionPermissionRequest;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.CollectionPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/collections/{collectionId}/permissions")
@RequiredArgsConstructor
@Tag(name = "Collection Permission API", description = "知识库权限管理相关接口")
public class CollectionPermissionController {

    private final CollectionPermissionService collectionPermissionService;

    @GetMapping
    @Operation(summary = "获取知识库权限配置", description = "获取指定知识库的权限配置详情，需要所有者或管理员权限")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<CollectionPermissionDTO>> getCollectionPermission(
            @Parameter(description = "知识库ID") @PathVariable String collectionId,
            @AuthenticationPrincipal UserDetails userDetails) {
        // 权限校验：只有所有者或管理员可查看权限配置详情（包含用户列表等敏感信息）
        String userId = TenantContext.getUserId();
        com.clacjz.medical.rag.model.entity.User operator = collectionPermissionService.getOperatorWithCheck(userId);
        com.clacjz.medical.rag.model.entity.Collection collection = collectionPermissionService.getCollectionWithCheck(collectionId);
        if (!collectionPermissionService.canModifyCollectionPermission(operator, collection)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "无权查看此知识库权限配置");
        }

        CollectionPermissionDTO permission = collectionPermissionService.getCollectionPermission(collectionId);
        return ResponseEntity.ok(Result.success(permission));
    }

    @PutMapping
    @Operation(summary = "更新知识库权限配置", description = "更新指定知识库的权限配置，所有者或管理员可操作")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<CollectionPermissionDTO>> updateCollectionPermission(
            @Parameter(description = "知识库ID") @PathVariable String collectionId,
            @Valid @RequestBody UpdateCollectionPermissionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = getUsernameSafely(userDetails);
        log.info("Update collection permission: collectionId={}, operator={}", collectionId, username);
        CollectionPermissionDTO permission = collectionPermissionService.updateCollectionPermission(collectionId, request);
        return ResponseEntity.ok(Result.success("知识库权限更新成功", permission));
    }

    @PostMapping("/users")
    @Operation(summary = "添加知识库用户权限", description = "为知识库添加指定用户的访问权限")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<Void>> addUserPermission(
            @Parameter(description = "知识库ID") @PathVariable String collectionId,
            @Valid @RequestBody AddCollectionUserPermissionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = getUsernameSafely(userDetails);
        log.info("Add user permission to collection: collectionId={}, userId={}, operator={}",
                collectionId, request.getUserId(), username);
        collectionPermissionService.addUserPermission(collectionId, request);
        return ResponseEntity.ok(Result.success("用户权限添加成功", null));
    }

    @DeleteMapping("/users/{userId}")
    @Operation(summary = "移除知识库用户权限", description = "移除知识库指定用户的访问权限")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<Void>> removeUserPermission(
            @Parameter(description = "知识库ID") @PathVariable String collectionId,
            @Parameter(description = "用户ID") @PathVariable String userId,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = getUsernameSafely(userDetails);
        log.info("Remove user permission from collection: collectionId={}, userId={}, operator={}",
                collectionId, userId, username);
        collectionPermissionService.removeUserPermission(collectionId, userId);
        return ResponseEntity.ok(Result.success("用户权限移除成功", null));
    }

    @GetMapping("/check")
    @Operation(summary = "检查当前用户权限", description = "检查当前登录用户对指定知识库的访问权限")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<CollectionPermissionService.CollectionAccessCheckResult>> checkAccess(
            @Parameter(description = "知识库ID") @PathVariable String collectionId) {
        String userId = TenantContext.getUserId();
        CollectionPermissionService.CollectionAccessCheckResult result = collectionPermissionService.checkCollectionAccess(userId, collectionId);
        return ResponseEntity.ok(Result.success(result));
    }

    private String getUsernameSafely(UserDetails userDetails) {
        return Optional.ofNullable(userDetails)
                .map(UserDetails::getUsername)
                .orElseThrow(() -> new BizException(ErrorCode.AUTHENTICATION_FAILED,
                        "用户信息不存在，请重新登录"));
    }
}
