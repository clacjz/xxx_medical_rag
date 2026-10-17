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

@TableName("sys_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("category")
    private String category;

    @TableField("config_key")
    private String configKey;

    @TableField("value")
    private String value;

    @TableField("value_type")
    private String valueType;

    @TableField("description")
    private String description;

    @TableField("default_value")
    private String defaultValue;

    @TableField("valid_values")
    private String validValues;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("updated_by")
    private String updatedBy;

    @TableField("created_at")
    private LocalDateTime createdAt;
}