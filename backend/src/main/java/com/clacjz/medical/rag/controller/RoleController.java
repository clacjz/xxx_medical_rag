package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.AssignRoleRequest;
import com.clacjz.medical.rag.model.dto.CreateRoleRequest;
import com.clacjz.medical.rag.model.dto.RoleDTO;
import com.clacjz.medical.rag.model.dto.RolePermissionDTO;
import com.clacjz.medical.rag.model.dto.UpdateRoleRequest;
import com.clacjz.medical.rag.model.dto.UpdateRolePermissionsRequest;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.RoleService;
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

import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@Tag(name = "Role Management API", description = "角色管理相关接口")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @Operation(summary = "获取所有角色", description = "获取所有角色列表，包含用户数统计")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<List<RoleDTO>>> getAllRoles() {
        List<RoleDTO> roles = roleService.getAllRoles();
        return ResponseEntity.ok(Result.success(roles));
    }

    @PostMapping("/init")
    @Operation(summary = "初始化默认角色", description = "初始化系统默认角色（SYSTEM_ADMIN, DEPT_ADMIN, DEPT_UPLOADER, DEPT_USER），仅在角色表为空时生效")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<String>> initializeDefaultRoles(
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = getUsernameSafely(userDetails);
        log.info("Initialize default roles request: operator={}", username);
        roleService.initializeDefaultRoles();
        return ResponseEntity.ok(Result.success("默认角色初始化完成", "角色初始化成功"));
    }

    @GetMapping("/active")
    @Operation(summary = "获取所有启用的角色", description = "获取所有启用的角色列表，按级别排序")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<List<RoleDTO>>> getAllActiveRoles() {
        List<RoleDTO> roles = roleService.getAllActiveRoles();
        return ResponseEntity.ok(Result.success(roles));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取角色详情", description = "根据ID获取角色详细信息")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<RoleDTO>> getRoleById(
            @Parameter(description = "角色ID") @PathVariable String id) {
        RoleDTO role = roleService.getRoleById(id);
        return ResponseEntity.ok(Result.success(role));
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "根据代码获取角色", description = "根据角色代码获取角色信息")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<RoleDTO>> getRoleByCode(
            @Parameter(description = "角色代码") @PathVariable String code) {
        RoleDTO role = roleService.getRoleByCode(code);
        return ResponseEntity.ok(Result.success(role));
    }

    @GetMapping("/level/{level}")
    @Operation(summary = "按级别获取角色", description = "获取指定级别的所有角色")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<List<RoleDTO>>> getRolesByLevel(
            @Parameter(description = "角色级别(1-4)") @PathVariable Integer level) {
        if (level < 1 || level > 4) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "角色级别必须在1-4之间");
        }
        List<RoleDTO> roles = roleService.getRolesByLevel(level);
        return ResponseEntity.ok(Result.success(roles));
    }

    @PostMapping
    @Operation(summary = "创建角色", description = "创建新角色，仅系统管理员可操作")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<RoleDTO>> createRole(
            @Valid @RequestBody CreateRoleRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = getUsernameSafely(userDetails);
        log.info("Create role request: code={}, name={}, operator={}",
                request.getCode(), request.getName(), username);
        RoleDTO role = roleService.createRole(request);
        return ResponseEntity.ok(Result.success("角色创建成功", role));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新角色", description = "更新角色信息，系统管理员可操作")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<RoleDTO>> updateRole(
            @Parameter(description = "角色ID") @PathVariable String id,
            @Valid @RequestBody UpdateRoleRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = getUsernameSafely(userDetails);
        log.info("Update role request: id={}, operator={}", id, username);
        RoleDTO role = roleService.updateRole(id, request);
        return ResponseEntity.ok(Result.success("角色更新成功", role));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除角色", description = "删除角色，系统管理员可操作，需确保角色下无用户")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<Void>> deleteRole(
            @Parameter(description = "角色ID") @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = getUsernameSafely(userDetails);
        log.info("Delete role request: id={}, operator={}", id, username);
        roleService.deleteRole(id);
        return ResponseEntity.ok(Result.success("角色删除成功", null));
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "获取用户的角色列表", description = "获取指定用户的所有角色")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<List<RoleDTO>>> getUserRoles(
            @Parameter(description = "用户ID") @PathVariable String userId) {
        List<RoleDTO> roles = roleService.getUserRoles(userId);
        return ResponseEntity.ok(Result.success(roles));
    }

    @PostMapping("/users/{userId}/assign")
    @Operation(summary = "分配角色给用户", description = "为用户分配角色，系统管理员可操作")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<Void>> assignRoleToUser(
            @Parameter(description = "用户ID") @PathVariable String userId,
            @Valid @RequestBody AssignRoleRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = getUsernameSafely(userDetails);
        log.info("Assign role to user: userId={}, roleId={}, operator={}",
                userId, request.getRoleId(), username);
        roleService.assignRoleToUser(userId, request);
        return ResponseEntity.ok(Result.success("角色分配成功", null));
    }

    @PostMapping("/users/{userId}/assign/{roleId}")
    @Operation(summary = "分配角色给用户（快捷方式）", description = "为用户分配角色，快速指定是否为首选角色")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<Void>> assignRoleToUserSimple(
            @Parameter(description = "用户ID") @PathVariable String userId,
            @Parameter(description = "角色ID") @PathVariable String roleId,
            @RequestParam(defaultValue = "false") boolean isPrimary,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = getUsernameSafely(userDetails);
        log.info("Assign role to user (simple): userId={}, roleId={}, isPrimary={}, operator={}",
                userId, roleId, isPrimary, username);
        roleService.assignRoleToUser(userId, roleId, isPrimary);
        return ResponseEntity.ok(Result.success("角色分配成功", null));
    }

    @DeleteMapping("/users/{userId}/remove/{roleId}")
    @Operation(summary = "移除用户角色", description = "从用户移除指定角色，系统管理员可操作")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<Void>> removeRoleFromUser(
            @Parameter(description = "用户ID") @PathVariable String userId,
            @Parameter(description = "角色ID") @PathVariable String roleId,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = getUsernameSafely(userDetails);
        log.info("Remove role from user: userId={}, roleId={}, operator={}",
                userId, roleId, username);
        roleService.removeRoleFromUser(userId, roleId);
        return ResponseEntity.ok(Result.success("角色移除成功", null));
    }

    @GetMapping("/permissions")
    @Operation(summary = "获取所有角色权限配置", description = "获取所有角色的权限配置列表")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<List<RolePermissionDTO>>> getAllRolePermissions() {
        List<RolePermissionDTO> permissions = roleService.getAllRolePermissions();
        return ResponseEntity.ok(Result.success(permissions));
    }

    @GetMapping("/{id}/permissions")
    @Operation(summary = "获取角色权限配置", description = "获取指定角色的权限配置详情")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<RolePermissionDTO>> getRolePermissions(
            @Parameter(description = "角色ID") @PathVariable String id) {
        RolePermissionDTO permission = roleService.getRolePermissions(id);
        return ResponseEntity.ok(Result.success(permission));
    }

    @PutMapping("/{id}/permissions")
    @Operation(summary = "更新角色权限配置", description = "更新指定角色的权限配置，系统管理员可操作")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<RolePermissionDTO>> updateRolePermissions(
            @Parameter(description = "角色ID") @PathVariable String id,
            @Valid @RequestBody UpdateRolePermissionsRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = getUsernameSafely(userDetails);
        log.info("Update role permissions: roleId={}, operator={}", id, username);
        RolePermissionDTO permission = roleService.updateRolePermissions(id, request.getPermissions());
        return ResponseEntity.ok(Result.success("角色权限更新成功", permission));
    }

    private String getUsernameSafely(UserDetails userDetails) {
        return Optional.ofNullable(userDetails)
                .map(UserDetails::getUsername)
                .orElseThrow(() -> new BizException(ErrorCode.AUTHENTICATION_FAILED,
                        "用户信息不存在，请重新登录"));
    }
}
