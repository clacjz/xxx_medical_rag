package com.clacjz.medical.rag.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt)) {
                // 解析JWT一次，复用Claims对象，避免5次重复的HMAC-SHA256签名验证
                Claims claims;
                try {
                    claims = jwtTokenProvider.parseAndValidateToken(jwt);
                } catch (ExpiredJwtException e) {
                    throw e; // 向上传播，由外层catch处理
                } catch (JwtException | IllegalArgumentException e) {
                    sendUnauthorizedResponse(response, ErrorCode.INVALID_TOKEN, "Token无效或已过期");
                    return;
                }

                // 验证token类型
                String tokenType = claims.get("type", String.class);
                if (!"access".equals(tokenType)) {
                    sendUnauthorizedResponse(response, ErrorCode.INVALID_TOKEN, "无效的Token类型");
                    return;
                }

                // 检查黑名单
                String jti = claims.get("jti", String.class);
                if (tokenBlacklistService.isAccessTokenBlacklisted(jti)) {
                    log.warn("Blacklisted access token used: jti={}", jti);
                    sendUnauthorizedResponse(response, ErrorCode.INVALID_TOKEN, "Token已被吊销，请重新登录");
                    return;
                }

                String username = claims.get("username", String.class);
                String userId = claims.getSubject();

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // 检查账户状态：禁用或锁定的用户不应通过JWT认证
                if (!userDetails.isEnabled()) {
                    log.warn("JWT authentication rejected: user {} is disabled", username);
                    sendUnauthorizedResponse(response, ErrorCode.ACCOUNT_DISABLED, "账户已被禁用");
                    return;
                }
                if (!userDetails.isAccountNonLocked()) {
                    log.warn("JWT authentication rejected: user {} is locked", username);
                    sendUnauthorizedResponse(response, ErrorCode.ACCOUNT_LOCKED, "账户已被锁定");
                    return;
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // 设置userId请求属性，供Controller的@RequestAttribute使用
                request.setAttribute("userId", userId);

                log.debug("Authentication set for user: {}, authorities: {}", username, userDetails.getAuthorities());
            }
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired");
            sendUnauthorizedResponse(response, ErrorCode.TOKEN_EXPIRED, "Token已过期，请重新登录");
            return;
        } catch (UsernameNotFoundException e) {
            log.warn("User not found during JWT authentication");
            sendUnauthorizedResponse(response, ErrorCode.USER_NOT_FOUND, "用户不存在");
            return;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Invalid JWT token");
            sendUnauthorizedResponse(response, ErrorCode.INVALID_TOKEN, "Token无效");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Result<Void> result = Result.<Void>builder()
                .code(errorCode.getCode())
                .message(message)
                .timestamp(java.time.LocalDateTime.now())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(result));
        response.getWriter().flush();
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        // 优先从 Header 获取
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        // 兜底：从 query 参数获取（仅限 SSE 等无法设置 header 的场景，避免 token 通过 URL 泄漏到日志/Referer）
        // 使用精确路径匹配，避免contains()匹配到非SSE端点（如/documents/assessment）
        String requestURI = request.getRequestURI();
        if (requestURI != null && (requestURI.endsWith("/chat/stream") || requestURI.endsWith("/chat/sse")
                || requestURI.endsWith("/sse") || requestURI.endsWith("/event-stream"))) {
            String token = request.getParameter("token");
            if (StringUtils.hasText(token)) {
                return token;
            }
        }
        return null;
    }
}
