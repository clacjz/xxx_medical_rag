package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.*;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.DepartmentConfigService;
import com.clacjz.medical.rag.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
@Tag(name = "Department Management API", description = "部门管理相关接口")
public class DepartmentController {

    private final DepartmentService departmentService;
    private final DepartmentConfigService departmentConfigService;

    @GetMapping
    @Operation(summary = "获取部门列表（树形结构）", description = "获取所有部门，以树形结构返回")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<List<DepartmentDTO>>> getDepartmentTree() {
        List<DepartmentDTO> departments = departmentService.getDepartmentTree();
        return ResponseEntity.ok(Result.success(departments));
    }

    @GetMapping("/default")
    @Operation(summary = "获取默认部门", description = "获取系统配置的默认部门")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<DepartmentDTO>> getDefaultDepartment() {
        DepartmentDTO defaultDept = departmentConfigService.getDefaultDepartment();
        if (defaultDept == null) {
            return ResponseEntity.ok(Result.success("系统未设置默认部门", null));
        }
        return ResponseEntity.ok(Result.success(defaultDept));
    }

    @PutMapping("/default")
    @Operation(summary = "设置默认部门", description = "将指定部门设置为系统默认部门")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<DepartmentDTO>> setDefaultDepartment(
            @Valid @RequestBody SetDefaultDepartmentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "DepartmentController.setDefaultDepartment");

        log.info("Set default department request: departmentId={}, operator={}",
                request.getDepartmentId(), username);

        DepartmentDTO department = departmentConfigService.setDefaultDepartment(
                request.getDepartmentId(), username,
                request.getAutoAssignNewUsers(), request.getAutoAssignUnassignedUsers());

