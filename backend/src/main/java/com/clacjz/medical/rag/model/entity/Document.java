package com.clacjz.medical.rag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.model.enums.DocType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@TableName("documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("title")
    private String title;

    @TableField("description")
    private String description;

    @TableField("file_path")
    private String filePath;

    @TableField("file_name")
    private String fileName;

    @TableField("file_size")
    private Long fileSize;

    @TableField("file_type")
    private String fileType;

    /**
     * @deprecated 多对多关系不再使用此字段保留单个归属。
     *             文档与知识库的关联完全通过 collection_documents 表管理。
     *             此字段可能为 null，尤其是新上传的文档。
     *             已有数据可能仍存有值，仅作历史兼容。
     */
    @TableField("collection_id")
    private String collectionId;

    @TableField("owner_id")
    private String ownerId;

    @TableField(exist = false)
    private User owner;

    // 多部门支持
    @TableField("department_id")
    private String departmentId;  // 保留字段名但支持多部门逗号分隔

    @TableField(exist = false)
    private Department department;


    @TableField("upload_status")
    @Builder.Default
    private String uploadStatus = "pending";

    @TableField("parse_status")
    @Builder.Default
    private String parseStatus = "pending";

    @TableField("chunk_count")
    @Builder.Default
    private Integer chunkCount = 0;

    /**
     * 解析阶段预期的分块数量（parse成功后设置，与实际存储数量可能不同）
     */
    @TableField("expected_chunk_count")
    @Builder.Default
    private Integer expectedChunkCount = 0;

    @TableField("vector_ids")
    private String vectorIds;

    @TableField("chunk_status")
    @Builder.Default
    private String chunkStatus = "pending";

    @TableField("parse_error_message")
    private String parseErrorMessage;

    @TableField("parse_char_count")
    private Integer parseCharCount;

    @TableField("parse_page_count")
    private Integer parsePageCount;

    @TableField("process_result_file")
    private String processResultFile;

    @TableField("last_parse_at")
    private LocalDateTime lastParseAt;

    @TableField("status")
    @Builder.Default
    private String status = "active";

    /**
     * 软删除标记：true=已删除，false=未删除
     */
    @TableField("is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    /**
     * 软删除时间
     */
    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 文档类型：MANUAL（说明书类）/ NORMAL（普通文档）
     * 可继承自collection
     */
    @TableField("doc_type")
    @Builder.Default
    private DocType docType = DocType.NORMAL;

    /**
     * 结构化字段（仅MANUAL类型文档，如药品说明书的国标字段）
     */
    @TableField("key_sections")
    private String keySectionsJson;

    /**
     * 页面标注（来自OCR，用于说明书快速导航）
     */
    @TableField("page_annotations")
    private String pageAnnotationsJson;

    // 瞬时字段，不存储
    @TableField(exist = false)
    private Map<String, Object> keySections;

    @TableField(exist = false)
    private List<PageAnnotation> pageAnnotations;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public boolean isOwner(String userId) {
        return this.ownerId != null && this.ownerId.equals(userId);
    }

    /**
     * 软删除文档（与 Collection.softDelete() 保持一致）
     */
    public void softDelete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
        this.status = "deleted";
    }

    public boolean isDeletedFlag() {
        return Boolean.TRUE.equals(this.isDeleted);
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

    /**
     * 获取keySections映射
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getKeySections() {
        if (keySections == null && keySectionsJson != null && !keySectionsJson.isBlank()) {
            try {
                keySections = objectMapper.readValue(keySectionsJson, Map.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse keySections JSON: " + keySectionsJson, e);
            }
        }
        return keySections;
    }

    /**
     * 设置keySections映射
     */
    public void setKeySections(Map<String, Object> keySections) {
        this.keySections = keySections;
        if (keySections != null) {
            try {
                this.keySectionsJson = objectMapper.writeValueAsString(keySections);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize keySections to JSON", e);
            }
        } else {
            this.keySectionsJson = null;
        }
    }

    /**
     * 获取pageAnnotations列表
     */
    public List<PageAnnotation> getPageAnnotations() {
        if (pageAnnotations == null && pageAnnotationsJson != null && !pageAnnotationsJson.isBlank()) {
            try {
                pageAnnotations = Arrays.asList(objectMapper.readValue(pageAnnotationsJson, PageAnnotation[].class));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse pageAnnotations JSON: " + pageAnnotationsJson, e);
            }
        }
        return pageAnnotations;
    }

    /**
     * 设置pageAnnotations列表
     */
    public void setPageAnnotations(List<PageAnnotation> pageAnnotations) {
        this.pageAnnotations = pageAnnotations;
        if (pageAnnotations != null) {
            try {
                this.pageAnnotationsJson = objectMapper.writeValueAsString(pageAnnotations);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize pageAnnotations to JSON", e);
            }
        } else {
            this.pageAnnotationsJson = null;
        }
    }

    /**
     * 页面标注类，用于说明书快速导航
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageAnnotation {
        private Integer page;
        private Long startPos;
        private Long endPos;
        private String preview;
    }
}