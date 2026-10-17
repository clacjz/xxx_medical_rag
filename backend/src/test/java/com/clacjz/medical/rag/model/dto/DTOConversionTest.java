package com.clacjz.medical.rag.model.dto;

import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import com.clacjz.medical.rag.model.enums.UserStatusEnums;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DTO 转换测试")
class DTOConversionTest {

    @Nested
    @DisplayName("LoginRequest 测试")
    class LoginRequestTests {

        @Test
        @DisplayName("创建 LoginRequest")
        void createLoginRequest() {
            LoginRequest request = LoginRequest.builder()
                    .username("testuser")
                    .password("password123")
                    .build();

            assertNotNull(request);
            assertEquals("testuser", request.getUsername());
            assertEquals("password123", request.getPassword());
        }

        @Test
        @DisplayName("LoginRequest 缺失用户名")
        void loginRequest_MissingUsername() {
            LoginRequest request = LoginRequest.builder()
                    .password("password123")
                    .build();

            assertNotNull(request);
            assertNull(request.getUsername());
        }

        @Test
        @DisplayName("LoginRequest 缺失密码")
        void loginRequest_MissingPassword() {
            LoginRequest request = LoginRequest.builder()
                    .username("testuser")
                    .build();

            assertNotNull(request);
            assertNull(request.getPassword());
        }
    }

    @Nested
    @DisplayName("RegisterRequest 测试")
    class RegisterRequestTests {

        @Test
        @DisplayName("创建 RegisterRequest")
        void createRegisterRequest() {
            RegisterRequest request = RegisterRequest.builder()
                    .username("newuser")
                    .email("newuser@example.com")
                    .password("securePassword123")
                    .build();

            assertNotNull(request);
            assertEquals("newuser", request.getUsername());
            assertEquals("newuser@example.com", request.getEmail());
            assertEquals("securePassword123", request.getPassword());
        }

        @Test
        @DisplayName("RegisterRequest 可选字段为空")
        void registerRequest_OptionalFieldsNull() {
            RegisterRequest request = RegisterRequest.builder()
                    .username("newuser")
                    .password("password")
                    .build();

            assertNotNull(request);
            assertNull(request.getEmail());
        }
    }

    @Nested
    @DisplayName("UserDTO 测试")
    class UserDTOTests {

        @Test
        @DisplayName("创建 UserDTO")
        void createUserDTO() {
            LocalDateTime now = LocalDateTime.now();
            UserDTO dto = UserDTO.builder()
                    .id("user-123")
                    .username("testuser")
                    .email("test@example.com")
                    .displayName("Test User")
                    .role(UserRoleEnum.DEPT_USER)
                    .status(UserStatusEnums.ACTIVE)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            assertNotNull(dto);
            assertEquals("user-123", dto.getId());
            assertEquals("testuser", dto.getUsername());
            assertEquals("test@example.com", dto.getEmail());
            assertEquals("Test User", dto.getDisplayName());
            assertEquals(UserRoleEnum.DEPT_USER, dto.getRole());
            assertEquals(UserStatusEnums.ACTIVE, dto.getStatus());
        }

        @Test
        @DisplayName("UserDTO 缺失可选字段")
        void userDTO_OptionalFieldsNull() {
            UserDTO dto = UserDTO.builder()
                    .username("testuser")
                    .build();

            assertNotNull(dto);
            assertNull(dto.getId());
            assertNull(dto.getEmail());
            assertNull(dto.getDisplayName());
        }
    }

    @Nested
    @DisplayName("SearchResult 测试")
    class SearchResultTests {

        @Test
        @DisplayName("创建 SearchResult")
        void createSearchResult() {
            SearchResult result = SearchResult.builder()
                    .id("doc-123")
                    .text("This is the document content...")
                    .score(0.95)
                    .metadata(Map.of("author", "John Doe"))
                    .build();

            assertNotNull(result);
            assertEquals("doc-123", result.getId());
            assertEquals("This is the document content...", result.getText());
            assertEquals(0.95, result.getScore());
            assertEquals("John Doe", result.getMetadata().get("author"));
        }

        @Test
        @DisplayName("SearchResult 空元数据")
        void searchResult_EmptyMetadata() {
            SearchResult result = SearchResult.builder()
                    .id("doc-123")
                    .text("Content")
                    .score(0.5)
                    .metadata(new HashMap<>())
                    .build();

            assertNotNull(result);
            assertTrue(result.getMetadata().isEmpty());
        }

