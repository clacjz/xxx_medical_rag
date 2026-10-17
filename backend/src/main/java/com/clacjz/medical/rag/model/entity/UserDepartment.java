package com.clacjz.medical.rag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName("user_departments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDepartment {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("user_id")
    private String userId;

    @TableField(exist = false)
    private User user;

    @TableField("department_id")
    private String departmentId;

    @TableField(exist = false)
    private Department department;

    @TableField("is_primary")
    @Builder.Default
    private Boolean isPrimary = true;

    @TableField("position")
    private String position;

    @TableField("joined_at")
    private LocalDateTime joinedAt;
}