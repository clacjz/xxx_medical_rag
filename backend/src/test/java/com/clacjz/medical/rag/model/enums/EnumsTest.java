package com.clacjz.medical.rag.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Enum 类型测试")
class EnumsTest {

    @Nested
    @DisplayName("ChunkingStrategy")
    class ChunkingStrategyTests {

        @Test
        @DisplayName("包含 6 种分块策略")
        void shouldHaveSixStrategies() {
            assertEquals(6, ChunkingStrategy.values().length);
        }

        @Test
        @DisplayName("每个策略都有中文标签")
        void shouldHaveLabel() {
            for (ChunkingStrategy s : ChunkingStrategy.values()) {
                assertNotNull(s.getLabel());
                assertFalse(s.getLabel().isBlank());
            }
        }

        @Test
        @DisplayName("valueOf 转换正确")
        void shouldSupportValueOf() {
            assertEquals(ChunkingStrategy.FIXED, ChunkingStrategy.valueOf("FIXED"));
            assertEquals(ChunkingStrategy.SEMANTIC, ChunkingStrategy.valueOf("SEMANTIC"));
            assertEquals(ChunkingStrategy.PARAGRAPH, ChunkingStrategy.valueOf("PARAGRAPH"));
        }

        @Test
        @DisplayName("FIXED 标签为'固定长度分块'")
        void shouldHaveCorrectFixedLabel() {
            assertEquals("固定长度分块", ChunkingStrategy.FIXED.getLabel());
        }

        @Test
        @DisplayName("SEMANTIC 标签为'语义分块'")
        void shouldHaveCorrectSemanticLabel() {
            assertEquals("语义分块", ChunkingStrategy.SEMANTIC.getLabel());
        }

        @Test
        @DisplayName("NONE 表示不切片")
        void shouldHaveNoneStrategy() {
            assertEquals(ChunkingStrategy.NONE, ChunkingStrategy.valueOf("NONE"));
        }
    }

    @Nested
    @DisplayName("DocType")
    class DocTypeTests {

        @Test
        @DisplayName("包含 NORMAL 和 MANUAL 两种类型")
        void shouldHaveBothTypes() {
            assertNotNull(DocType.NORMAL);
            assertNotNull(DocType.MANUAL);
        }

        @Test
        @DisplayName("valueOf 转换正确")
        void shouldSupportValueOf() {
            assertEquals(DocType.NORMAL, DocType.valueOf("NORMAL"));
            assertEquals(DocType.MANUAL, DocType.valueOf("MANUAL"));
        }
    }

    @Nested
    @DisplayName("UserRoleEnum")
    class UserRoleEnumTests {

        @Test
        @DisplayName("包含 4 种角色")
        void shouldHaveFourRoles() {
            assertEquals(4, UserRoleEnum.values().length);
        }

        @Test
        @DisplayName("valueOf 转换正确")
        void shouldSupportValueOf() {
            assertEquals(UserRoleEnum.SYSTEM_ADMIN, UserRoleEnum.valueOf("SYSTEM_ADMIN"));
            assertEquals(UserRoleEnum.DEPT_ADMIN, UserRoleEnum.valueOf("DEPT_ADMIN"));
            assertEquals(UserRoleEnum.DEPT_UPLOADER, UserRoleEnum.valueOf("DEPT_UPLOADER"));
            assertEquals(UserRoleEnum.DEPT_USER, UserRoleEnum.valueOf("DEPT_USER"));
        }
    }

    @Nested
    @DisplayName("UserStatusEnums")
    class UserStatusEnumsTests {

        @Test
        @DisplayName("包含 ACTIVE/DISABLED/LOCKED 等状态")
        void shouldHaveStatusValues() {
            assertNotNull(UserStatusEnums.ACTIVE);
            assertNotNull(UserStatusEnums.DISABLED);
            assertNotNull(UserStatusEnums.LOCKED);
        }

        @Test
        @DisplayName("每个状态都有描述")
        void shouldHaveDescription() {
            for (UserStatusEnums s : UserStatusEnums.values()) {
                assertNotNull(s.getDescription());
                assertFalse(s.getDescription().isBlank());
            }
        }
    }

    @Nested
    @DisplayName("OcrStatus")
    class OcrStatusTests {

        @Test
        @DisplayName("包含核心状态")
        void shouldHaveCoreStatuses() {
            assertNotNull(OcrStatus.SUCCESS);
            assertNotNull(OcrStatus.FAILED);
            assertNotNull(OcrStatus.EXCEEDED);
            assertNotNull(OcrStatus.PARTIAL);
        }

        @Test
        @DisplayName("valueOf 转换正确")
        void shouldSupportValueOf() {
            assertEquals(OcrStatus.SUCCESS, OcrStatus.valueOf("SUCCESS"));
            assertEquals(OcrStatus.FAILED, OcrStatus.valueOf("FAILED"));
        }
    }

    @Nested
    @DisplayName("CollectionStatus")
    class CollectionStatusTests {

        @Test
        @DisplayName("valueOf 转换正确")
        void shouldSupportValueOf() {
            assertEquals(CollectionStatus.ACTIVE, CollectionStatus.valueOf("ACTIVE"));
        }
    }

    @Nested
    @DisplayName("CollectionType")
    class CollectionTypeTests {

        @Test
        @DisplayName("valueOf 转换正确")
        void shouldSupportValueOf() {
            assertEquals(CollectionType.KNOWLEDGE_BASE, CollectionType.valueOf("KNOWLEDGE_BASE"));
            assertEquals(CollectionType.PRODUCT_MANUAL, CollectionType.valueOf("PRODUCT_MANUAL"));
        }

        @Test
        @DisplayName("每个类型都有 label 和 description")
        void shouldHaveLabelAndDescription() {
            for (CollectionType t : CollectionType.values()) {
                assertNotNull(t.getLabel());
                assertNotNull(t.getDescription());
            }
        }
    }

    @Nested
    @DisplayName("PermissionLevelEnums")
    class PermissionLevelEnumsTests {

        @Test
        @DisplayName("包含 READ/COMMENT/EDIT 等级")
        void shouldHaveLevels() {
            assertNotNull(PermissionLevelEnums.READ);
            assertNotNull(PermissionLevelEnums.COMMENT);
            assertNotNull(PermissionLevelEnums.EDIT);
        }
    }
}
