package com.clacjz.medical.rag.model.enums;

public enum AuditActionEnums {
    LOGIN("登录", "AUTH"),
    LOGOUT("注销", "AUTH"),
    REGISTER("注册", "AUTH"),
    REFRESH_TOKEN("刷新令牌", "AUTH"),
    PASSWORD_CHANGE("修改密码", "AUTH"),
    PASSWORD_RESET("重置密码", "AUTH"),

    USER_CREATE("创建用户", "USER"),
    USER_UPDATE("更新用户", "USER"),
    USER_DELETE("删除用户", "USER"),
    USER_DISABLE("禁用用户", "USER"),
    USER_ENABLE("启用用户", "USER"),
    USER_LOCK("锁定用户", "USER"),
    USER_UNLOCK("解锁用户", "USER"),
    USER_VIEW("查看用户", "USER"),

    DEPT_CREATE("创建部门", "DEPT"),
    DEPT_UPDATE("更新部门", "DEPT"),
    DEPT_DELETE("删除部门", "DEPT"),
    DEPT_VIEW("查看部门", "DEPT"),

    COLLECTION_CREATE("创建知识库", "COLLECTION"),
    COLLECTION_UPDATE("更新知识库", "COLLECTION"),
    COLLECTION_DELETE("删除知识库", "COLLECTION"),
    COLLECTION_VIEW("查看知识库", "COLLECTION"),
    COLLECTION_CONFIG_UPDATE("更新知识库配置", "COLLECTION"),

    DOCUMENT_UPLOAD("上传文档", "DOCUMENT"),
    DOCUMENT_DOWNLOAD("下载文档", "DOCUMENT"),
    DOCUMENT_DELETE("删除文档", "DOCUMENT"),
    DOCUMENT_VIEW("查看文档", "DOCUMENT"),
    DOCUMENT_UPDATE("更新文档", "DOCUMENT"),
    DOCUMENT_SHARE("分享文档", "DOCUMENT"),

    PERMISSION_GRANT("授予权限", "PERMISSION"),
    PERMISSION_REVOKE("撤销权限", "PERMISSION"),
    PERMISSION_UPDATE("更新权限", "PERMISSION"),
    PERMISSION_VIEW("查看权限", "PERMISSION"),

    QA_QUERY("问答查询", "QA"),
    QA_CONVERSATION_CREATE("创建对话", "QA"),
    QA_CONVERSATION_DELETE("删除对话", "QA"),

    SYSTEM_CONFIG_VIEW("查看系统配置", "SYSTEM"),
    SYSTEM_CONFIG_UPDATE("更新系统配置", "SYSTEM"),
    SYSTEM_BACKUP("系统备份", "SYSTEM"),
    SYSTEM_RESTORE("系统恢复", "SYSTEM"),
    SYSTEM_OPERATION("系统操作", "SYSTEM"),

    SECURITY_LOGIN_FAILED("登录失败", "SECURITY"),
    SECURITY_ACCOUNT_LOCKED("账户锁定", "SECURITY"),
    SECURITY_SUSPICIOUS_ACTIVITY("可疑活动", "SECURITY"),
    SECURITY_TOKEN_EXPIRED("令牌过期", "SECURITY"),

    EXPORT("导出数据", "OTHER"),
    IMPORT("导入数据", "OTHER"),
    BATCH_OPERATION("批量操作", "OTHER");

    private final String description;
    private final String category;

    AuditActionEnums(String description, String category) {
        this.description = description;
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }
}
