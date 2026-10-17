package com.clacjz.medical.rag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import com.clacjz.medical.rag.model.enums.UserStatusEnums;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@TableName("users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = "userRoles")
public class User {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("username")
    private String username;

    @TableField("email")
    private String email;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("display_name")
    private String displayName;

    @TableField("role")
    private UserRoleEnum role;

    @TableField("status")
    private UserStatusEnums status;

    @TableField("login_fail_count")
    @Builder.Default
    private Integer loginFailCount = 0;

    @TableField("lock_until")
    private LocalDateTime lockUntil;

    @TableField(exist = false)
    @Builder.Default
    private Set<UserRole> userRoles = new HashSet<>();

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public boolean isLocked() {
        return lockUntil != null && LocalDateTime.now().isBefore(lockUntil);
    }

    public boolean isNotActive() {
        return status != UserStatusEnums.ACTIVE;
    }

    public long getRemainingLockSeconds() {
        if (lockUntil == null) {
            return 0;
        }
        Duration remaining = Duration.between(LocalDateTime.now(), lockUntil);
        return Math.max(0, remaining.getSeconds());
    }

    public void incrementLoginFailCount() {
        this.loginFailCount++;
        if (this.loginFailCount >= 5) {
            this.lockUntil = LocalDateTime.now().plusMinutes(30);
        }
    }

    public void lock(Duration duration) {
        this.lockUntil = LocalDateTime.now().plus(duration);
        this.loginFailCount = 5;
    }

    public void resetLoginFailCount() {
        this.loginFailCount = 0;
        this.lockUntil = null;
    }

    public void addRole(Role role) {
        UserRole userRole = UserRole.builder()
                .user(this)
                .role(role)
                .isPrimary(this.userRoles.isEmpty())
                .build();
        this.userRoles.add(userRole);
    }

    public void removeRole(Role role) {
        this.userRoles.removeIf(ur -> ur.getRoleId().equals(role.getId()));
    }

    public UserRole getPrimaryRole() {
        return this.userRoles.stream()
                .filter(UserRole::getIsPrimary)
                .findFirst()
                .orElse(null);
    }

    public Set<Role> getRoles() {
        return this.userRoles.stream()
                .map(UserRole::getRole)
                .collect(Collectors.toSet());
    }

    public boolean hasRole(String roleCode) {
        return this.userRoles.stream()
                .anyMatch(ur -> ur.getRole() != null && ur.getRole().getCode().equals(roleCode));
    }

    public boolean hasRoleLevel(int level) {
        return this.userRoles.stream()
                .anyMatch(ur -> ur.getRole() != null && ur.getRole().getLevel() <= level);
    }

    /**
     * 安全获取角色名称，role为null时返回默认值DEPT_USER。
     * 统一null-safe处理，避免各调用点分散的ternary遗漏导致NPE。
     */
    public String getSafeRoleName() {
        return role != null ? role.name() : UserRoleEnum.DEPT_USER.name();
    }

    /**
     * 安全获取状态名称，status为null时返回默认值ACTIVE。
     */
    public String getSafeStatusName() {
        return status != null ? status.name() : UserStatusEnums.ACTIVE.name();
    }
}
