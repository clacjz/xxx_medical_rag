package com.clacjz.medical.rag.context;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TenantContext {

    private static final ThreadLocal<String> CURRENT_DEPARTMENT = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_USER_ROLE = new ThreadLocal<>();

    public static void setDepartmentId(String departmentId) {
        CURRENT_DEPARTMENT.set(departmentId);
        log.debug("Tenant context set: departmentId={}", departmentId);
    }

    public static String getDepartmentId() {
        return CURRENT_DEPARTMENT.get();
    }

    public static void setUserId(String userId) {
        CURRENT_USER.set(userId);
    }

    public static String getUserId() {
        return CURRENT_USER.get();
    }

    public static void setUserRole(String role) {
        CURRENT_USER_ROLE.set(role);
    }

    public static String getUserRole() {
        return CURRENT_USER_ROLE.get();
    }

    public static void clear() {
        CURRENT_DEPARTMENT.remove();
        CURRENT_USER.remove();
        CURRENT_USER_ROLE.remove();
        log.debug("Tenant context cleared");
    }

    public static boolean hasDepartment() {
        return CURRENT_DEPARTMENT.get() != null;
    }

    public static boolean isAdmin() {
        String role = CURRENT_USER_ROLE.get();
        return "SYSTEM_ADMIN".equals(role) || "DEPT_ADMIN".equals(role);
    }

    public static boolean isSystemAdmin() {
        return "SYSTEM_ADMIN".equals(CURRENT_USER_ROLE.get());
    }
}