        return ResponseEntity.ok(Result.success("默认部门设置成功", department));
    }

    @DeleteMapping("/default")
    @Operation(summary = "取消默认部门", description = "取消当前默认部门的默认属性")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<Void>> unsetDefaultDepartment(
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "DepartmentController.unsetDefaultDepartment");

        log.info("Unset default department request: operator={}", username);

        departmentConfigService.unsetDefaultDepartment(null, username);
        return ResponseEntity.ok(Result.success("默认部门已取消", null));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取部门详情", description = "根据ID获取部门详细信息")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<DepartmentDTO>> getDepartmentById(
            @Parameter(description = "部门ID") @PathVariable String id) {
        DepartmentDTO department = departmentService.getDepartmentById(id);
        return ResponseEntity.ok(Result.success(department));
    }

    @GetMapping("/{id}/delete-check")
    @Operation(summary = "检查部门是否可以删除", description = "检查部门关联的文档、用户、子部门等，返回能否删除及原因")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<DepartmentService.DepartmentDeleteCheckResult>> checkDepartmentDelete(
            @Parameter(description = "部门ID") @PathVariable String id) {
        DepartmentService.DepartmentDeleteCheckResult result = departmentService.checkDepartmentDelete(id);
        return ResponseEntity.ok(Result.success(result));
    }

    @PostMapping
    @Operation(summary = "创建部门", description = "创建新部门，仅系统管理员可操作")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<DepartmentDTO>> createDepartment(
            @Valid @RequestBody CreateDepartmentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "DepartmentController.createDepartment");

        log.info("Create department request: code={}, name={}, operator={}",
                request.getCode(), request.getName(), username);
        DepartmentDTO department = departmentService.createDepartment(request, username);
        return ResponseEntity.ok(Result.success("部门创建成功", department));
    }

    @PostMapping("/{id}")
    @Operation(summary = "更新部门", description = "更新部门信息，系统管理员可更新任意部门")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<DepartmentDTO>> updateDepartment(
            @Parameter(description = "部门ID") @PathVariable String id,
            @Valid @RequestBody UpdateDepartmentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "DepartmentController.updateDepartment");

        log.info("Update department request: id={}, operator={}", id, username);
        DepartmentDTO department = departmentService.updateDepartment(id, request, username);
        return ResponseEntity.ok(Result.success("部门更新成功", department));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除部门", description = "删除部门，系统管理员可操作，需确保部门下无用户和子部门")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<Void>> deleteDepartment(
            @Parameter(description = "部门ID") @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "DepartmentController.deleteDepartment");

        log.info("Delete department request: id={}, operator={}", id, username);
        departmentService.deleteDepartment(id, username);
        return ResponseEntity.ok(Result.success("部门删除成功", null));
    }

    @GetMapping("/{id}/users")
    @Operation(summary = "获取部门用户列表", description = "获取指定部门下的所有用户")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<List<UserDepartmentDTO>>> getDepartmentUsers(
            @Parameter(description = "部门ID") @PathVariable String id) {
        List<UserDepartmentDTO> users = departmentService.getDepartmentUsers(id);
        return ResponseEntity.ok(Result.success(users));
    }

    @PostMapping("/{id}/users")
    @Operation(summary = "添加用户到部门", description = "将用户添加到指定部门")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<Void>> addUserToDepartment(
            @Parameter(description = "部门ID") @PathVariable String id,
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = (String) request.get("userId");
        Boolean isPrimary = request.containsKey("isPrimary") ? (Boolean) request.get("isPrimary") : false;
        String position = (String) request.get("position");

        String username = getUsernameSafely(userDetails, "DepartmentController.addUserToDepartment");

        // DEPT_ADMIN只能管理自己所属部门
        checkDeptAdminScope(id, userDetails);

        log.info("Add user to department: departmentId={}, userId={}, operator={}",
                id, userId, username);
        departmentService.addUserToDepartment(userId, id, isPrimary, position, username);
        return ResponseEntity.ok(Result.success("用户已添加到部门", null));
    }

    @DeleteMapping("/{id}/users/{userId}")
    @Operation(summary = "从部门移除用户", description = "将用户从指定部门移除")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<Void>> removeUserFromDepartment(
            @Parameter(description = "部门ID") @PathVariable String id,
            @Parameter(description = "用户ID") @PathVariable String userId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "DepartmentController.removeUserFromDepartment");

        // DEPT_ADMIN只能管理自己所属部门
        checkDeptAdminScope(id, userDetails);

        log.info("Remove user from department: departmentId={}, userId={}, operator={}",
                id, userId, username);
        departmentService.removeUserFromDepartment(userId, id, username);
        return ResponseEntity.ok(Result.success("用户已从部门移除", null));
    }

    @PostMapping("/{id}/users/batch")
    @Operation(summary = "批量添加用户到部门", description = "将多个用户批量添加到指定部门")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN')")
    public ResponseEntity<Result<Map<String, Object>>> batchAssignUsersToDepartment(
            @Parameter(description = "部门ID") @PathVariable String id,
            @Valid @RequestBody BatchAssignUserRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "DepartmentController.batchAssignUsersToDepartment");

        log.info("Batch assign users to department: departmentId={}, userCount={}, operator={}",
                id, request.getUserIds().size(), username);

        Map<String, Object> result = departmentConfigService.batchAssignUsersToDepartment(
                id, request, username);

        return ResponseEntity.ok(Result.success("批量分配完成", result));
    }

    @GetMapping("/unassigned-users")
    @Operation(summary = "获取未分配部门的用户列表", description = "获取所有未分配到任何部门的用户")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<List<UserDTO>>> getUnassignedUsers() {
        List<UserDTO> users = departmentConfigService.getUnassignedUsers();
        return ResponseEntity.ok(Result.success(users));
    }

    @PostMapping("/assign-unassigned")
    @Operation(summary = "分配未分配用户到默认部门", description = "将所有未分配部门的用户自动分配到默认部门")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<Map<String, Integer>>> assignUnassignedUsersToDefault(
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "DepartmentController.assignUnassignedUsersToDefault");

        log.info("Assign unassigned users to default department: operator={}", username);

        int count = departmentConfigService.assignUnassignedUsersToDefault(username);

        Map<String, Integer> result = Map.of("assignedCount", count);
        return ResponseEntity.ok(Result.success("已分配" + count + "个用户到默认部门", result));
    }

    @GetMapping("/config-logs")
    @Operation(summary = "获取部门配置变更日志", description = "分页查询部门配置变更日志")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<IPage<DepartmentConfigLogDTO>>> getConfigLogs(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "操作人ID") @RequestParam(required = false) String operatorId,
            @Parameter(description = "目标类型") @RequestParam(required = false) String targetType,
            @Parameter(description = "目标ID") @RequestParam(required = false) String targetId,
            @Parameter(description = "操作类型") @RequestParam(required = false) String operationType) {

        IPage<DepartmentConfigLogDTO> logs = departmentConfigService.getConfigLogs(
                page, pageSize, operatorId, targetType, targetId, operationType, null, null);

        return ResponseEntity.ok(Result.success(logs));
    }

    @GetMapping("/{id}/config-logs")
    @Operation(summary = "获取部门的配置变更日志", description = "获取指定部门的所有配置变更历史")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<List<DepartmentConfigLogDTO>>> getConfigLogsByDepartment(
            @Parameter(description = "部门ID") @PathVariable String id) {

        List<DepartmentConfigLogDTO> logs = departmentConfigService.getConfigLogsByTarget(
                "DEPARTMENT", id);

        return ResponseEntity.ok(Result.success(logs));
    }

    private String getUsernameSafely(UserDetails userDetails, String location) {
        return Optional.ofNullable(userDetails)
                .map(UserDetails::getUsername)
                .orElseThrow(() -> new BizException(ErrorCode.AUTHENTICATION_FAILED,
                        String.format("[%s] 用户信息不存在，请重新登录", location)));
    }

    /**
     * DEPT_ADMIN只能操作自己所属部门，SYSTEM_ADMIN不受限制
     */
    private void checkDeptAdminScope(String targetDepartmentId, UserDetails userDetails) {
        boolean isSystemAdmin = userDetails.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_SYSTEM_ADMIN"));
        if (isSystemAdmin) {
            return;
        }
        // 从请求属性获取userId（由JwtAuthenticationFilter设置）
        // DEPT_ADMIN必须属于目标部门才能操作
        String operatorUserId = null;
        org.springframework.web.context.request.ServletRequestAttributes attrs =
                (org.springframework.web.context.request.ServletRequestAttributes)
                        org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            operatorUserId = (String) attrs.getRequest().getAttribute("userId");
        }
        if (operatorUserId == null || !departmentService.canManageDepartment(operatorUserId, targetDepartmentId)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "只能管理自己所属部门");
        }
    }
}