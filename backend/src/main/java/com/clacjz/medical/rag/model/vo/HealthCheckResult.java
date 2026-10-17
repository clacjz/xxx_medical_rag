package com.clacjz.medical.rag.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthCheckResult {

    private String status;
    private Long totalResponseTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    private Map<String, ServiceHealth> services;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceHealth {
        private String status;
        private String message;
        private Long responseTime;
        private String errorType;
        private Instant errorTimestamp;
        private ServiceDetails details;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ServiceDetails {
            private String host;
            private Integer port;
            private String endpoint;
            private Boolean connectionAvailable;
            private String additionalInfo;
        }
    }
}
