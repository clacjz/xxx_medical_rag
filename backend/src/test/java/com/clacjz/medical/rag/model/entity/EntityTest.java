package com.clacjz.medical.rag.model.entity;

import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import com.clacjz.medical.rag.model.enums.UserStatusEnums;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Entity 实体测试")
class EntityTest {

    @Nested
    @DisplayName("User 实体测试")
    class UserEntityTests {

        @Test
        @DisplayName("User 创建")
        void createUser() {
            User user = User.builder()
                    .id("user-123")
                    .username("testuser")
                    .email("test@example.com")
                    .passwordHash("hashedpassword")
                    .displayName("Test User")
                    .build();

            assertNotNull(user);
            assertEquals("user-123", user.getId());
            assertEquals("testuser", user.getUsername());
            assertEquals("test@example.com", user.getEmail());
        }

        @Test
        @DisplayName("User.isLocked() - 未锁定")
        void userIsLocked_NotLocked() {
            User user = User.builder()
                    .lockUntil(null)
                    .build();

            assertFalse(user.isLocked());
        }

        @Test
        @DisplayName("User.isLocked() - 已锁定")
        void userIsLocked_Locked() {
            User user = User.builder()
                    .lockUntil(LocalDateTime.now().plusMinutes(30))
                    .build();

            assertTrue(user.isLocked());
        }

        @Test
        @DisplayName("User.isLocked() - 锁定已过期")
        void userIsLocked_Expired() {
            User user = User.builder()
                    .lockUntil(LocalDateTime.now().minusMinutes(1))
                    .build();

            assertFalse(user.isLocked());
        }

        @Test
        @DisplayName("User.isNotActive()")
        void userIsNotActive() {
            User activeUser = User.builder()
                    .status(UserStatusEnums.ACTIVE)
                    .build();

            User disabledUser = User.builder()
                    .status(UserStatusEnums.DISABLED)
                    .build();

            assertFalse(activeUser.isNotActive());
            assertTrue(disabledUser.isNotActive());
        }

        @Test
        @DisplayName("User.incrementLoginFailCount()")
        void userIncrementLoginFailCount() {
            User user = User.builder()
                    .loginFailCount(0)
                    .build();

            user.incrementLoginFailCount();
            assertEquals(1, user.getLoginFailCount());

            user.incrementLoginFailCount();
            user.incrementLoginFailCount();
            user.incrementLoginFailCount();
            assertEquals(4, user.getLoginFailCount());
            assertFalse(user.isLocked());
        }

        @Test
        @DisplayName("User.incrementLoginFailCount() - 触发锁定")
        void userIncrementLoginFailCount_TriggersLock() {
            User user = User.builder()
                    .loginFailCount(4)
                    .build();

            user.incrementLoginFailCount();
            assertEquals(5, user.getLoginFailCount());
            assertTrue(user.isLocked());
            assertNotNull(user.getLockUntil());
        }

        @Test
        @DisplayName("User.resetLoginFailCount()")
        void userResetLoginFailCount() {
            User user = User.builder()
                    .loginFailCount(5)
                    .lockUntil(LocalDateTime.now().plusMinutes(30))
                    .build();

            user.resetLoginFailCount();
            assertEquals(0, user.getLoginFailCount());
            assertNull(user.getLockUntil());
            assertFalse(user.isLocked());
        }

        @Test
        @DisplayName("User.lock()")
        void userLock() {
            User user = User.builder()
                    .loginFailCount(0)
                    .build();

            user.lock(java.time.Duration.ofMinutes(15));
            assertEquals(5, user.getLoginFailCount());
            assertTrue(user.isLocked());
            assertNotNull(user.getLockUntil());
        }

        @Test
        @DisplayName("User.getRemainingLockSeconds() - 无锁定")
        void userGetRemainingLockSeconds_NoLock() {
            User user = User.builder()
                    .lockUntil(null)
                    .build();

            assertEquals(0, user.getRemainingLockSeconds());
        }

        @Test
        @DisplayName("User.getRemainingLockSeconds() - 有效锁定")
        void userGetRemainingLockSeconds_ValidLock() {
            User user = User.builder()
                    .lockUntil(LocalDateTime.now().plusSeconds(300))
                    .build();

            assertTrue(user.getRemainingLockSeconds() > 0);
            assertTrue(user.getRemainingLockSeconds() <= 300);
        }
    }

    @Nested
    @DisplayName("Collection 实体测试")
    class CollectionEntityTests {

        @Test
        @DisplayName("Collection 创建")
        void createCollection() {
            Collection collection = Collection.builder()
                    .id("col-123")
                    .name("Test Collection")
                    .description("Test description")
                    .ownerId("user-123")
                    .departmentId("dept-123")
                    .build();

            assertNotNull(collection);
            assertEquals("col-123", collection.getId());
            assertEquals("Test Collection", collection.getName());
        }

