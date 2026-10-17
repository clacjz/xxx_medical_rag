package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.mapper.SysConfigMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.model.dto.SysConfigDTO;
import com.clacjz.medical.rag.model.entity.SysConfig;
import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.enums.AuditActionEnums;
import com.clacjz.medical.rag.model.enums.AuditLevelEnums;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigService 单元测试")
class ConfigServiceTest {

    @Mock
    private SysConfigMapper sysConfigMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private AuditService auditService;

    @Mock
    private ConfigRefreshService configRefreshService;

    @InjectMocks
    private ConfigService configService;

    private SysConfig testSysConfig;
    private User testUser;

    private static final String TEST_CONFIG_KEY = "test.config.key";
    private static final String TEST_CATEGORY = "llm";
    private static final String TEST_USER_ID = "user-123";
    private static final String TEST_OPERATOR_ID = "operator-456";

    @BeforeEach
    void setUp() {
        testSysConfig = new SysConfig();
        testSysConfig.setId("config-1");
        testSysConfig.setConfigKey(TEST_CONFIG_KEY);
        testSysConfig.setCategory(TEST_CATEGORY);
        testSysConfig.setValue("oldValue");
        testSysConfig.setDescription("测试配置");
        testSysConfig.setUpdatedAt(LocalDateTime.now().minusDays(1));

        testUser = new User();
        testUser.setId(TEST_USER_ID);
        testUser.setUsername("testuser");
        testUser.setDisplayName("测试用户");
    }

    @Nested
    @DisplayName("getConfigByCategory - 按分类获取配置")
    class GetConfigByCategoryTests {

        @Test
        @DisplayName("返回配置列表")
        void shouldReturnConfigList() {
            when(sysConfigMapper.selectByCategory(TEST_CATEGORY))
                    .thenReturn(List.of(testSysConfig));

            List<SysConfigDTO> result = configService.getConfigByCategory(TEST_CATEGORY);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(TEST_CONFIG_KEY, result.get(0).getConfigKey());
            assertEquals(TEST_CATEGORY, result.get(0).getCategory());
        }

        @Test
        @DisplayName("分类为空时返回空列表")
        void shouldReturnEmptyListWhenCategoryEmpty() {
            when(sysConfigMapper.selectByCategory("")).thenReturn(Collections.emptyList());

            List<SysConfigDTO> result = configService.getConfigByCategory("");

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("分类不存在时返回空列表")
        void shouldReturnEmptyListWhenCategoryNotExists() {
            when(sysConfigMapper.selectByCategory("nonexistent")).thenReturn(Collections.emptyList());

            List<SysConfigDTO> result = configService.getConfigByCategory("nonexistent");

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("getConfigByKey - 按键获取配置")
    class GetConfigByKeyTests {

        @Test
        @DisplayName("配置存在时返回配置")
        void shouldReturnConfigWhenExists() {
            when(sysConfigMapper.selectByConfigKey(TEST_CONFIG_KEY)).thenReturn(testSysConfig);

            SysConfigDTO result = configService.getConfigByKey(TEST_CONFIG_KEY);

            assertNotNull(result);
            assertEquals(TEST_CONFIG_KEY, result.getConfigKey());
        }

        @Test
        @DisplayName("配置不存在时抛出异常")
        void shouldThrowWhenConfigNotFound() {
            when(sysConfigMapper.selectByConfigKey("nonexistent")).thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> configService.getConfigByKey("nonexistent"));

            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("配置项不存在"));
        }
    }

    @Nested
    @DisplayName("getAllConfigs - 获取所有配置")
    class GetAllConfigsTests {

