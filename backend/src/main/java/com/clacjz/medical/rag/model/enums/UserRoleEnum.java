package com.clacjz.medical.rag.model.enums;

public enum UserRoleEnum {
    SYSTEM_ADMIN(1, "系统管理员", "全系统最高权限，用户管理、部门管理、系统配置、审计日志查看"),
    DEPT_ADMIN(2, "部门管理员", "本部门全部权限，本部门用户管理、文档权限配置、本部门审计日志查看"),
    DEPT_UPLOADER(3, "部门文档上传者", "本部门普通权限，文档上传、文档权限配置（仅自己上传的）、文档删除（仅自己上传的）"),
    DEPT_USER(4, "部门普通人员", "文档查看（根据文档权限配置访问）、智能问答、对话历史查看、个人设置");

    private final int level;
    private final String description;
    private final String permissionSummary;

    UserRoleEnum(int level, String description, String permissionSummary) {
        this.level = level;
        this.description = description;
        this.permissionSummary = permissionSummary;
    }

    public int getLevel() {
        return level;
    }

    public String getDescription() {
        return description;
    }

    public String getPermissionSummary() {
        return permissionSummary;
    }

    public boolean isSystemAdmin() {
        return this == SYSTEM_ADMIN;
    }

    public boolean isDeptAdmin() {
        return this == DEPT_ADMIN;
    }

    public boolean isDeptUploader() {
        return this == DEPT_UPLOADER;
    }

    public boolean isDeptUser() {
        return this == DEPT_USER;
    }

    public boolean hasHigherOrEqualLevel(UserRoleEnum other) {
        return this.level <= other.level;
    }
}
