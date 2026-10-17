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

@TableName("document_chunks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("document_id")
    private String documentId;

    @TableField("point_id")
    private String pointId;

    @TableField("chunk_index")
    private Integer chunkIndex;

    @TableField("content")
    private String content;

    @TableField("chunk_size")
    private Integer chunkSize;

    @TableField("start_pos")
    private Integer startPos;

    @TableField("end_pos")
    private Integer endPos;

    @TableField("start_page")
    @Builder.Default
    private Integer startPage = 0;

    @TableField("end_page")
    @Builder.Default
    private Integer endPage = 0;

    /**
     * 元数据JSON字符串（存储到JSONB列）
     */
    @TableField("metadata")
    private String metadata;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}