        @Test
        @DisplayName("返回所有配置")
        void shouldReturnAllConfigs() {
            SysConfig config2 = new SysConfig();
            config2.setId("config-2");
            config2.setConfigKey("another.key");
            config2.setCategory("embedding");
            config2.setValue("value2");

            when(sysConfigMapper.selectAll()).thenReturn(Arrays.asList(testSysConfig, config2));

            List<SysConfigDTO> result = configService.getAllConfigs();

            assertNotNull(result);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("无配置时返回空列表")
        void shouldReturnEmptyListWhenNoConfigs() {
            when(sysConfigMapper.selectAll()).thenReturn(Collections.emptyList());

            List<SysConfigDTO> result = configService.getAllConfigs();

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("getAllConfigsGrouped - 获取分组配置")
    class GetAllConfigsGroupedTests {

        @Test
        @DisplayName("按分类分组返回配置")
        void shouldReturnGroupedConfigs() {
            SysConfig config2 = new SysConfig();
            config2.setId("config-2");
            config2.setConfigKey("embed.batch_size");
            config2.setCategory("embedding");
            config2.setValue("32");

            when(sysConfigMapper.selectAll()).thenReturn(Arrays.asList(testSysConfig, config2));

            Map<String, Map<String, String>> result = configService.getAllConfigsGrouped();

            assertNotNull(result);
            assertEquals(2, result.size());
            assertTrue(result.containsKey(TEST_CATEGORY));
            assertTrue(result.containsKey("embedding"));
            assertEquals("oldValue", result.get(TEST_CATEGORY).get(TEST_CONFIG_KEY));
            assertEquals("32", result.get("embedding").get("embed.batch_size"));
        }

        @Test
        @DisplayName("空配置时返回空map")
        void shouldReturnEmptyMapWhenNoConfigs() {
            when(sysConfigMapper.selectAll()).thenReturn(Collections.emptyList());

            Map<String, Map<String, String>> result = configService.getAllConfigsGrouped();

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("updateConfig - 更新单个配置")
    class UpdateConfigTests {

        @Test
        @DisplayName("更新配置成功")
        void shouldUpdateConfigSuccessfully() {
            when(sysConfigMapper.selectByConfigKey(TEST_CONFIG_KEY)).thenReturn(testSysConfig);
            when(userMapper.selectById(TEST_OPERATOR_ID)).thenReturn(testUser);
            when(sysConfigMapper.updateById(any(SysConfig.class))).thenReturn(1);

            SysConfigDTO result = configService.updateConfig(
                    TEST_CONFIG_KEY, "newValue", TEST_OPERATOR_ID);

            assertNotNull(result);
            assertEquals("newValue", result.getValue());
            verify(configRefreshService).refreshConfig(TEST_CATEGORY, TEST_CONFIG_KEY, "newValue");
            verify(auditService).logAction(
                    eq(TEST_OPERATOR_ID),
                    eq("testuser"),
                    eq("测试用户"),
                    eq(AuditActionEnums.SYSTEM_CONFIG_UPDATE),
                    eq(AuditLevelEnums.INFO),
                    eq("SYSTEM"),
                    eq(testSysConfig.getId()),
                    anyString(),
                    isNull(),
                    isNull(),
                    anyMap(),
                    eq("success")
            );
        }

        @Test
        @DisplayName("配置不存在时抛出异常")
        void shouldThrowWhenConfigNotFoundOnUpdate() {
            when(sysConfigMapper.selectByConfigKey("nonexistent")).thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> configService.updateConfig("nonexistent", "value", TEST_OPERATOR_ID));

            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("操作员不存在时使用system作为默认值")
        void shouldUseSystemWhenOperatorNotFound() {
            when(sysConfigMapper.selectByConfigKey(TEST_CONFIG_KEY)).thenReturn(testSysConfig);
            when(userMapper.selectById("nonexistent")).thenReturn(null);
            when(sysConfigMapper.updateById(any(SysConfig.class))).thenReturn(1);

            SysConfigDTO result = configService.updateConfig(
                    TEST_CONFIG_KEY, "newValue", "nonexistent");

            assertNotNull(result);
            verify(auditService, atLeastOnce()).logAction(
                    anyString(), anyString(), anyString(),
                    eq(AuditActionEnums.SYSTEM_CONFIG_UPDATE),
                    eq(AuditLevelEnums.INFO),
                    eq("SYSTEM"),
                    anyString(),
                    anyString(),
                    isNull(),
                    isNull(),
                    anyMap(),
                    eq("success")
            );
        }
    }

    @Nested
    @DisplayName("updateConfigByCategory - 批量更新分类配置")
    class UpdateConfigByCategoryTests {

        @Test
        @DisplayName("批量更新配置成功")
        void shouldBatchUpdateConfigsSuccessfully() {
            SysConfig config2 = new SysConfig();
            config2.setId("config-2");
            config2.setConfigKey("batch_size");
            config2.setCategory(TEST_CATEGORY);
            config2.setValue("oldBatchSize");

            when(sysConfigMapper.selectByCategory(TEST_CATEGORY))
                    .thenReturn(Arrays.asList(testSysConfig, config2));
            when(userMapper.selectById(TEST_OPERATOR_ID)).thenReturn(testUser);
            when(sysConfigMapper.updateById(any(SysConfig.class))).thenReturn(1);

            Map<String, String> values = Map.of(
                    TEST_CONFIG_KEY, "newValue",
                    "batch_size", "64"
            );

            List<SysConfigDTO> result = configService.updateConfigByCategory(
                    TEST_CATEGORY, values, TEST_OPERATOR_ID);

            assertNotNull(result);
            assertEquals(2, result.size());
            verify(configRefreshService, times(2)).refreshConfig(anyString(), anyString(), anyString());
            verify(auditService, times(2)).logAction(
                    anyString(), anyString(), anyString(),
                    eq(AuditActionEnums.SYSTEM_CONFIG_UPDATE),
                    eq(AuditLevelEnums.INFO),
                    eq("SYSTEM"),
                    anyString(),
                    anyString(),
                    isNull(),
                    isNull(),
                    anyMap(),
                    eq("success")
            );
        }

        @Test
        @DisplayName("分类不存在时抛出异常")
        void shouldThrowWhenCategoryNotFound() {
            when(sysConfigMapper.selectByCategory("nonexistent"))
                    .thenReturn(Collections.emptyList());

            BizException exception = assertThrows(BizException.class,
                    () -> configService.updateConfigByCategory(
                            "nonexistent", Map.of("key", "value"), TEST_OPERATOR_ID));

            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("配置分类不存在"));
        }

        @Test
        @DisplayName("只更新存在的键，忽略不存在的键")
        void shouldOnlyUpdateExistingKeys() {
            when(sysConfigMapper.selectByCategory(TEST_CATEGORY))
                    .thenReturn(List.of(testSysConfig));
            when(userMapper.selectById(TEST_OPERATOR_ID)).thenReturn(testUser);
            when(sysConfigMapper.updateById(any(SysConfig.class))).thenReturn(1);

            Map<String, String> values = Map.of(
                    TEST_CONFIG_KEY, "newValue",
                    "nonexistent_key", "ignored"
            );

            List<SysConfigDTO> result = configService.updateConfigByCategory(
                    TEST_CATEGORY, values, TEST_OPERATOR_ID);

            assertEquals(1, result.size());
            assertEquals("newValue", result.get(0).getValue());
        }

        @Test
        @DisplayName("空值map时不更新任何配置")
        void shouldNotUpdateWhenEmptyValues() {
            when(sysConfigMapper.selectByCategory(TEST_CATEGORY))
                    .thenReturn(List.of(testSysConfig));

            List<SysConfigDTO> result = configService.updateConfigByCategory(
                    TEST_CATEGORY, Collections.emptyMap(), TEST_OPERATOR_ID);

            assertEquals(1, result.size());
            verify(sysConfigMapper, never()).updateById(any());
        }
    }
}
