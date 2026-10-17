package com.clacjz.medical.rag.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

@Slf4j
public final class SecurityContextUtils {

    private SecurityContextUtils() {
    }

    public static Optional<String> getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.empty();
            }

            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails userDetails) {
                return Optional.ofNullable(userDetails.getUsername());
            } else if (principal instanceof String str) {
                return Optional.of(str);
            }

            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to get current username from security context", e);
            return Optional.empty();
        }
    }

    public static String getCurrentUsernameOrDefault(String defaultValue) {
        return getCurrentUsername().orElse(defaultValue);
    }

    public static Optional<UserDetails> getCurrentUserDetails() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.empty();
            }

            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails userDetails) {
                return Optional.of(userDetails);
            }

            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to get current UserDetails from security context", e);
            return Optional.empty();
        }
    }

    public static boolean isAuthenticated() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            // 排除匿名认证：AnonymousAuthenticationToken.isAuthenticated()返回true，
            // 但匿名用户不应被视为已认证用户
            return authentication != null
                    && authentication.isAuthenticated()
                    && !(authentication instanceof AnonymousAuthenticationToken);
        } catch (Exception e) {
            return false;
        }
    }
}
