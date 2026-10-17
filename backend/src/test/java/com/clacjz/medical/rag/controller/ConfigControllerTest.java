package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.model.dto.SysConfigDTO;
import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigController 单元测试")
class ConfigControllerTest {

    @Mock
    private ConfigService configService;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private ConfigController configController;

    private UserDetails mockUserDetails;
    private User testUser;

    @BeforeEach
    void setUp() {
        mockUserDetails = org.springframework.security.core.userdetails.User.builder()
                .username("admin")
                .password("password")
                .authorities("ROLE_SYSTEM_ADMIN")
                .build();

        testUser = User.builder()
                .id("user-123")
                .username("admin")
                .build();
    }

    @Nested
    @DisplayName("获取配置测试")
    class GetConfigTests {

        @Test
        @DisplayName("获取所有配置成功")
        void getAllConfigs_Success() {
            List<SysConfigDTO> configs = List.of(
                    SysConfigDTO.builder().configKey("key1").value("value1").category("system").build(),
                    SysConfigDTO.builder().configKey("key2").value("value2").category("system").build()
            );
            when(configService.getAllConfigs()).thenReturn(configs);

            ResponseEntity<Result<List<SysConfigDTO>>> response = configController.getAllConfigs();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(2, response.getBody().getData().size());
        }

        @Test
        @DisplayName("获取所有配置返回空列表")
        void getAllConfigs_Empty() {
            when(configService.getAllConfigs()).thenReturn(List.of());

            ResponseEntity<Result<List<SysConfigDTO>>> response = configController.getAllConfigs();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertTrue(response.getBody().getData().isEmpty());
        }

        @Test
        @DisplayName("按分类获取配置成功")
        void getConfigByCategory_Success() {
            List<SysConfigDTO> configs = List.of(
                    SysConfigDTO.builder().configKey("embed.model").value("BGE").category("embedding").build()
            );
            when(configService.getConfigByCategory("embedding")).thenReturn(configs);

            ResponseEntity<Result<List<SysConfigDTO>>> response =
                    configController.getConfigByCategory("embedding");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(1, response.getBody().getData().size());
            assertEquals("embedding", response.getBody().getData().get(0).getCategory());
        }

        @Test
        @DisplayName("获取单个配置成功")
        void getConfigByKey_Success() {
            SysConfigDTO config = SysConfigDTO.builder()
                    .configKey("embed.model")
                    .value("BGE-large")
                    .category("embedding")
                    .build();
            when(configService.getConfigByKey("embed.model")).thenReturn(config);

            ResponseEntity<Result<SysConfigDTO>> response = configController.getConfigByKey("embed.model");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("embed.model", response.getBody().getData().getConfigKey());
        }
    }

    @Nested
    @DisplayName("更新配置测试")
    class UpdateConfigTests {

        @Test
        @DisplayName("更新单个配置成功")
        void updateConfig_Success() {
            SysConfigDTO updated = SysConfigDTO.builder()
                    .configKey("embed.model")
                    .value("BGE-small")
                    .category("embedding")
                    .build();

            when(userMapper.selectByUsername("admin")).thenReturn(testUser);
            when(configService.updateConfig(eq("embed.model"), eq("BGE-small"), eq("user-123")))
                    .thenReturn(updated);

            ResponseEntity<Result<SysConfigDTO>> response =
                    configController.updateConfig("embed.model", "BGE-small", mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("配置更新成功", response.getBody().getMessage());
            assertEquals("BGE-small", response.getBody().getData().getValue());
        }

        @Test
        @DisplayName("更新配置 - 用户不存在使用system")
        void updateConfig_UserNotFound_UsesSystem() {
            SysConfigDTO updated = SysConfigDTO.builder()
                    .configKey("embed.model")
                    .value("BGE-small")
                    .build();

            when(userMapper.selectByUsername("admin")).thenReturn(null);
            when(configService.updateConfig(eq("embed.model"), eq("BGE-small"), eq("system")))
                    .thenReturn(updated);

            ResponseEntity<Result<SysConfigDTO>> response =
                    configController.updateConfig("embed.model", "BGE-small", mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(configService).updateConfig("embed.model", "BGE-small", "system");
        }

        @Test
        @DisplayName("更新配置 - userDetails为null使用system")
        void updateConfig_NullUserDetails_UsesSystem() {
            SysConfigDTO updated = SysConfigDTO.builder()
                    .configKey("embed.model")
                    .value("BGE-small")
                    .build();

            when(configService.updateConfig(eq("embed.model"), eq("BGE-small"), eq("system")))
                    .thenReturn(updated);

            ResponseEntity<Result<SysConfigDTO>> response =
                    configController.updateConfig("embed.model", "BGE-small", null);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(configService).updateConfig("embed.model", "BGE-small", "system");
        }

        @Test
        @DisplayName("批量更新配置成功")
        void batchUpdateConfig_Success() {
            Map<String, String> values = Map.of(
                    "key1", "value1",
                    "key2", "value2"
            );

            List<SysConfigDTO> updated = List.of(
                    SysConfigDTO.builder().configKey("key1").value("value1").build(),
                    SysConfigDTO.builder().configKey("key2").value("value2").build()
            );

            when(userMapper.selectByUsername("admin")).thenReturn(testUser);
            when(configService.updateConfigByCategory(eq("embedding"), eq(values), eq("user-123")))
                    .thenReturn(updated);

            ResponseEntity<Result<List<SysConfigDTO>>> response =
                    configController.batchUpdateConfig("embedding", values, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("配置批量更新成功", response.getBody().getMessage());
            assertEquals(2, response.getBody().getData().size());
        }

        @Test
        @DisplayName("批量更新配置 - 空映射")
        void batchUpdateConfig_EmptyMap() {
            Map<String, String> values = Map.of();

            when(userMapper.selectByUsername("admin")).thenReturn(testUser);
            when(configService.updateConfigByCategory(eq("embedding"), eq(values), eq("user-123")))
                    .thenReturn(List.of());

            ResponseEntity<Result<List<SysConfigDTO>>> response =
                    configController.batchUpdateConfig("embedding", values, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }
}
