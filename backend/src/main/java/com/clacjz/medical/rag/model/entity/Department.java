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
import java.util.ArrayList;
import java.util.List;

@TableName("departments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Department {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("code")
    private String code;

    @TableField("name")
    private String name;

    @TableField("parent_id")
    private String parentId;

    @TableField(exist = false)
    private Department parent;

    @TableField(exist = false)
    @Builder.Default
    private List<Department> children = new ArrayList<>();

    @TableField("manager_id")
    private String managerId;

    @TableField(exist = false)
    private User manager;

    @TableField("description")
    private String description;

    @TableField("status")
    @Builder.Default
    private String status = "active";

    @TableField("is_default")
    @Builder.Default
    private Boolean isDefault = false;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public boolean isActive() {
        return "active".equals(status);
    }
}
