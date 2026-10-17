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

@TableName("user_roles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRole {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("user_id")
    private String userId;

    @TableField(exist = false)
    @EqualsAndHashCode.Exclude
    @lombok.ToString.Exclude
    private User user;

    @TableField("role_id")
    private String roleId;

    @TableField(exist = false)
    @EqualsAndHashCode.Exclude
    @lombok.ToString.Exclude
    private Role role;

    @TableField("is_primary")
    @Builder.Default
    private Boolean isPrimary = false;

    @TableField("created_at")
    private LocalDateTime createdAt;
}