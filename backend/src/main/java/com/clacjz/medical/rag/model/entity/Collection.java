package com.clacjz.medical.rag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.model.config.CollectionConfig;
import com.clacjz.medical.rag.model.config.SectionTemplate;
import com.clacjz.medical.rag.model.enums.CollectionStatus;
import com.clacjz.medical.rag.model.enums.CollectionType;
import com.clacjz.medical.rag.model.enums.DocType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@TableName(value = "collections", autoResultMap = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Collection {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("type")
    private CollectionType type;

    /**
     * 文档类型：MANUAL（说明书类结构化短文档）/ NORMAL（普通文档）
     * 决定文档的处理策略
     */
    @TableField("doc_type")
    @Builder.Default
    private DocType docType = DocType.NORMAL;

    /**
     * 字段模板配置（仅MANUAL类型文档使用）
     * 定义要从文档中提取的结构化字段
     */
    @TableField("section_template")
    private String sectionTemplateJson;

    @TableField("config")
    private String configJson;

    @TableField("status")
    @Builder.Default
    private CollectionStatus status = CollectionStatus.ACTIVE;

    @TableField("document_count")
    @Builder.Default
    private Integer documentCount = 0;

    @TableField("chunk_count")
    @Builder.Default
    private Long chunkCount = 0L;

    @TableField("total_size")
    @Builder.Default
    private Long totalSize = 0L;

    // 保留字段名department_id，但支持多部门逗号分隔存储
    @TableField("department_id")
    private String departmentId;

    @TableField(exist = false)
    private Department department;

    @TableField("owner_id")
    private String ownerId;

    @TableField(exist = false)
    private User owner;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    // 瞬时字段，不存储，用于API交互
    @TableField(exist = false)
    private CollectionConfig config;

    // 瞬时字段，不存储，用于API交互（section_template解析后）
    @TableField(exist = false)
    private List<SectionTemplate> sectionTemplate;

    public CollectionConfig getConfig() {
        if (config == null && configJson != null && !configJson.isBlank()) {
            try {
                config = objectMapper.readValue(configJson, CollectionConfig.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse config JSON: " + configJson, e);
            }
        }
        return config;
    }

    public void setConfig(CollectionConfig config) {
        this.config = config;
        if (config != null) {
            try {
                this.configJson = objectMapper.writeValueAsString(config);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize config to JSON", e);
            }
        } else {
            this.configJson = null;
        }
    }

    /**
     * 获取sectionTemplate列表
     */
    public List<SectionTemplate> getSectionTemplate() {
        if (sectionTemplate == null && sectionTemplateJson != null && !sectionTemplateJson.isBlank()) {
            try {
                sectionTemplate = Arrays.asList(objectMapper.readValue(sectionTemplateJson, SectionTemplate[].class));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse sectionTemplate JSON: " + sectionTemplateJson, e);
            }
        }
        return sectionTemplate;
    }

    /**
     * 设置sectionTemplate列表
     */
    public void setSectionTemplate(List<SectionTemplate> sectionTemplate) {
        this.sectionTemplate = sectionTemplate;
        if (sectionTemplate != null) {
            try {
                this.sectionTemplateJson = objectMapper.writeValueAsString(sectionTemplate);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize sectionTemplate to JSON", e);
            }
        } else {
            this.sectionTemplateJson = null;
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isActive() {
        return status == CollectionStatus.ACTIVE && deletedAt == null;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
        this.status = CollectionStatus.DELETED;
    }

    public void incrementDocumentCount() {
        this.documentCount = (this.documentCount == null ? 0 : this.documentCount) + 1;
    }

    public void decrementDocumentCount() {
        if (this.documentCount != null && this.documentCount > 0) {
            this.documentCount--;
        }
    }

    public void incrementChunkCount(long count) {
        this.chunkCount = (this.chunkCount == null ? 0L : this.chunkCount) + count;
    }

    public void decrementChunkCount(long count) {
        this.chunkCount = (this.chunkCount == null ? 0L : this.chunkCount) - count;
        if (this.chunkCount < 0) {
            this.chunkCount = 0L;
        }
    }

    public void addSize(long size) {
        this.totalSize = (this.totalSize == null ? 0L : this.totalSize) + size;
    }

    // 多部门支持方法

    /**
     * 获取部门ID列表
     */
    public List<String> getDepartmentIdList() {
        if (departmentId == null || departmentId.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.asList(departmentId.split(","));
    }

    /**
     * 设置部门ID列表
     */
    public void setDepartmentIdList(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            this.departmentId = null;
        } else {
            this.departmentId = String.join(",", ids);
        }
    }

    /**
     * 检查是否属于指定部门
     */
    public boolean belongsToDepartment(String deptId) {
        return getDepartmentIdList().contains(deptId);
    }
}
