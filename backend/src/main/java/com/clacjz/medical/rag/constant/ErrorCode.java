package com.clacjz.medical.rag.constant;


public enum ErrorCode {
    // 系统错误码 (1xxx)
    SUCCESS(200, "操作成功"),
    SYSTEM_ERROR(1000, "系统错误"),
    SERVICE_UNAVAILABLE(1001, "服务不可用"),
    SERVICE_TIMEOUT(1002, "服务超时"),
    INVALID_PARAMETER(1003, "参数错误"),
    RESOURCE_NOT_FOUND(1004, "资源不存在"),
    OPERATION_FAILED(1005, "操作失败"),

    // 认证错误码 (2xxx)
    AUTHENTICATION_FAILED(2001, "认证失败"),
    INVALID_TOKEN(2002, "无效的Token"),
    TOKEN_EXPIRED(2003, "Token已过期"),
    TOKEN_MISSING(2004, "Token缺失"),
    INSUFFICIENT_PERMISSIONS(2005, "权限不足"),
    ACCOUNT_LOCKED(2006, "账户已被锁定"),
    ACCOUNT_DISABLED(2007, "账户已被禁用"),
    TOO_MANY_REQUESTS(2008, "请求过于频繁"),

    // 用户管理错误码 (3xxx)
    USER_NOT_FOUND(3001, "用户不存在"),
    USER_ALREADY_EXISTS(3002, "用户已存在"),
    INVALID_PASSWORD(3003, "密码错误"),
    PASSWORD_MISMATCH(3004, "两次密码输入不一致"),
    INVALID_EMAIL(3005, "邮箱格式错误"),
    INVALID_USERNAME(3006, "用户名格式错误"),

    // 文档管理错误码 (4xxx)
    DOCUMENT_NOT_FOUND(4001, "文档不存在"),
    DOCUMENT_UPLOAD_FAILED(4002, "文档上传失败"),
    DOCUMENT_FORMAT_UNSUPPORTED(4003, "不支持的文件格式"),
    DOCUMENT_SIZE_EXCEEDED(4004, "文件大小超出限制"),
    DOCUMENT_PARSING_FAILED(4005, "文档解析失败"),
    DOCUMENT_DELETION_FAILED(4006, "文档删除失败"),
    DOCUMENT_OVERSIZED(4007, "超出最大页数限制，请分页预览"),
    OCR_SERVICE_UNAVAILABLE(4008, "OCR服务不可用"),
    OCR_RECOGNITION_FAILED(4009, "OCR识别失败"),

    // 知识库错误码 (5xxx)
    COLLECTION_NOT_FOUND(5001, "知识库不存在"),
    COLLECTION_ALREADY_EXISTS(5002, "知识库已存在"),
    COLLECTION_CREATION_FAILED(5003, "知识库创建失败"),
    COLLECTION_ERROR(5004, "Collection操作错误"),

    // 向量数据库错误码 (14xxx)
    VECTOR_ERROR(14001, "向量操作错误"),
    VECTOR_NOT_FOUND(14002, "向量不存在"),
    BATCH_OPERATION_ERROR(14003, "批量操作错误"),

    // RAG 查询错误码 (6xxx)
    QUERY_FAILED(6001, "问答查询失败"),
    EMBEDDING_FAILED(6002, "文本向量化失败"),
    RETRIEVAL_FAILED(6003, "语义检索失败"),
    LLM_GENERATION_FAILED(6004, "LLM生成失败"),
    CONTEXT_TOO_LONG(6005, "上下文超出长度限制"),

    // AI 服务错误码 (7xxx)
    AI_SERVICE_UNAVAILABLE(7001, "AI服务不可用"),
    AI_SERVICE_TIMEOUT(7002, "AI服务响应超时"),
    EMBEDDING_SERVICE_ERROR(7003, "Embedding服务错误"),
    LLM_SERVICE_ERROR(7004, "LLM服务错误"),
    MODEL_NOT_FOUND(7005, "模型不存在"),

    // 会话错误码 (8xxx)
    CONVERSATION_NOT_FOUND(8001, "会话不存在"),
    CONVERSATION_CREATION_FAILED(8002, "会话创建失败"),
    MESSAGE_SEND_FAILED(8003, "消息发送失败"),
    CONVERSATION_EXPORT_FAILED(8004, "会话导出失败"),

    // 导出错误码 (13xxx)
    EXPORT_FAILED(13001, "导出失败"),
    EXPORT_FORMAT_UNSUPPORTED(13002, "不支持的导出格式"),
    EXPORT_DATA_TOO_LARGE(13003, "导出数据量过大"),
    PDF_EXPORT_FAILED(13004, "PDF导出失败"),
    MARKDOWN_EXPORT_FAILED(13005, "Markdown导出失败"),
    FONT_LOAD_FAILED(13006, "字体加载失败"),
    EXPORT_VALIDATION_FAILED(13007, "导出参数验证失败"),

    // 部门错误码 (9xxx)
    DEPARTMENT_NOT_FOUND(9001, "部门不存在"),
    DEPARTMENT_ALREADY_EXISTS(9002, "部门已存在"),
    DEPARTMENT_HAS_USERS(9003, "部门下存在用户，无法删除"),
    INVALID_DEPARTMENT_CODE(9004, "无效的部门编码"),

    // 角色错误码 (12xxx)
    ROLE_NOT_FOUND(12001, "角色不存在"),
    ROLE_ALREADY_EXISTS(12002, "角色已存在"),
    ROLE_HAS_USERS(12003, "角色下存在用户，无法删除"),
    USER_ROLE_ALREADY_EXISTS(12004, "用户已拥有该角色"),
    USER_ROLE_NOT_FOUND(12005, "用户角色关联不存在"),
    CANNOT_DELETE_SYSTEM_ROLE(12006, "系统内置角色无法删除"),
    LAST_PRIMARY_ROLE_CANNOT_DELETE(12007, "不能删除用户的唯一主角色"),
    CANNOT_MODIFY_SYSTEM_ROLE_PERMISSIONS(12008, "系统内置角色的权限不可修改"),

    // 权限错误码 (10xxx)
    PERMISSION_DENIED(10001, "权限不足"),
    DOCUMENT_PERMISSION_NOT_FOUND(10002, "文档权限配置不存在"),
    USER_PERMISSION_EXISTS(10003, "用户已有该文档权限"),
    USER_PERMISSION_NOT_FOUND(10004, "用户权限不存在"),
    PERMISSION_EXPIRED(10005, "权限已过期"),
    INVALID_PERMISSION_TYPE(10006, "无效的权限类型"),
    CANNOT_MODIFY_OTHERS_PERMISSION(10007, "无权修改他人权限"),

    // 审计错误码 (11xxx)
    AUDIT_LOG_NOT_FOUND(11001, "审计日志不存在"),
    INVALID_AUDIT_QUERY(11002, "无效的审计查询参数");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getFormattedMessage(Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        return String.format(message, args);
    }
}