        @Test
        @DisplayName("SearchResult 零分")
        void searchResult_ZeroScore() {
            SearchResult result = SearchResult.builder()
                    .id("doc-123")
                    .text("Content")
                    .score(0.0)
                    .build();

            assertNotNull(result);
            assertEquals(0.0, result.getScore());
        }

        @Test
        @DisplayName("SearchResult 满分")
        void searchResult_PerfectScore() {
            SearchResult result = SearchResult.builder()
                    .id("doc-123")
                    .text("Content")
                    .score(1.0)
                    .build();

            assertNotNull(result);
            assertEquals(1.0, result.getScore());
        }
    }

    @Nested
    @DisplayName("CollectionDTO 测试")
    class CollectionDTOTests {

        @Test
        @DisplayName("创建 CollectionDTO")
        void createCollectionDTO() {
            CollectionDTO dto = CollectionDTO.builder()
                    .id("col-123")
                    .name("Medical Knowledge Base")
                    .description("Medical documents collection")
                    .type("KNOWLEDGE_BASE")
                    .status("ACTIVE")
                    .documentCount(100)
                    .chunkCount(1000L)
                    .totalSize(5000000L)
                    .build();

            assertNotNull(dto);
            assertEquals("col-123", dto.getId());
            assertEquals("Medical Knowledge Base", dto.getName());
            assertEquals("KNOWLEDGE_BASE", dto.getType());
            assertEquals("ACTIVE", dto.getStatus());
            assertEquals(100, dto.getDocumentCount());
            assertEquals(1000L, dto.getChunkCount());
            assertEquals(5000000L, dto.getTotalSize());
        }

        @Test
        @DisplayName("CollectionDTO 默认值")
        void collectionDTO_Defaults() {
            CollectionDTO dto = CollectionDTO.builder()
                    .name("Test Collection")
                    .build();

            assertNotNull(dto);
            assertEquals("Test Collection", dto.getName());
            assertNull(dto.getId());
            assertNull(dto.getDescription());
            assertNull(dto.getDocumentCount());
            assertNull(dto.getChunkCount());
        }
    }

    @Nested
    @DisplayName("RoleDTO 测试")
    class RoleDTOTests {

        @Test
        @DisplayName("创建 RoleDTO")
        void createRoleDTO() {
            RoleDTO dto = RoleDTO.builder()
                    .id("role-123")
                    .code("DEPT_ADMIN")
                    .name("Department Administrator")
                    .description("Manages department resources")
                    .level(2)
                    .userCount(10L)
                    .active(true)
                    .build();

            assertNotNull(dto);
            assertEquals("role-123", dto.getId());
            assertEquals("DEPT_ADMIN", dto.getCode());
            assertEquals("Department Administrator", dto.getName());
            assertEquals(2, dto.getLevel());
            assertEquals(10L, dto.getUserCount());
            assertTrue(dto.getActive());
        }

        @Test
        @DisplayName("RoleDTO 零用户数")
        void roleDTO_ZeroUsers() {
            RoleDTO dto = RoleDTO.builder()
                    .code("NEW_ROLE")
                    .name("New Role")
                    .level(4)
                    .userCount(0L)
                    .build();

            assertNotNull(dto);
            assertEquals(0L, dto.getUserCount());
        }
    }

    @Nested
    @DisplayName("DepartmentDTO 测试")
    class DepartmentDTOTests {

        @Test
        @DisplayName("创建 DepartmentDTO")
        void createDepartmentDTO() {
            LocalDateTime now = LocalDateTime.now();
            DepartmentDTO dto = DepartmentDTO.builder()
                    .id("dept-123")
                    .code("MEDICAL_DEPT")
                    .name("Medical Department")
                    .description("Handles medical records")
                    .parentId("dept-parent")
                    .userCount(50L)
                    .createdAt(now)
                    .build();

            assertNotNull(dto);
            assertEquals("dept-123", dto.getId());
            assertEquals("MEDICAL_DEPT", dto.getCode());
            assertEquals("Medical Department", dto.getName());
            assertEquals("dept-parent", dto.getParentId());
            assertEquals(50L, dto.getUserCount());
        }

        @Test
        @DisplayName("DepartmentDTO 根部门")
        void departmentDTO_RootDepartment() {
            DepartmentDTO dto = DepartmentDTO.builder()
                    .id("dept-root")
                    .code("ROOT")
                    .name("Root Department")
                    .build();

            assertNotNull(dto);
            assertNull(dto.getParentId());
        }
    }
}
