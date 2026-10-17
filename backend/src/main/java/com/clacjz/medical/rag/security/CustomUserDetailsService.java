package com.clacjz.medical.rag.security;

import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.service.PermissionCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;
    private final PermissionCacheService permissionCacheService;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        // 加载完整权限集：包含ROLE_角色标识 + 细粒度权限（如collection:read等）
        Set<SimpleGrantedAuthority> authorities = permissionCacheService.getUserPermissions(user.getId())
                .stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

        // 确保至少有一个ROLE_权限（兜底：PermissionCacheService可能缓存未命中时返回空）
        if (authorities.stream().noneMatch(a -> a.getAuthority().startsWith("ROLE_"))) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getSafeRoleName()));
        }

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash(),
                user.getStatus() != null && "ACTIVE".equals(user.getStatus().name()),
                true,
                true,
                !user.isLocked(),
                authorities
        );
    }

    @Transactional(readOnly = true)
    public User loadUserEntityByUsername(String username) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        return user;
    }
}
