package com.clacjz.medical.rag.model.dto;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OCR结果保存请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveOcrMarkdownRequest {

    @NotBlank(message = "知识库ID不能为空")
    @Parameter(description = "知识库ID")
    private String collectionId;

    @Parameter(description = "文档ID（为空时由后端自动生成）")
    private String documentId;

    @NotBlank(message = "文档标题不能为空")
    @Size(max = 500, message = "文档标题不能超过500字符")
    @Parameter(description = "文档标题")
    private String title;

    @NotBlank(message = "Markdown内容不能为空")
    @Parameter(description = "Markdown文本内容")
    private String markdownContent;

    @Parameter(description = "分块策略: fixed/paragraph")
    @Builder.Default
    private String strategy = "fixed";

    @Parameter(description = "是否为更新操作（true=删除旧数据重新存储，false=新增）")
    @Builder.Default
    private Boolean updateContent = false;

    @Parameter(description = "权限类型: COMPANY_WIDE/DEPARTMENT_WIDE/RESTRICTED")
    private String permissionType;

    @Parameter(description = "指定可访问的用户ID列表，当permissionType为RESTRICTED时必填")
    private java.util.List<String> allowedUserIds;
}
