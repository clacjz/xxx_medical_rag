package com.clacjz.medical.rag.model.dto;

import com.clacjz.medical.rag.model.config.CollectionConfig;
import com.clacjz.medical.rag.model.config.SectionTemplate;
import com.clacjz.medical.rag.model.enums.CollectionType;
import com.clacjz.medical.rag.model.enums.DocType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCollectionRequest {

    @NotBlank(message = "Collection名称不能为空")
    @Size(min = 1, max = 128, message = "Collection名称长度必须在1-128之间")
    private String name;

    @Size(max = 1024, message = "描述长度不能超过1024")
    private String description;

    @Builder.Default
    private CollectionType type = CollectionType.KNOWLEDGE_BASE;

    /**
     * 文档处理策略：MANUAL（说明书类）/ NORMAL（普通文档）
     * MANUAL：强制不切片，整篇存储，必须配置 sectionTemplate
     * NORMAL：使用语义分块
     */
    @Builder.Default
    private DocType docType = DocType.NORMAL;

    /**
     * 字段模板配置（仅MANUAL类型文档使用）
     * 定义要从文档中提取的结构化字段
     */
    private List<SectionTemplate> sectionTemplate;

    /**
     * JSON格式的字段模板字符串（仅MANUAL类型文档使用）
     * 支持直接粘贴JSON数组格式的模板，与 sectionTemplate 二选一
     * 格式：[{"field": "indications", "label": "适应症", "required": true}, ...]
     */
    private String jsonTemplate;

    private CollectionConfig config;

    private String departmentId;

    /**
     * 权限类型：PUBLIC / DEPARTMENT_WIDE / RESTRICTED / DEPARTMENT_WIDE_RESTRICTED
     */
    private String permissionType;

    /**
     * 指定可访问的用户ID列表（permissionType为RESTRICTED或DEPARTMENT_WIDE_RESTRICTED时有效）
     */
    private List<String> allowedUserIds;
}
