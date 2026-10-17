package com.clacjz.medical.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.context.TenantContext;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.mapper.CollectionDocumentMapper;
import com.clacjz.medical.rag.mapper.CollectionMapper;
import com.clacjz.medical.rag.mapper.DepartmentMapper;
import com.clacjz.medical.rag.mapper.DocumentMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.model.config.CollectionConfig;
import com.clacjz.medical.rag.model.config.SectionTemplate;
import com.clacjz.medical.rag.model.dto.*;
import com.clacjz.medical.rag.model.entity.Collection;
import com.clacjz.medical.rag.model.entity.CollectionDocument;
import com.clacjz.medical.rag.model.entity.Department;
import com.clacjz.medical.rag.model.entity.Document;
import com.clacjz.medical.rag.model.enums.CollectionPermissionTypeEnums;
import com.clacjz.medical.rag.model.enums.CollectionStatus;
import com.clacjz.medical.rag.model.enums.CollectionType;
import com.clacjz.medical.rag.model.enums.DocType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionService {

    private final CollectionMapper collectionMapper;
    private final CollectionDocumentMapper collectionDocumentMapper;
    private final DepartmentMapper departmentMapper;
    private final UserMapper userMapper;
    private final DocumentMapper documentMapper;
    private final ObjectMapper objectMapper;
    private final CollectionPermissionService collectionPermissionService;
    private final VectorCrudService vectorCrudService;
    private final EsIndexService esIndexService;

    @Transactional(readOnly = true)
    public IPage<CollectionDetailDTO> getCollections(int page, int pageSize, String keyword,
                                                     CollectionType type, CollectionStatus status) {
        String departmentId = TenantContext.getDepartmentId();

        Page<Collection> mpPage = new Page<>(page + 1, pageSize);
        QueryWrapper<Collection> wrapper = new QueryWrapper<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like("name", keyword);
        }

        wrapper.isNull("deleted_at");

        if (type != null && departmentId != null) {
            wrapper.eq("department_id", departmentId).eq("type", type);
        } else if (status != null && departmentId != null) {
            wrapper.eq("department_id", departmentId).eq("status", status);
        } else if (departmentId != null) {
            wrapper.eq("department_id", departmentId).eq("status", CollectionStatus.ACTIVE);
        }

        wrapper.orderByDesc("created_at");

        IPage<Collection> collectionPage = collectionMapper.selectPage(mpPage, wrapper);

        // 批量查询部门和用户名（避免N+1查询）
        Map<String, String> deptNameCache = new HashMap<>();
        Map<String, String> ownerNameCache = new HashMap<>();
        Set<String> deptIds = collectionPage.getRecords().stream()
                .map(Collection::getDepartmentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> ownerIds = collectionPage.getRecords().stream()
                .map(Collection::getOwnerId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!deptIds.isEmpty()) {
            departmentMapper.selectBatchIds(deptIds).forEach(d -> deptNameCache.put(d.getId(), d.getName()));
        }
        if (!ownerIds.isEmpty()) {
            userMapper.selectBatchIds(ownerIds).forEach(u -> ownerNameCache.put(u.getId(), u.getDisplayName()));
        }

        return collectionPage.convert(c -> convertToDetailDTO(c, deptNameCache, ownerNameCache));
    }

    @Transactional(readOnly = true)
    public CollectionDetailDTO getCollectionById(String collectionId) {
        Collection collection = findActiveCollectionById(collectionId);
        validateCollectionAccess(collection);
        return convertToDetailDTO(collection);
    }

    @Transactional
    public CollectionDetailDTO createCollection(CreateCollectionRequest request) {
        String userId = TenantContext.getUserId();
        String departmentId = request.getDepartmentId() != null ?
                request.getDepartmentId() : TenantContext.getDepartmentId();

        if (departmentId == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "无法确定用户所属部门");
        }

        if (collectionMapper.existsByNameAndDeletedAtIsNull(request.getName())) {
            throw new BizException(ErrorCode.COLLECTION_ALREADY_EXISTS,
                    "Collection名称 [" + request.getName() + "] 已存在，请使用其他名称");
        }

        if (!TenantContext.isAdmin() && !departmentId.equals(TenantContext.getDepartmentId())) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "无权在其他部门创建Collection");
        }

        // 校验：MANUAL类型必须配置sectionTemplate（通过jsonTemplate或sectionTemplate提供）
        List<SectionTemplate> sectionTemplates = request.getSectionTemplate();
        if (request.getDocType() == DocType.MANUAL &&
            (sectionTemplates == null || sectionTemplates.isEmpty())) {
            // 如果没有sectionTemplate，尝试解析jsonTemplate
            if (request.getJsonTemplate() != null && !request.getJsonTemplate().isBlank()) {
                sectionTemplates = parseJsonTemplate(request.getJsonTemplate());
            }
            if (sectionTemplates == null || sectionTemplates.isEmpty()) {
                throw new BizException(ErrorCode.INVALID_PARAMETER,
                        "说明书类知识库必须配置字段模板（sectionTemplate）或jsonTemplate");
            }
        }

        Collection collection = Collection.builder()
                .name(request.getName())
                .description(request.getDescription())
                .type(request.getType() != null ? request.getType() : CollectionType.KNOWLEDGE_BASE)
                .docType(request.getDocType() != null ? request.getDocType() : DocType.NORMAL)
                .sectionTemplate(sectionTemplates)
                .config(request.getConfig() != null ? request.getConfig() : CollectionConfig.defaultConfig())
                .status(CollectionStatus.CREATING)
                .ownerId(userId)
                .departmentId(departmentId)
                .documentCount(0)
                .chunkCount(0L)
                .totalSize(0L)
                .build();

        collectionMapper.insert(collection);

        collection.setStatus(CollectionStatus.ACTIVE);
        collectionMapper.updateById(collection);

        log.info("Collection created: id={}, name={}, type={}, docType={}, ownerId={}, departmentId={}",
                collection.getId(), collection.getName(), collection.getType(), collection.getDocType(), userId, departmentId);

        // 设置知识库权限（如果创建时指定了权限类型）
        if (request.getPermissionType() != null && !request.getPermissionType().isBlank()) {
            try {
                CollectionPermissionTypeEnums permType = CollectionPermissionTypeEnums.valueOf(request.getPermissionType());
                UpdateCollectionPermissionRequest permRequest = UpdateCollectionPermissionRequest.builder()
                        .permissionType(permType)
                        .allowedUserIds(request.getAllowedUserIds())
                        .build();
                collectionPermissionService.updateCollectionPermission(collection.getId(), permRequest);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid permissionType: {}, skipping permission setup", request.getPermissionType());
            }
        }

        return convertToDetailDTO(collection);
    }

    @Transactional
    public CollectionDetailDTO updateCollection(String collectionId, UpdateCollectionRequest request) {
        Collection collection = findActiveCollectionById(collectionId);
        validateCollectionAccess(collection);

        if (!TenantContext.isAdmin() && !TenantContext.getUserId().equals(collection.getOwnerId())) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "只有Collection所有者或管理员可以修改");
        }

        if (request.getName() != null && !request.getName().equals(collection.getName())) {
            if (collectionMapper.existsByNameAndDeletedAtIsNull(request.getName())) {
                throw new BizException(ErrorCode.COLLECTION_ALREADY_EXISTS,
                        "Collection名称 [" + request.getName() + "] 已存在，请使用其他名称");
            }
            collection.setName(request.getName());
        }

        if (request.getDescription() != null) {
            collection.setDescription(request.getDescription());
        }

        if (request.getType() != null) {
            collection.setType(request.getType());
        }

        if (request.getStatus() != null) {
            if (!TenantContext.isAdmin() && request.getStatus() == CollectionStatus.DELETED) {
                throw new BizException(ErrorCode.PERMISSION_DENIED, "只有管理员可以删除Collection");
            }
            collection.setStatus(request.getStatus());
        }

        collectionMapper.updateById(collection);
        log.info("Collection updated: id={}", collectionId);

        return convertToDetailDTO(collection);
    }

    @Transactional
    public CollectionDetailDTO updateCollectionConfig(String collectionId, CollectionConfig config) {
        Collection collection = findActiveCollectionById(collectionId);
        validateCollectionAccess(collection);

        if (!TenantContext.isAdmin() && !TenantContext.getUserId().equals(collection.getOwnerId())) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "只有Collection所有者或管理员可以修改配置");
        }

        collection.setConfig(config);
        collectionMapper.updateById(collection);

        log.info("Collection config updated: id={}", collectionId);
        return convertToDetailDTO(collection);
    }

    @Transactional
    public void deleteCollection(String collectionId) {
        Collection collection = findActiveCollectionById(collectionId);
        validateCollectionAccess(collection);

        if (!TenantContext.isAdmin() && !TenantContext.getUserId().equals(collection.getOwnerId())) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "只有Collection所有者或管理员可以删除");
        }

        collection.softDelete();
        collectionMapper.updateById(collection);

        // 删除关联文档记录
        collectionDocumentMapper.deleteByCollectionId(collectionId);

        log.info("Collection soft-deleted in DB: id={}", collectionId);

        // 外部资源清理放在事务提交后执行，避免：
        // 1. 事务回滚时外部数据已删除无法恢复
        // 2. 外部调用失败导致事务回滚
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            cleanupExternalResources(collectionId);
                        }
                    });
        } else {
            // 非事务上下文（如单元测试），直接清理
            cleanupExternalResources(collectionId);
        }
    }

    /**
     * 清理外部资源（Qdrant向量、ES索引），在DB事务提交后异步执行。
     * 失败仅记录日志，不影响主流程。
     */
    private void cleanupExternalResources(String collectionId) {
        // 清理Qdrant向量数据
        try {
            vectorCrudService.deleteAllVectors(collectionId);
            log.info("Qdrant vectors deleted for collection: {}", collectionId);
        } catch (Exception e) {
            log.warn("Failed to delete Qdrant vectors for collection {}: {}", collectionId, e);
        }

        // 清理ES索引
        try {
            esIndexService.deleteCollectionIndex(collectionId);
            log.info("ES index deleted for collection: {}", collectionId);
        } catch (Exception e) {
            log.warn("Failed to delete ES index for collection {}: {}", collectionId, e);
        }

        log.info("Collection external cleanup completed: id={}", collectionId);
    }

    @Transactional
    public void addDocumentsToCollection(String collectionId, AddDocumentsToCollectionRequest request) {
        Collection collection = findActiveCollectionById(collectionId);
        validateCollectionAccess(collection);

        for (String documentId : request.getDocumentIds()) {
            // 校验文档是否存在
            Document doc = documentMapper.selectById(documentId);
            if (doc == null) {
                log.warn("Document {} not found, skip adding to collection {}", documentId, collectionId);
                continue;
            }
            if (Boolean.TRUE.equals(doc.getIsDeleted())) {
                log.warn("Document {} is deleted, skip adding to collection {}", documentId, collectionId);
                continue;
            }

            if (collectionDocumentMapper.existsByCollectionIdAndDocumentId(collectionId, documentId)) {
                log.warn("Document {} already exists in collection {}", documentId, collectionId);
                continue;
            }

            CollectionDocument cd = CollectionDocument.builder()
                    .collectionId(collectionId)
                    .documentId(documentId)
                    .departmentId(collection.getDepartmentId())
                    .addedBy(TenantContext.getUserId())
                    .processingStatus("PENDING")
                    .processingPriority(request.getProcessingPriority())
                    .build();

            collectionDocumentMapper.insert(cd);
            collection.incrementDocumentCount();
        }

        collectionMapper.updateById(collection);
        log.info("Documents added to collection: id={}, count={}", collectionId, request.getDocumentIds().size());
    }

    @Transactional
    public void removeDocumentFromCollection(String collectionId, String documentId) {
        Collection collection = findActiveCollectionById(collectionId);
        validateCollectionAccess(collection);

        CollectionDocument cd = collectionDocumentMapper.selectByCollectionIdAndDocumentId(collectionId, documentId);
        if (cd == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND, "文档不在该Collection中");
        }

        collectionDocumentMapper.deleteById(cd.getId());
        collection.decrementDocumentCount();
        collectionMapper.updateById(collection);

        log.info("Document removed from collection: collectionId={}, documentId={}", collectionId, documentId);
    }

    @Transactional
    public CollectionDetailDTO archiveCollection(String collectionId) {
        Collection collection = findActiveCollectionById(collectionId);
        validateCollectionAccess(collection);

        if (!TenantContext.isAdmin() && !TenantContext.getUserId().equals(collection.getOwnerId())) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "只有Collection所有者或管理员可以归档");
        }

        collection.setStatus(CollectionStatus.ARCHIVED);
        collectionMapper.updateById(collection);

        log.info("Collection archived: id={}", collectionId);
        return convertToDetailDTO(collection);
    }

    @Transactional
    public CollectionDetailDTO restoreCollection(String collectionId) {
        Collection collection = collectionMapper.selectById(collectionId);
        if (collection == null) {
            throw new BizException(ErrorCode.COLLECTION_NOT_FOUND);
        }

        if (!collection.isDeleted() && collection.getStatus() != CollectionStatus.ARCHIVED) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "该Collection未被归档，无需恢复");
        }

        validateCollectionAccess(collection);

        if (!TenantContext.isAdmin() && !TenantContext.getUserId().equals(collection.getOwnerId())) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "只有Collection所有者或管理员可以恢复");
        }

        collection.setStatus(CollectionStatus.ACTIVE);
        collectionMapper.updateById(collection);

        log.info("Collection restored: id={}", collectionId);
        return convertToDetailDTO(collection);
    }

    @Transactional(readOnly = true)
    public CollectionConfig getCollectionConfig(String collectionId) {
        Collection collection = findActiveCollectionById(collectionId);
        validateCollectionAccess(collection);
        return collection.getConfig();
    }

    @Transactional(readOnly = true)
    public CollectionStatistics getCollectionStatistics(String collectionId) {
        Collection collection = findActiveCollectionById(collectionId);
        validateCollectionAccess(collection);

        long queryCount = 0L;
        double avgLatency = 0.0;
        double p95Latency = 0.0;
        double errorRate = 0.0;
        int activeUsers = 0;

        return CollectionStatistics.builder()
                .totalQueries(queryCount)
                .avgLatencyMs(avgLatency)
                .p95LatencyMs(p95Latency)
                .errorRate(errorRate)
                .activeUsers(activeUsers)
                .build();
    }

    @Transactional(readOnly = true)
    public IPage<CollectionDocumentDTO> getCollectionDocuments(String collectionId, int page, int pageSize, String status) {
        Collection collection = findActiveCollectionById(collectionId);
        validateCollectionAccess(collection);

        // 使用DB层分页，JOIN documents表过滤已删除文档
        Page<CollectionDocument> mpPage = new Page<>(page + 1, pageSize);
        IPage<CollectionDocument> cdPage = collectionDocumentMapper.selectPageByCollectionIdWithFilter(
                mpPage, collectionId, status);
        return cdPage.convert(this::convertToDocumentDTO);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDocumentProcessStatus(String collectionId, String documentId) {
        Collection collection = findActiveCollectionById(collectionId);
        validateCollectionAccess(collection);

        CollectionDocument cd = collectionDocumentMapper.selectByCollectionIdAndDocumentId(collectionId, documentId);
        if (cd == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND, "文档不在该Collection中");
        }

        Document document = documentMapper.selectById(documentId);

        Map<String, Object> status = new HashMap<>();
        status.put("documentId", documentId);
        status.put("collectionId", collectionId);
        status.put("processingStatus", cd.getProcessingStatus());
        status.put("processingError", cd.getProcessingError());
        status.put("processedAt", cd.getProcessedAt());
        status.put("addedAt", cd.getAddedAt());

        if (document != null) {
            status.put("uploadStatus", document.getUploadStatus());
            status.put("parseStatus", document.getParseStatus());
            status.put("chunkStatus", document.getChunkStatus());
            status.put("chunkCount", document.getChunkCount());
            status.put("parseErrorMessage", document.getParseErrorMessage());
            status.put("title", document.getTitle());
            status.put("fileType", document.getFileType());
            status.put("fileSize", document.getFileSize());
        }

        return status;
    }

    private Collection findActiveCollectionById(String collectionId) {
        Collection collection = collectionMapper.selectById(collectionId);
        if (collection == null) {
            throw new BizException(ErrorCode.COLLECTION_NOT_FOUND);
        }

        if (collection.isDeleted()) {
            throw new BizException(ErrorCode.COLLECTION_NOT_FOUND, "Collection已被删除");
        }

        return collection;
    }

    private void validateCollectionAccess(Collection collection) {
        String departmentId = TenantContext.getDepartmentId();
        String userId = TenantContext.getUserId();

        if (TenantContext.isSystemAdmin()) {
            return;
        }

        if (TenantContext.isAdmin()) {
            if (departmentId != null && departmentId.equals(collection.getDepartmentId())) {
                return;
            }
            throw new BizException(ErrorCode.PERMISSION_DENIED, "部门管理员只能访问本部门的Collection");
        }

        if (userId.equals(collection.getOwnerId())) {
            return;
        }

        if (departmentId != null && departmentId.equals(collection.getDepartmentId())) {
            return;
        }

        throw new BizException(ErrorCode.PERMISSION_DENIED, "无权访问该Collection");
    }

    private CollectionDetailDTO convertToDetailDTO(Collection collection) {
        return convertToDetailDTO(collection, null, null);
    }

    private CollectionDetailDTO convertToDetailDTO(Collection collection, Map<String, String> deptNameCache, Map<String, String> ownerNameCache) {
        CollectionDetailDTO dto = CollectionDetailDTO.builder()
                .id(collection.getId())
                .name(collection.getName())
                .description(collection.getDescription())
                .type(collection.getType())
                .typeLabel(collection.getType() != null ? collection.getType().getLabel() : null)
                .config(collection.getConfig())
                .status(collection.getStatus())
                .statusLabel(collection.getStatus() != null ? collection.getStatus().getLabel() : null)
                .documentCount(collection.getDocumentCount())
                .chunkCount(collection.getChunkCount())
                .totalSize(collection.getTotalSize())
                .departmentId(collection.getDepartmentId())
                .ownerId(collection.getOwnerId())
                .createdAt(collection.getCreatedAt())
                .updatedAt(collection.getUpdatedAt())
                .build();

        if (collection.getDepartmentId() != null) {
            String deptName = resolveName(deptNameCache, collection.getDepartmentId(),
                    () -> departmentMapper.selectById(collection.getDepartmentId()), d -> d.getName());
            if (deptName != null) {
                dto.setDepartmentName(deptName);
            }
        }

        if (collection.getOwnerId() != null) {
            String ownerName = resolveName(ownerNameCache, collection.getOwnerId(),
                    () -> userMapper.selectById(collection.getOwnerId()), u -> u.getDisplayName());
            if (ownerName != null) {
                dto.setOwnerName(ownerName);
            }
        }

        return dto;
    }

    /**
     * 通用名称解析：先查缓存，缓存未命中则查询DB并提取名称。
     * 消除 dept/owner 名称解析的重复代码。
     */
    private <T> String resolveName(Map<String, String> cache, String id,
                                   java.util.function.Supplier<T> dbLoader,
                                   java.util.function.Function<T, String> nameExtractor) {
        if (cache != null) {
            String cached = cache.get(id);
            if (cached != null) {
                return cached;
            }
        }
        T entity = dbLoader.get();
        return entity != null ? nameExtractor.apply(entity) : null;
    }

    private CollectionDocumentDTO convertToDocumentDTO(CollectionDocument cd) {
        Document doc = documentMapper.selectById(cd.getDocumentId());
        return CollectionDocumentDTO.builder()
                .id(cd.getId())
                .collectionId(cd.getCollectionId())
                .documentId(cd.getDocumentId())
                .documentName(doc != null ? doc.getFileName() : null)
                .documentType(doc != null ? doc.getFileType() : null)
                .departmentId(cd.getDepartmentId())
                .addedBy(cd.getAddedBy())
                .processingStatus(cd.getProcessingStatus())
                .processingPriority(cd.getProcessingPriority())
                .processingError(cd.getProcessingError())
                .processedAt(cd.getProcessedAt())
                .addedAt(cd.getAddedAt())
                .build();
    }

    /**
     * 解析JSON模板字符串为SectionTemplate列表
     */
    private List<SectionTemplate> parseJsonTemplate(String jsonTemplate) {
        if (jsonTemplate == null || jsonTemplate.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonTemplate, new TypeReference<List<SectionTemplate>>() {});
        } catch (Exception e) {
            log.warn("解析JSON模板失败: {}: {}", jsonTemplate, e);
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    "JSON模板格式错误，请检查格式：" + e.getMessage());
        }
    }
}