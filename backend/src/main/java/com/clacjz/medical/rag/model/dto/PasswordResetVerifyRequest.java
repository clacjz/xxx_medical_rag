package com.clacjz.medical.rag.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

/**
 * 密码重置验证请求DTO。
 * 使用@RequestBody替代@RequestParam，避免新密码暴露在URL查询参数中
 * （URL会被记录在服务器访问日志、浏览器历史和代理日志中）。
 */
@Data
@Builder
public class PasswordResetVerifyRequest {

    @NotBlank(message = "邮箱不能为空")
    private String email;

    @NotBlank(message = "验证码不能为空")
    private String code;

    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
