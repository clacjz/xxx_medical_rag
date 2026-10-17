package com.clacjz.medical.rag.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Token配置信息")
public class TokenConfigDTO {

    @Schema(description = "Access Token过期时间(毫秒)")
    private Long accessTokenExpiration;

    @Schema(description = "Refresh Token过期时间(毫秒)")
    private Long refreshTokenExpiration;

    public static TokenConfigDTO of(Long accessExpiration, Long refreshExpiration) {
        return TokenConfigDTO.builder()
                .accessTokenExpiration(accessExpiration)
                .refreshTokenExpiration(refreshExpiration)
                .build();
    }
}
