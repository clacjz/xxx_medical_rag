package com.clacjz.medical.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.default-users")
public class DefaultUsersProperties {

    private boolean enabled = true;
    private List<DefaultUser> users = new ArrayList<>();

    @Data
    public static class DefaultUser {
        private String username;
        private String password;
        private String email;
        private String displayName;
        private String role;
        private boolean enabled = true;
    }
}
