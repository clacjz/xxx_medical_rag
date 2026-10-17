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

@TableName("collection_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionDocument {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("collection_id")
    private String collectionId;

    @TableField(exist = false)
    private Collection collection;

    @TableField("document_id")
    private String documentId;

    @TableField(exist = false)
    private Document document;

    @TableField("department_id")
    private String departmentId;

    @TableField(exist = false)
    private Department department;

    @TableField("added_by")
    private String addedBy;

    @TableField(exist = false)
    private User addedByUser;

    @TableField("processing_status")
    @Builder.Default
    private String processingStatus = "PENDING";

    @TableField("processing_priority")
    @Builder.Default
    private Integer processingPriority = 0;

    @TableField("processing_error")
    private String processingError;

    @TableField("processed_at")
    private LocalDateTime processedAt;

    @TableField("added_at")
    private LocalDateTime addedAt;

    public boolean isPending() {
        return "PENDING".equals(processingStatus);
    }

    public boolean isProcessing() {
        return "PROCESSING".equals(processingStatus);
    }

    public boolean isCompleted() {
        return "COMPLETED".equals(processingStatus);
    }

    public boolean isFailed() {
        return "FAILED".equals(processingStatus);
    }

    public void markProcessing() {
        this.processingStatus = "PROCESSING";
    }

    public void markCompleted() {
        this.processingStatus = "COMPLETED";
        this.processedAt = LocalDateTime.now();
        this.processingError = null;
    }

    public void markFailed(String error) {
        this.processingStatus = "FAILED";
        this.processedAt = LocalDateTime.now();
        this.processingError = error;
    }
}