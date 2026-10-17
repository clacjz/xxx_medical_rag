package com.clacjz.medical.rag.model.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 文档字段模板配置
 * 用于MANUAL类型文档（如药品说明书）的结构化字段提取
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionTemplate implements Serializable {

    /**
     * 字段名（英文，用于程序处理）
     */
    private String field;

    /**
     * 字段标签（中文，用于显示）
     */
    private String label;

    /**
     * 是否必填
     */
    @Builder.Default
    private boolean required = false;

    /**
     * 字段描述/提示
     */
    private String description;

    /**
     * 默认值
     */
    private String defaultValue;

    /**
     * 字段类型：string, number, boolean, array
     */
    @Builder.Default
    private String type = "string";

    /**
     * 预设的药品说明书字段模板
     */
    public static SectionTemplate[] drugManualTemplate() {
        return new SectionTemplate[]{
                SectionTemplate.builder()
                        .field("indications")
                        .label("适应症")
                        .required(true)
                        .description("药品的适应症/功能主治")
                        .build(),
                SectionTemplate.builder()
                        .field("dosage")
                        .label("用法用量")
                        .required(true)
                        .description("药品的用法和用量")
                        .build(),
                SectionTemplate.builder()
                        .field("contraindications")
                        .label("禁忌")
                        .required(false)
                        .description("药品的禁忌症")
                        .build(),
                SectionTemplate.builder()
                        .field("warnings")
                        .label("注意事项")
                        .required(false)
                        .description("使用药品时的注意事项")
                        .build(),
                SectionTemplate.builder()
                        .field("adverse_reactions")
                        .label("不良反应")
                        .required(false)
                        .description("可能出现的不良反应")
                        .build(),
                SectionTemplate.builder()
                        .field("storage")
                        .label("贮藏条件")
                        .required(false)
                        .description("药品的贮藏条件")
                        .build(),
                SectionTemplate.builder()
                        .field("manufacturer")
                        .label("生产企业")
                        .required(false)
                        .description("生产企业名称")
                        .build(),
                SectionTemplate.builder()
                        .field("approval_number")
                        .label("批准文号")
                        .required(false)
                        .description("药品批准文号")
                        .build()
        };
    }
}