package com.clacjz.medical.rag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@TableName(value = "chat_sessions", autoResultMap = true)
@Data
@NoArgsConstructor
public class ChatSession {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("title")
    private String title;

    @TableField("user_id")
    private String userId;

    @TableField(exist = false)
    private User user;

    @TableField("department_id")
    private String departmentId;

    @TableField(exist = false)
    private Department department;

    @TableField(value = "collection_ids", typeHandler = com.clacjz.medical.rag.mapper.JsonbTypeHandler.class)
    private String collectionIdsJson;

    @TableField("status")
    private String status = "ACTIVE";

    @TableField("message_count")
    private Integer messageCount = 0;

    @TableField(exist = false)
    private List<ChatMessage> messages = new ArrayList<>();

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("last_message_at")
    private LocalDateTime lastMessageAt;

    // 瞬时字段
    @TableField(exist = false)
    private List<String> collectionIds;

    public List<String> getCollectionIds() {
        if (collectionIds == null && collectionIdsJson != null && !collectionIdsJson.isBlank()) {
            try {
                collectionIds = objectMapper.readValue(collectionIdsJson, new TypeReference<List<String>>() {});
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse collectionIds JSON", e);
            }
        }
        if (collectionIds == null) {
            collectionIds = new ArrayList<>();
        }
        return collectionIds;
    }

    public void setCollectionIds(List<String> collectionIds) {
        this.collectionIds = collectionIds;
        if (collectionIds != null) {
            try {
                this.collectionIdsJson = objectMapper.writeValueAsString(collectionIds);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize collectionIds to JSON", e);
            }
        }
    }

    public void incrementMessageCount() {
        this.messageCount = (this.messageCount == null ? 0 : this.messageCount) + 1;
        this.lastMessageAt = LocalDateTime.now();
    }

    public void decrementMessageCount() {
        this.messageCount = (this.messageCount == null || this.messageCount <= 0) ? 0 : this.messageCount - 1;
    }

    public void addCollectionId(String collectionId) {
        if (getCollectionIds() == null) {
            this.collectionIds = new ArrayList<>();
        }
        if (!this.collectionIds.contains(collectionId)) {
            this.collectionIds.add(collectionId);
        }
    }

    public void removeCollectionId(String collectionId) {
        if (this.collectionIds != null) {
            this.collectionIds.remove(collectionId);
        }
    }

    public static ChatSessionBuilder builder() {
        return new ChatSessionBuilder();
    }

    public static class ChatSessionBuilder {
        private String id;
        private String title;
        private String userId;
        private User user;
        private String departmentId;
        private Department department;
        private String status = "ACTIVE";
        private Integer messageCount = 0;
        private List<ChatMessage> messages = new ArrayList<>();
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime lastMessageAt;
        private List<String> collectionIds;

        ChatSessionBuilder() {}

        public ChatSessionBuilder id(String id) { this.id = id; return this; }
        public ChatSessionBuilder title(String title) { this.title = title; return this; }
        public ChatSessionBuilder userId(String userId) { this.userId = userId; return this; }
        public ChatSessionBuilder user(User user) { this.user = user; return this; }
        public ChatSessionBuilder departmentId(String departmentId) { this.departmentId = departmentId; return this; }
        public ChatSessionBuilder department(Department department) { this.department = department; return this; }
        public ChatSessionBuilder status(String status) { this.status = status; return this; }
        public ChatSessionBuilder messageCount(Integer messageCount) { this.messageCount = messageCount; return this; }
        public ChatSessionBuilder messages(List<ChatMessage> messages) { this.messages = messages; return this; }
        public ChatSessionBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public ChatSessionBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }
        public ChatSessionBuilder lastMessageAt(LocalDateTime lastMessageAt) { this.lastMessageAt = lastMessageAt; return this; }
        public ChatSessionBuilder collectionIds(List<String> collectionIds) { this.collectionIds = collectionIds; return this; }

        public ChatSession build() {
            ChatSession session = new ChatSession();
            session.id = this.id;
            session.title = this.title;
            session.userId = this.userId;
            session.user = this.user;
            session.departmentId = this.departmentId;
            session.department = this.department;
            session.status = this.status;
            session.messageCount = this.messageCount;
            session.messages = this.messages;
            session.createdAt = this.createdAt;
            session.updatedAt = this.updatedAt;
            session.lastMessageAt = this.lastMessageAt;
            session.setCollectionIds(this.collectionIds);
            return session;
        }
    }
}
