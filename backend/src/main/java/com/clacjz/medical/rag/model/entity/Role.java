package com.clacjz.medical.rag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@TableName("roles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("code")
    private String code;

    @TableField("name")
    private String name;

    @TableField("level")
    private Integer level;

    @TableField("permissions")
    private String permissions;

    @TableField("description")
    private String description;

    @TableField("active")
    @Builder.Default
    private Boolean active = true;

    @TableField(exist = false)
    @Builder.Default
    @EqualsAndHashCode.Exclude
    @lombok.ToString.Exclude
    private Set<UserRole> userRoles = new HashSet<>();

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public boolean isSystemAdmin() {
        return "SYSTEM_ADMIN".equals(code);
    }

    public boolean isDeptAdmin() {
        return "DEPT_ADMIN".equals(code);
    }

    public boolean isDeptUploader() {
        return "DEPT_UPLOADER".equals(code);
    }

    public boolean isDeptUser() {
        return "DEPT_USER".equals(code);
    }

    public boolean hasHigherOrEqualLevel(Role other) {
        return this.level <= other.level;
    }
}