        @Test
        @DisplayName("Collection.softDelete()")
        void collectionSoftDelete() {
            Collection collection = Collection.builder()
                    .id("col-123")
                    .name("Test")
                    .deletedAt(null)
                    .build();

            assertNull(collection.getDeletedAt());

            collection.softDelete();
            assertNotNull(collection.getDeletedAt());
            assertEquals(com.clacjz.medical.rag.model.enums.CollectionStatus.DELETED, collection.getStatus());
        }
    }

    @Nested
    @DisplayName("Document 实体测试")
    class DocumentEntityTests {

        @Test
        @DisplayName("Document 创建")
        void createDocument() {
            Document document = Document.builder()
                    .id("doc-123")
                    .title("Test Document")
                    .fileName("test.pdf")
                    .fileSize(1024L)
                    .ownerId("user-123")
                    .departmentId("dept-123")
                    .build();

            assertNotNull(document);
            assertEquals("doc-123", document.getId());
            assertEquals("Test Document", document.getTitle());
            assertEquals("test.pdf", document.getFileName());
            assertEquals(1024L, document.getFileSize());
        }
    }

    @Nested
    @DisplayName("Role 实体测试")
    class RoleEntityTests {

        @Test
        @DisplayName("Role 创建")
        void createRole() {
            Role role = Role.builder()
                    .id("role-123")
                    .code("DEPT_ADMIN")
                    .name("Department Admin")
                    .level(2)
                    .description("Manages department")
                    .active(true)
                    .build();

            assertNotNull(role);
            assertEquals("DEPT_ADMIN", role.getCode());
            assertEquals("Department Admin", role.getName());
            assertEquals(2, role.getLevel());
            assertTrue(role.getActive());
        }
    }

    @Nested
    @DisplayName("Department 实体测试")
    class DepartmentEntityTests {

        @Test
        @DisplayName("Department 创建")
        void createDepartment() {
            Department dept = Department.builder()
                    .id("dept-123")
                    .code("MEDICAL")
                    .name("Medical Department")
                    .description("Medical records")
                    .parentId("dept-parent")
                    .build();

            assertNotNull(dept);
            assertEquals("MEDICAL", dept.getCode());
            assertEquals("Medical Department", dept.getName());
            assertEquals("dept-parent", dept.getParentId());
        }

        @Test
        @DisplayName("Department - 根部门")
        void department_RootDepartment() {
            Department dept = Department.builder()
                    .id("dept-root")
                    .code("ROOT")
                    .name("Root")
                    .build();

            assertNotNull(dept);
            assertNull(dept.getParentId());
        }
    }

    @Nested
    @DisplayName("ChatSession 实体测试")
    class ChatSessionEntityTests {

        @Test
        @DisplayName("ChatSession 创建")
        void createChatSession() {
            ChatSession session = ChatSession.builder()
                    .id(UUID.randomUUID().toString())
                    .userId("user-123")
                    .title("Test Session")
                    .build();

            assertNotNull(session);
            assertEquals("user-123", session.getUserId());
            assertEquals("Test Session", session.getTitle());
        }
    }

    @Nested
    @DisplayName("ChatMessage 实体测试")
    class ChatMessageEntityTests {

        @Test
        @DisplayName("ChatMessage 创建")
        void createChatMessage() {
            ChatMessage message = ChatMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .sessionId("session-123")
                    .role("user")
                    .content("Hello, AI!")
                    .build();

            assertNotNull(message);
            assertEquals("session-123", message.getSessionId());
            assertEquals("user", message.getRole());
            assertEquals("Hello, AI!", message.getContent());
        }

        @Test
        @DisplayName("ChatMessage - Assistant 消息")
        void chatMessage_AssistantMessage() {
            ChatMessage message = ChatMessage.builder()
                    .sessionId("session-123")
                    .role("assistant")
                    .content("Hello, how can I help you?")
                    .build();

            assertEquals("assistant", message.getRole());
        }
    }

    @Nested
    @DisplayName("AuditLog 实体测试")
    class AuditLogEntityTests {

        @Test
        @DisplayName("AuditLog 创建")
        void createAuditLog() {
            AuditLog log = AuditLog.builder()
                    .id(UUID.randomUUID().toString())
                    .userId("user-123")
                    .username("testuser")
                    .action(com.clacjz.medical.rag.model.enums.AuditActionEnums.LOGIN)
                    .level(com.clacjz.medical.rag.model.enums.AuditLevelEnums.INFO)
                    .ipAddress("127.0.0.1")
                    .description("User logged in")
                    .build();

            assertNotNull(log);
            assertEquals("user-123", log.getUserId());
            assertEquals(com.clacjz.medical.rag.model.enums.AuditActionEnums.LOGIN, log.getAction());
            assertEquals(com.clacjz.medical.rag.model.enums.AuditLevelEnums.INFO, log.getLevel());
        }
    }
}
