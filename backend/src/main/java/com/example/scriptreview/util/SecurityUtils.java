package com.example.scriptreview.util;

import com.example.scriptreview.entity.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 获取当前登录用户。JWT 过滤器将 User 实体放入 SecurityContext principal。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            throw new AccessDeniedException("未登录或登录状态已失效");
        }
        return user;
    }

    public static User requireRole(String role) {
        User user = getCurrentUser();
        if (!role.equals(user.getRole())) {
            throw new AccessDeniedException("无权执行该操作");
        }
        return user;
    }
}
