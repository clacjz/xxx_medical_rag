package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.UpdateUserRequest;
import com.clacjz.medical.rag.model.dto.UserDTO;
import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import com.clacjz.medical.rag.model.enums.UserStatusEnums;
import com.clacjz.medical.rag.model.vo.PageResult;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.RoleService;
import com.clacjz.medical.rag.service.UserService;
import com.clacjz.medical.rag.util.RsaCryptoUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "User Management API", description = "用户管理相关接口")
public class UserController {

    private final UserService userService;
    private final RoleService roleService;
    private final RsaCryptoUtil rsaCryptoUtil;

    @GetMapping
    @Operation(summary = "获取用户列表", description = "分页获取用户列表，支持按角色和状态筛选")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<PageResult<UserDTO>>> getUserList(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "角色筛选") @RequestParam(required = false) UserRoleEnum role,
            @Parameter(description = "状态筛选") @RequestParam(required = false) UserStatusEnums status) {

        log.info("Get user list request: page={}, pageSize={}, role={}, status={}",
                page, pageSize, role, status);

        IPage<UserDTO> userPage = userService.getUserList(page, pageSize, role, status);
        PageResult<UserDTO> result = PageResult.from(userPage, dto -> dto);
        return ResponseEntity.ok(Result.success(result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取用户详情", description = "根据ID获取用户详细信息")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DEPT_ADMIN') or @userService.isSelf(#id, authentication.principal.username)")
    public ResponseEntity<Result<UserDTO>> getUserById(
            @Parameter(description = "用户ID") @PathVariable String id) {

        log.info("Get user by id: {}", id);
        UserDTO user = userService.getUserById(id);
        return ResponseEntity.ok(Result.success(user));
    }

    @PostMapping
    @Operation(summary = "创建用户", description = "创建新用户，密码支持RSA加密传输")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<UserDTO>> createUser(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        String email = request.get("email");
        String roleStr = request.get("role");

        // 输入校验：防止null值导致NPE
        if (username == null || username.isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "用户名不能为空");
        }
        if (password == null || password.isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "密码不能为空");
        }
        if (email == null || email.isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "邮箱不能为空");
        }

        if (rsaCryptoUtil.isEncrypted(password)) {
            try {
                password = rsaCryptoUtil.decrypt(password);
            } catch (Exception e) {
                log.error("RSA decryption failed for user creation: {}. Rejecting request.", username);
                throw new BizException(ErrorCode.INVALID_PARAMETER, "密码解密失败，请重新获取加密公钥");
            }
        }

        log.info("Create user request: username={}", username);
        UserDTO user = userService.createUser(username, password, email, roleStr);
        return ResponseEntity.ok(Result.success("创建成功", user));
    }

    @PostMapping("/{userId}/role")
    @Operation(summary = "分配角色给用户", description = "为指定用户分配一个角色")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<Void>> assignRole(
            @Parameter(description = "用户ID") @PathVariable String userId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String roleId = request.get("roleId");
        String operator = getUsernameSafely(userDetails, "assignRole");

        log.info("Assign role to user: userId={}, roleId={}, operator={}", userId, roleId, operator);
        roleService.assignRoleToUser(userId, roleId, false);

        return ResponseEntity.ok(Result.success("角色分配成功", null));
    }

    @PostMapping("/{id}")
    @Operation(summary = "更新用户信息(POST)", description = "更新用户的显示名称、邮箱、角色或状态")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or @userService.isSelf(#id, authentication.principal.username)")
    public ResponseEntity<Result<UserDTO>> updateUser(
            @Parameter(description = "用户ID") @PathVariable String id,
            @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Update user request: id={}, request={}", id, request);
        UserRoleEnum operatorRole = getOperatorRole(userDetails);
        UserDTO user = userService.updateUser(id, request, operatorRole);
        return ResponseEntity.ok(Result.success("更新成功", user));
    }

    @PostMapping("/{id}/password")
    @Operation(summary = "更新用户密码", description = "更新用户密码，支持RSA加密传输")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or @userService.isSelf(#id, authentication.principal.username)")
    public ResponseEntity<Result<Void>> updatePassword(
            @Parameter(description = "用户ID") @PathVariable String id,
            @RequestBody Map<String, String> request) {

        String oldPassword = request.get("oldPassword");
        String newPassword = request.get("newPassword");

        // NPE防护：防止null值导致rsaCryptoUtil.isEncrypted(null) NPE
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "原密码不能为空");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "新密码不能为空");
        }

        if (rsaCryptoUtil.isEncrypted(oldPassword)) {
            try {
                oldPassword = rsaCryptoUtil.decrypt(oldPassword);
            } catch (Exception e) {
                log.error("RSA decryption failed for oldPassword of user: {}. Rejecting request.", id);
                throw new BizException(ErrorCode.INVALID_PARAMETER, "旧密码解密失败，请重新获取加密公钥");
            }
        }

        if (rsaCryptoUtil.isEncrypted(newPassword)) {
            try {
                newPassword = rsaCryptoUtil.decrypt(newPassword);
            } catch (Exception e) {
                log.error("RSA decryption failed for newPassword of user: {}. Rejecting request.", id);
                throw new BizException(ErrorCode.INVALID_PARAMETER, "新密码解密失败，请重新获取加密公钥");
            }
        }

        log.info("Password update request for user: {}", id);
        userService.updatePassword(id, oldPassword, newPassword);
        return ResponseEntity.ok(Result.success("密码更新成功", null));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户", description = "根据ID删除用户")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<Void>> deleteUser(
            @Parameter(description = "用户ID") @PathVariable String id) {

        log.info("Delete user request: id={}", id);
        userService.deleteUser(id);
        return ResponseEntity.ok(Result.success("删除成功", null));
    }

    @PutMapping("/{id}/departments")
    @Operation(summary = "分配用户部门", description = "为指定用户分配多个部门（替换现有分配）。DEPT_ADMIN只能管理本部门内的用户。")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasRole('DEPT_ADMIN')")
    public ResponseEntity<Result<Void>> assignDepartments(
            @Parameter(description = "用户ID") @PathVariable String id,
            @RequestBody java.util.List<String> departmentIds,
            @AuthenticationPrincipal UserDetails userDetails) {

        String operator = getUsernameSafely(userDetails, "assignDepartments");
        UserRoleEnum operatorRole = getOperatorRole(userDetails);

        // DEPT_ADMIN只能管理本部门内的用户和分配本部门范围内的部门
        if (operatorRole == UserRoleEnum.DEPT_ADMIN) {
            userService.validateDeptAdminDepartmentScope(operator, id, departmentIds);
        }

        log.info("Assign departments to user: userId={}, departmentIds={}, operator={}", id, departmentIds, operator);
        userService.assignDepartments(id, departmentIds);
        return ResponseEntity.ok(Result.success("部门分配成功", null));
    }

    private String getUsernameSafely(UserDetails userDetails, String location) {
        return Optional.ofNullable(userDetails)
                .map(UserDetails::getUsername)
                .orElseThrow(() -> new com.clacjz.medical.rag.exception.BizException(
                        com.clacjz.medical.rag.constant.ErrorCode.AUTHENTICATION_FAILED,
                        String.format("[%s] 用户信息不存在，请重新登录", location)));
    }

    private UserRoleEnum getOperatorRole(UserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .filter(auth -> auth.startsWith("ROLE_"))
                .map(auth -> auth.substring(5))
                .map(roleName -> {
                    try {
                        return UserRoleEnum.valueOf(roleName);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(role -> role != null)
                .min(java.util.Comparator.comparingInt(UserRoleEnum::getLevel))
                .orElse(UserRoleEnum.DEPT_USER);
    }
}
