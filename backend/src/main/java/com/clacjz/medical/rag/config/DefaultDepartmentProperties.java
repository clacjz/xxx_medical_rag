package com.clacjz.medical.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.default-department")
public class DefaultDepartmentProperties {

    private boolean enabled = true;
    private String code = "DEFAULT";
    private String name = "默认部门";
    private String description = "系统预设的默认部门，所有未分配部门的用户将自动加入此部门";
    private String parentId = null;
    private boolean autoAssignNewUsers = true;
    private boolean autoAssignUnassignedUsers = false;
}
