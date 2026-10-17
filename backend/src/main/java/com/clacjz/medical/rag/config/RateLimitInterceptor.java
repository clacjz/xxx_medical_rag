package com.clacjz.medical.rag.config;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API 限流拦截器
 * 对 /api/** 和 /auth/** 路径进行限流检查
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();

        // 仅对 API 路径进行限流（包含context-path前缀 /api/v1/... 和无前缀 /auth/...）
        if (path.startsWith("/api/") || path.startsWith("/auth/")) {
            boolean allowed;
            // 认证接口更严格的限流：匹配 /api/v1/auth/login, /api/v1/auth/refresh, /auth/login, /auth/refresh
            if (path.startsWith("/api/v1/auth/login") || path.startsWith("/api/v1/auth/refresh")
                    || path.startsWith("/auth/login") || path.startsWith("/auth/refresh")
                    || path.startsWith("/api/v1/auth/register") || path.startsWith("/auth/register")
                    || path.startsWith("/api/v1/auth/password") || path.startsWith("/auth/password")) {
                allowed = rateLimitService.allowAuthRequest();
            } else {
                allowed = rateLimitService.allowApiRequest();
            }

            if (!allowed) {
                log.warn("Rate limit exceeded: path={}, ip={}", path, getClientIp(request));
                throw new BizException(ErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
            }
        }

        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}