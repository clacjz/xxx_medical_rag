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

@TableName("department_config_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentConfigLog {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("operator_id")
    private String operatorId;

    @TableField(exist = false)
    private User operator;

    @TableField("target_type")
    private String targetType;

    @TableField("target_id")
    private String targetId;

    @TableField("operation_type")
    private String operationType;

    @TableField("before_value")
    private String beforeValue;

    @TableField("after_value")
    private String afterValue;

    @TableField("description")
    private String description;

    @TableField("created_at")
    private LocalDateTime createdAt;
}