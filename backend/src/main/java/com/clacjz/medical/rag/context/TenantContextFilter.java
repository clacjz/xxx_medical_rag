package com.clacjz.medical.rag.context;

import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.entity.UserDepartment;
import com.clacjz.medical.rag.mapper.UserDepartmentMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class TenantContextFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);

    private final UserMapper userMapper;
    private final UserDepartmentMapper userDepartmentMapper;

    public TenantContextFilter(UserMapper userMapper, UserDepartmentMapper userDepartmentMapper) {
        this.userMapper = userMapper;
        this.userDepartmentMapper = userDepartmentMapper;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        if (log.isDebugEnabled()) {
            log.debug("TenantContextFilter initialized successfully");
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (shouldNotFilter(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            doFilterInternal(httpRequest, httpResponse, chain);
        } finally {
            TenantContext.clear();
        }
    }

    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            String username = authentication.getName();

            // 复用JwtAuthenticationFilter已设置的userId请求属性，
            // 避免重复调用userMapper.selectByUsername（JwtAuthenticationFilter已通过
            // CustomUserDetailsService.loadUserByUsername查询过用户）
            String userId = (String) request.getAttribute("userId");

            if (userId != null) {
                // 仅查询部门信息（用户信息已在JwtAuthenticationFilter中查询过）
                List<UserDepartment> userDepartments = userDepartmentMapper.selectByUserId(userId);
                String departmentId = userDepartments.stream()
                        .filter(ud -> Boolean.TRUE.equals(ud.getIsPrimary()))
                        .findFirst()
                        .map(UserDepartment::getDepartmentId)
                        .orElse(userDepartments.isEmpty() ? null : userDepartments.get(0).getDepartmentId());

                if (departmentId != null) {
                    TenantContext.setDepartmentId(departmentId);
                }
                TenantContext.setUserId(userId);

                // 从authentication的authorities中提取角色
                String roleName = authentication.getAuthorities().stream()
                        .filter(a -> a.getAuthority().startsWith("ROLE_"))
                        .map(a -> a.getAuthority().substring(5))
                        .findFirst()
                        .orElse("DEPT_USER");
                TenantContext.setUserRole(roleName);
            } else {
                // 兜底：无 userId 属性时仍从数据库查询（兼容非 JWT 认证场景）
                Optional<User> userOpt = Optional.ofNullable(userMapper.selectByUsername(username));
                userOpt.ifPresent(user -> {
                    List<UserDepartment> userDepartments = userDepartmentMapper.selectByUserId(user.getId());
                    String departmentId = userDepartments.stream()
                            .filter(ud -> Boolean.TRUE.equals(ud.getIsPrimary()))
                            .findFirst()
                            .map(UserDepartment::getDepartmentId)
                            .orElse(userDepartments.isEmpty() ? null : userDepartments.get(0).getDepartmentId());

                    if (departmentId != null) {
                        TenantContext.setDepartmentId(departmentId);
                    }
                    TenantContext.setUserId(user.getId());
                    TenantContext.setUserRole(user.getSafeRoleName());
                });
            }
        }
        chain.doFilter(request, response);
    }

    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/") || path.startsWith("/auth/") ||
               path.startsWith("/health") ||
               path.startsWith("/actuator/") || path.startsWith("/swagger-ui/");
    }

    @Override
    public void destroy() {
        if (log.isDebugEnabled()) {
            log.debug("TenantContextFilter destroyed");
        }
    }
}
