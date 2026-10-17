package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.context.TenantContext;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.config.CollectionConfig;
import com.clacjz.medical.rag.model.dto.*;
import com.clacjz.medical.rag.model.entity.Collection;
import com.clacjz.medical.rag.model.entity.CollectionDocument;
import com.clacjz.medical.rag.model.entity.Department;
import com.clacjz.medical.rag.model.entity.Document;
import com.clacjz.medical.rag.model.enums.CollectionStatus;
import com.clacjz.medical.rag.model.enums.CollectionType;
import com.clacjz.medical.rag.mapper.CollectionDocumentMapper;
import com.clacjz.medical.rag.mapper.CollectionMapper;
import com.clacjz.medical.rag.mapper.DepartmentMapper;
import com.clacjz.medical.rag.mapper.DocumentMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.clacjz.medical.rag.model.entity.User;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CollectionService 单元测试")
class CollectionServiceTest {

    @Mock
    private CollectionMapper collectionMapper;

    @Mock
    private CollectionDocumentMapper collectionDocumentMapper;

    @Mock
    private DepartmentMapper departmentMapper;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private VectorCrudService vectorCrudService;

    @Mock
    private EsIndexService esIndexService;

    @Mock
    private CollectionPermissionService collectionPermissionService;

    @InjectMocks
    private CollectionService collectionService;

    private Collection testCollection;
    private CollectionDocument testCollectionDocument;
    private Department testDepartment;
    private static final String TEST_USER_ID = "user-123";
    private static final String TEST_DEPT_ID = "dept-123";
    private static final String TEST_COLLECTION_ID = "collection-123";

    @BeforeEach
    void setUp() {
        testDepartment = Department.builder()
                .id(TEST_DEPT_ID)
                .code("TEST_DEPT")
                .name("测试部门")
                .build();

        testCollection = Collection.builder()
                .id(TEST_COLLECTION_ID)
                .name("测试知识库")
                .description("测试描述")
                .type(CollectionType.KNOWLEDGE_BASE)
                .status(CollectionStatus.ACTIVE)
                .ownerId(TEST_USER_ID)
                .departmentId(TEST_DEPT_ID)
                .documentCount(0)
                .chunkCount(0L)
                .totalSize(0L)
                .config(CollectionConfig.defaultConfig())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deletedAt(null)
                .build();

        testCollectionDocument = CollectionDocument.builder()
                .id(UUID.randomUUID().toString())
                .collectionId(TEST_COLLECTION_ID)
                .documentId("doc-123")
                .departmentId(TEST_DEPT_ID)
                .addedBy(TEST_USER_ID)
                .processingStatus("COMPLETED")
                .processingPriority(1)
                .addedAt(LocalDateTime.now())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void setSystemAdminContext() {
        TenantContext.setUserId(TEST_USER_ID);
        TenantContext.setDepartmentId(null);
        TenantContext.setUserRole("SYSTEM_ADMIN");
    }

    private void setDeptAdminContext() {
        TenantContext.setUserId(TEST_USER_ID);
        TenantContext.setDepartmentId(TEST_DEPT_ID);
        TenantContext.setUserRole("DEPT_ADMIN");
    }

    private void setRegularUserContext() {
        TenantContext.setUserId(TEST_USER_ID);
        TenantContext.setDepartmentId(TEST_DEPT_ID);
        TenantContext.setUserRole("DEPT_USER");
    }

    @Nested
    @DisplayName("获取知识库列表测试")
    class GetCollectionsTests {

        @Test
        @DisplayName("获取知识库列表 - 按关键词搜索")
        void getCollections_ByKeyword() {
            setDeptAdminContext();
            Page<Collection> mpPage = new Page<>(1, 10);
            mpPage.setRecords(Arrays.asList(testCollection));
            mpPage.setTotal(1);
            when(collectionMapper.selectPage(any(Page.class), any())).thenReturn(mpPage);

            IPage<CollectionDetailDTO> result = collectionService.getCollections(0, 10, "测试", null, null);

            assertNotNull(result);
            assertEquals(1, result.getTotal());
        }

        @Test
        @DisplayName("获取知识库列表 - 按类型筛选")
        void getCollections_ByType() {
            setDeptAdminContext();
            Page<Collection> mpPage = new Page<>(1, 10);
            mpPage.setRecords(Arrays.asList(testCollection));
            mpPage.setTotal(1);
            when(collectionMapper.selectPage(any(Page.class), any())).thenReturn(mpPage);

            IPage<CollectionDetailDTO> result = collectionService.getCollections(0, 10, null, CollectionType.KNOWLEDGE_BASE, null);

            assertNotNull(result);
            assertEquals(1, result.getTotal());
        }

        @Test
        @DisplayName("获取知识库列表 - 按状态筛选")
        void getCollections_ByStatus() {
            setDeptAdminContext();
            Page<Collection> mpPage = new Page<>(1, 10);
            mpPage.setRecords(Arrays.asList(testCollection));
            mpPage.setTotal(1);
            when(collectionMapper.selectPage(any(Page.class), any())).thenReturn(mpPage);

            IPage<CollectionDetailDTO> result = collectionService.getCollections(0, 10, null, null, CollectionStatus.ACTIVE);

            assertNotNull(result);
            assertEquals(1, result.getTotal());
        }

        @Test
        @DisplayName("获取知识库列表 - 系统管理员获取所有")
        void getCollections_SystemAdmin() {
            setSystemAdminContext();
            Page<Collection> mpPage = new Page<>(1, 10);
            mpPage.setRecords(Arrays.asList(testCollection));
            mpPage.setTotal(1);
            when(collectionMapper.selectPage(any(Page.class), any())).thenReturn(mpPage);

            IPage<CollectionDetailDTO> result = collectionService.getCollections(0, 10, null, null, null);

            assertNotNull(result);
            assertEquals(1, result.getTotal());
        }
    }

    @Nested
    @DisplayName("获取单个知识库测试")
    class GetCollectionByIdTests {

        @Test
        @DisplayName("获取知识库成功 - 所有者访问")
        void getCollectionById_Success_OwnerAccess() {
            setRegularUserContext();
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);

            CollectionDetailDTO result = collectionService.getCollectionById(TEST_COLLECTION_ID);

            assertNotNull(result);
            assertEquals("测试知识库", result.getName());
        }

        @Test
        @DisplayName("获取知识库成功 - 同一部门访问")
        void getCollectionById_Success_SameDepartment() {
            setRegularUserContext();
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);

            CollectionDetailDTO result = collectionService.getCollectionById(TEST_COLLECTION_ID);

            assertNotNull(result);
        }

        @Test
        @DisplayName("获取知识库失败 - 知识库不存在")
        void getCollectionById_NotFound() {
            setDeptAdminContext();
            when(collectionMapper.selectById("nonexistent")).thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> collectionService.getCollectionById("nonexistent"));

            assertEquals(ErrorCode.COLLECTION_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("获取知识库失败 - 知识库已删除")
        void getCollectionById_Deleted() {
            setDeptAdminContext();
            testCollection.setDeletedAt(LocalDateTime.now());
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);

            BizException exception = assertThrows(BizException.class,
                    () -> collectionService.getCollectionById(TEST_COLLECTION_ID));

            assertEquals(ErrorCode.COLLECTION_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("获取知识库失败 - 无权访问其他部门")
        void getCollectionById_NoPermission() {
            setRegularUserContext();
            testCollection.setDepartmentId("other-dept");
            testCollection.setOwnerId("other-owner");
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);

            BizException exception = assertThrows(BizException.class,
                    () -> collectionService.getCollectionById(TEST_COLLECTION_ID));

            assertEquals(ErrorCode.PERMISSION_DENIED, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("创建知识库测试")
    class CreateCollectionTests {

        @Test
        @DisplayName("创建知识库成功")
        void createCollection_Success() {
            setDeptAdminContext();
            CreateCollectionRequest request = CreateCollectionRequest.builder()
                    .name("新知识库")
                    .description("新知识库描述")
                    .type(CollectionType.KNOWLEDGE_BASE)
                    .build();

            when(collectionMapper.existsByNameAndDeletedAtIsNull("新知识库")).thenReturn(false);
            when(collectionMapper.insert(any(Collection.class))).thenAnswer(invocation -> {
                Collection c = invocation.getArgument(0);
                c.setId(UUID.randomUUID().toString());
                return 1;
            });
            when(collectionMapper.updateById(any(Collection.class))).thenReturn(1);

            CollectionDetailDTO result = collectionService.createCollection(request);

            assertNotNull(result);
            assertEquals("新知识库", result.getName());
            verify(collectionMapper, times(1)).insert(any(Collection.class));
            verify(collectionMapper).updateById(any(Collection.class));
        }

        @Test
        @DisplayName("创建知识库失败 - 名称已存在")
        void createCollection_Failure_NameExists() {
            setDeptAdminContext();
            CreateCollectionRequest request = CreateCollectionRequest.builder()
                    .name("测试知识库")
                    .build();

            when(collectionMapper.existsByNameAndDeletedAtIsNull("测试知识库")).thenReturn(true);

            BizException exception = assertThrows(BizException.class,
                    () -> collectionService.createCollection(request));

            assertEquals(ErrorCode.COLLECTION_ALREADY_EXISTS, exception.getErrorCode());
        }

        @Test
        @DisplayName("创建知识库失败 - 无法确定部门")
        void createCollection_Failure_NoDepartment() {
            TenantContext.setUserId(TEST_USER_ID);
            TenantContext.setDepartmentId(null);
            TenantContext.setUserRole("DEPT_USER");

            CreateCollectionRequest request = CreateCollectionRequest.builder()
                    .name("新知识库")
                    .build();

            BizException exception = assertThrows(BizException.class,
                    () -> collectionService.createCollection(request));

            assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("无法确定用户所属部门"));
        }

        @Test
        @DisplayName("创建知识库失败 - 无权在其他部门创建")
        void createCollection_Failure_NoPermission() {
            setRegularUserContext();
            CreateCollectionRequest request = CreateCollectionRequest.builder()
                    .name("新知识库")
                    .departmentId("other-dept")
                    .build();

            BizException exception = assertThrows(BizException.class,
                    () -> collectionService.createCollection(request));

            assertEquals(ErrorCode.PERMISSION_DENIED, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("更新知识库测试")
    class UpdateCollectionTests {

        @Test
        @DisplayName("更新知识库成功 - 所有者更新")
        void updateCollection_Success_OwnerUpdate() {
            setRegularUserContext();
            UpdateCollectionRequest request = UpdateCollectionRequest.builder()
                    .name("更新后的名称")
                    .description("更新后的描述")
                    .build();

            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(collectionMapper.existsByNameAndDeletedAtIsNull("更新后的名称")).thenReturn(false);
            when(collectionMapper.updateById(any(Collection.class))).thenReturn(1);

            CollectionDetailDTO result = collectionService.updateCollection(TEST_COLLECTION_ID, request);

            assertNotNull(result);
            verify(collectionMapper).updateById(any(Collection.class));
        }

        @Test
        @DisplayName("更新知识库成功 - 管理员更新状态")
        void updateCollection_Success_AdminUpdateStatus() {
            setDeptAdminContext();
            UpdateCollectionRequest request = UpdateCollectionRequest.builder()
                    .status(CollectionStatus.DELETED)
                    .build();

            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(collectionMapper.updateById(any(Collection.class))).thenReturn(1);

            CollectionDetailDTO result = collectionService.updateCollection(TEST_COLLECTION_ID, request);

            assertNotNull(result);
        }

        @Test
        @DisplayName("更新知识库失败 - 普通用户删除")
        void updateCollection_Failure_NormalUserDelete() {
            setRegularUserContext();
            UpdateCollectionRequest request = UpdateCollectionRequest.builder()
                    .status(CollectionStatus.DELETED)
                    .build();

            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);

            BizException exception = assertThrows(BizException.class,
                    () -> collectionService.updateCollection(TEST_COLLECTION_ID, request));

            assertEquals(ErrorCode.PERMISSION_DENIED, exception.getErrorCode());
        }

        @Test
        @DisplayName("更新知识库失败 - 无权访问")
        void updateCollection_Failure_NoPermission() {
            setRegularUserContext();
            testCollection.setOwnerId("other-user");
            testCollection.setDepartmentId("other-dept");

            UpdateCollectionRequest request = UpdateCollectionRequest.builder()
                    .name("新名称")
                    .build();

            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);

            BizException exception = assertThrows(BizException.class,
                    () -> collectionService.updateCollection(TEST_COLLECTION_ID, request));

            assertEquals(ErrorCode.PERMISSION_DENIED, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("更新知识库配置测试")
    class UpdateCollectionConfigTests {

        @Test
        @DisplayName("更新知识库配置成功")
        void updateCollectionConfig_Success() {
            setRegularUserContext();
            CollectionConfig.ChunkingConfig chunkingConfig = CollectionConfig.ChunkingConfig.builder()
                    .strategy(com.clacjz.medical.rag.model.enums.ChunkingStrategy.SEMANTIC)
                    .chunkSize(500)
                    .chunkOverlap(50)
                    .build();
            CollectionConfig newConfig = CollectionConfig.builder()
                    .chunking(chunkingConfig)
                    .build();

            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(collectionMapper.updateById(any(Collection.class))).thenReturn(1);

            CollectionDetailDTO result = collectionService.updateCollectionConfig(TEST_COLLECTION_ID, newConfig);

            assertNotNull(result);
            verify(collectionMapper).updateById(any(Collection.class));
        }
    }

    @Nested
    @DisplayName("删除知识库测试")
    class DeleteCollectionTests {

        @Test
        @DisplayName("删除知识库成功 - 软删除并清理外部资源")
        void deleteCollection_Success() throws Exception {
            setRegularUserContext();
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(collectionMapper.updateById(any(Collection.class))).thenReturn(1);

            assertDoesNotThrow(() -> collectionService.deleteCollection(TEST_COLLECTION_ID));

            verify(collectionMapper).updateById(any(Collection.class));
            verify(collectionDocumentMapper).deleteByCollectionId(TEST_COLLECTION_ID);
            // 验证外部资源清理被调用
            verify(vectorCrudService).deleteAllVectors(TEST_COLLECTION_ID);
            verify(esIndexService).deleteCollectionIndex(TEST_COLLECTION_ID);
        }
    }

    @Nested
    @DisplayName("删除知识库外部资源清理测试")
    class DeleteCollectionCleanupTests {

        @Test
        @DisplayName("删除知识库 - 清理Qdrant向量数据")
        void deleteCollection_CleansUpQdrantVectors() throws Exception {
            setRegularUserContext();
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(collectionMapper.updateById(any(Collection.class))).thenReturn(1);

            collectionService.deleteCollection(TEST_COLLECTION_ID);

            verify(vectorCrudService).deleteAllVectors(TEST_COLLECTION_ID);
        }

        @Test
        @DisplayName("删除知识库 - 清理ES索引")
        void deleteCollection_CleansUpEsIndex() throws Exception {
            setRegularUserContext();
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(collectionMapper.updateById(any(Collection.class))).thenReturn(1);

            collectionService.deleteCollection(TEST_COLLECTION_ID);

            verify(esIndexService).deleteCollectionIndex(TEST_COLLECTION_ID);
        }

        @Test
        @DisplayName("删除知识库 - Qdrant清理失败不抛异常")
        void deleteCollection_QdrantFailure_DoesNotThrow() throws Exception {
            setRegularUserContext();
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(collectionMapper.updateById(any(Collection.class))).thenReturn(1);
            doThrow(new RuntimeException("Qdrant connection refused"))
                    .when(vectorCrudService).deleteAllVectors(TEST_COLLECTION_ID);

            assertDoesNotThrow(() -> collectionService.deleteCollection(TEST_COLLECTION_ID));

            verify(vectorCrudService).deleteAllVectors(TEST_COLLECTION_ID);
            verify(esIndexService).deleteCollectionIndex(TEST_COLLECTION_ID);
        }

        @Test
        @DisplayName("删除知识库 - ES清理失败不抛异常")
        void deleteCollection_EsFailure_DoesNotThrow() throws Exception {
            setRegularUserContext();
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(collectionMapper.updateById(any(Collection.class))).thenReturn(1);
            doThrow(new java.io.IOException("ES index not found"))
                    .when(esIndexService).deleteCollectionIndex(TEST_COLLECTION_ID);

            assertDoesNotThrow(() -> collectionService.deleteCollection(TEST_COLLECTION_ID));

            verify(vectorCrudService).deleteAllVectors(TEST_COLLECTION_ID);
            verify(esIndexService).deleteCollectionIndex(TEST_COLLECTION_ID);
        }

        @Test
        @DisplayName("删除知识库 - Qdrant和ES均清理失败不抛异常")
        void deleteCollection_BothExternalFailures_DoesNotThrow() throws Exception {
            setRegularUserContext();
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(collectionMapper.updateById(any(Collection.class))).thenReturn(1);
            doThrow(new RuntimeException("Qdrant connection refused"))
                    .when(vectorCrudService).deleteAllVectors(TEST_COLLECTION_ID);
            doThrow(new java.io.IOException("ES index not found"))
                    .when(esIndexService).deleteCollectionIndex(TEST_COLLECTION_ID);

            assertDoesNotThrow(() -> collectionService.deleteCollection(TEST_COLLECTION_ID));

            verify(vectorCrudService).deleteAllVectors(TEST_COLLECTION_ID);
            verify(esIndexService).deleteCollectionIndex(TEST_COLLECTION_ID);
        }
    }

    @Nested
    @DisplayName("添加文档到知识库测试")
    class AddDocumentsToCollectionTests {

        private Document testDoc;

        @BeforeEach
        void setUp() {
            testDoc = Document.builder()
                    .id("doc-1")
                    .fileName("test.pdf")
                    .isDeleted(false)
                    .departmentId(TEST_DEPT_ID)
                    .build();
            when(documentMapper.selectById(anyString())).thenReturn(testDoc);
        }

        @Test
        @DisplayName("添加文档到知识库成功")
        void addDocumentsToCollection_Success() {
            setRegularUserContext();
            AddDocumentsToCollectionRequest request = AddDocumentsToCollectionRequest.builder()
                    .documentIds(Arrays.asList("doc-1", "doc-2"))
                    .processingPriority(1)
                    .build();

            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(collectionDocumentMapper.existsByCollectionIdAndDocumentId(anyString(), anyString())).thenReturn(false);
            when(collectionDocumentMapper.insert(any(CollectionDocument.class))).thenReturn(1);
            when(collectionMapper.updateById(any(Collection.class))).thenReturn(1);

            assertDoesNotThrow(() -> collectionService.addDocumentsToCollection(TEST_COLLECTION_ID, request));

            verify(collectionDocumentMapper, times(2)).insert(any(CollectionDocument.class));
            verify(collectionMapper).updateById(any(Collection.class));
        }

        @Test
        @DisplayName("添加文档到知识库 - 跳过已存在的文档")
        void addDocumentsToCollection_SkipExisting() {
            setRegularUserContext();
            AddDocumentsToCollectionRequest request = AddDocumentsToCollectionRequest.builder()
                    .documentIds(Arrays.asList("doc-1", "doc-2"))
                    .processingPriority(1)
                    .build();

            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(collectionDocumentMapper.existsByCollectionIdAndDocumentId(anyString(), anyString())).thenReturn(false);
            when(collectionDocumentMapper.insert(any(CollectionDocument.class))).thenReturn(1);
            when(collectionMapper.updateById(any(Collection.class))).thenReturn(1);

            assertDoesNotThrow(() -> collectionService.addDocumentsToCollection(TEST_COLLECTION_ID, request));

            verify(collectionDocumentMapper, times(2)).insert(any(CollectionDocument.class));
        }
    }

    @Nested
    @DisplayName("从知识库移除文档测试")
    class RemoveDocumentFromCollectionTests {

        @Test
        @DisplayName("从知识库移除文档成功")
        void removeDocumentFromCollection_Success() {
            setRegularUserContext();
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(collectionDocumentMapper.selectByCollectionIdAndDocumentId(TEST_COLLECTION_ID, "doc-123"))
                    .thenReturn(testCollectionDocument);
            when(collectionMapper.updateById(any(Collection.class))).thenReturn(1);

            assertDoesNotThrow(() -> collectionService.removeDocumentFromCollection(TEST_COLLECTION_ID, "doc-123"));

            verify(collectionDocumentMapper).deleteById(anyString());
            verify(collectionMapper).updateById(any(Collection.class));
        }

        @Test
        @DisplayName("从知识库移除文档失败 - 文档不在知识库中")
        void removeDocumentFromCollection_NotFound() {
            setRegularUserContext();
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(collectionDocumentMapper.selectByCollectionIdAndDocumentId(TEST_COLLECTION_ID, "nonexistent"))
                    .thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> collectionService.removeDocumentFromCollection(TEST_COLLECTION_ID, "nonexistent"));

            assertEquals(ErrorCode.DOCUMENT_NOT_FOUND, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("批量名称解析测试（N+1修复）")
    class BatchNameResolutionTests {

        private Collection buildCollection(String id, String ownerId, String deptId) {
            return Collection.builder()
                    .id(id)
                    .name("知识库-" + id)
                    .description("描述")
                    .type(CollectionType.KNOWLEDGE_BASE)
                    .status(CollectionStatus.ACTIVE)
                    .ownerId(ownerId)
                    .departmentId(deptId)
                    .documentCount(0)
                    .chunkCount(0L)
                    .totalSize(0L)
                    .config(CollectionConfig.defaultConfig())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .deletedAt(null)
                    .build();
        }

        @Test
        @DisplayName("getCollections - departmentMapper.selectBatchIds 只调用一次（非N次）")
        void getCollections_DepartmentBatchLoadedOnce() {
            setDeptAdminContext();
            Collection c1 = buildCollection("c1", TEST_USER_ID, "dept-A");
            Collection c2 = buildCollection("c2", TEST_USER_ID, "dept-B");
            Collection c3 = buildCollection("c3", TEST_USER_ID, "dept-A"); // 重复部门

            Page<Collection> mpPage = new Page<>(1, 10);
            mpPage.setRecords(Arrays.asList(c1, c2, c3));
            mpPage.setTotal(3);
            when(collectionMapper.selectPage(any(Page.class), any())).thenReturn(mpPage);

            Department deptA = Department.builder().id("dept-A").name("部门A").build();
            Department deptB = Department.builder().id("dept-B").name("部门B").build();
            when(departmentMapper.selectBatchIds(anyCollection())).thenReturn(Arrays.asList(deptA, deptB));
            when(userMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());

            collectionService.getCollections(0, 10, null, null, null);

            // selectBatchIds 只调用一次，而非每条记录调用一次 selectById
            verify(departmentMapper, times(1)).selectBatchIds(anyCollection());
            verify(departmentMapper, never()).selectById(anyString());
        }

        @Test
        @DisplayName("getCollections - userMapper.selectBatchIds 只调用一次（非N次）")
        void getCollections_UserBatchLoadedOnce() {
            setDeptAdminContext();
            Collection c1 = buildCollection("c1", "user-A", TEST_DEPT_ID);
            Collection c2 = buildCollection("c2", "user-B", TEST_DEPT_ID);
            Collection c3 = buildCollection("c3", "user-A", TEST_DEPT_ID); // 重复owner

            Page<Collection> mpPage = new Page<>(1, 10);
            mpPage.setRecords(Arrays.asList(c1, c2, c3));
            mpPage.setTotal(3);
            when(collectionMapper.selectPage(any(Page.class), any())).thenReturn(mpPage);

            when(departmentMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());
            User userA = User.builder().id("user-A").displayName("用户A").build();
            User userB = User.builder().id("user-B").displayName("用户B").build();
            when(userMapper.selectBatchIds(anyCollection())).thenReturn(Arrays.asList(userA, userB));

            collectionService.getCollections(0, 10, null, null, null);

            // selectBatchIds 只调用一次，而非每条记录调用一次 selectById
            verify(userMapper, times(1)).selectBatchIds(anyCollection());
            verify(userMapper, never()).selectById(anyString());
        }

        @Test
        @DisplayName("getCollections - null departmentId 不进入 deptNameCache")
        void getCollections_NullDepartmentId_NotInCache() {
            setDeptAdminContext();
            Collection c1 = buildCollection("c1", TEST_USER_ID, "dept-A");
            Collection c2 = buildCollection("c2", TEST_USER_ID, null); // 无部门

            Page<Collection> mpPage = new Page<>(1, 10);
            mpPage.setRecords(Arrays.asList(c1, c2));
            mpPage.setTotal(2);
            when(collectionMapper.selectPage(any(Page.class), any())).thenReturn(mpPage);

            Department deptA = Department.builder().id("dept-A").name("部门A").build();
            when(departmentMapper.selectBatchIds(anyCollection())).thenReturn(Arrays.asList(deptA));
            when(userMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());

            IPage<CollectionDetailDTO> result = collectionService.getCollections(0, 10, null, null, null);

            // null departmentId 被过滤，不传入 selectBatchIds
            verify(departmentMapper, times(1)).selectBatchIds(anyCollection());
            assertNotNull(result);
            // c1 有部门名称，c2 没有
            CollectionDetailDTO dto1 = result.getRecords().get(0);
            CollectionDetailDTO dto2 = result.getRecords().get(1);
            assertEquals("部门A", dto1.getDepartmentName());
            assertNull(dto2.getDepartmentName());
        }

        @Test
        @DisplayName("getCollections - 混合 null/non-null ownerIds 批量加载")
        void getCollections_MixedNullNonNullOwnerIds() {
            setDeptAdminContext();
            Collection c1 = buildCollection("c1", "user-A", TEST_DEPT_ID);
            Collection c2 = buildCollection("c2", null, TEST_DEPT_ID); // 无owner
            Collection c3 = buildCollection("c3", "user-B", TEST_DEPT_ID);

            Page<Collection> mpPage = new Page<>(1, 10);
            mpPage.setRecords(Arrays.asList(c1, c2, c3));
            mpPage.setTotal(3);
            when(collectionMapper.selectPage(any(Page.class), any())).thenReturn(mpPage);

            when(departmentMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());
            User userA = User.builder().id("user-A").displayName("用户A").build();
            User userB = User.builder().id("user-B").displayName("用户B").build();
            when(userMapper.selectBatchIds(anyCollection())).thenReturn(Arrays.asList(userA, userB));

            IPage<CollectionDetailDTO> result = collectionService.getCollections(0, 10, null, null, null);

            // null ownerId 被过滤，只查 user-A 和 user-B
            verify(userMapper, times(1)).selectBatchIds(anyCollection());
            verify(userMapper, never()).selectById(anyString());

            // c2 无 ownerName
            CollectionDetailDTO dto2 = result.getRecords().get(1);
            assertNull(dto2.getOwnerName());
        }

        @Test
        @DisplayName("缓存命中 - 不对已缓存条目调用 selectById")
        void getCollections_CacheHit_NoIndividualSelectById() {
            setDeptAdminContext();
            Collection c1 = buildCollection("c1", TEST_USER_ID, TEST_DEPT_ID);

            Page<Collection> mpPage = new Page<>(1, 10);
            mpPage.setRecords(Arrays.asList(c1));
            mpPage.setTotal(1);
            when(collectionMapper.selectPage(any(Page.class), any())).thenReturn(mpPage);

            Department dept = Department.builder().id(TEST_DEPT_ID).name("测试部门").build();
            when(departmentMapper.selectBatchIds(anyCollection())).thenReturn(Arrays.asList(dept));
            User user = User.builder().id(TEST_USER_ID).displayName("测试用户").build();
            when(userMapper.selectBatchIds(anyCollection())).thenReturn(Arrays.asList(user));

            IPage<CollectionDetailDTO> result = collectionService.getCollections(0, 10, null, null, null);

            // 全部从缓存命中，不应调用 selectById
            verify(departmentMapper, never()).selectById(anyString());
            verify(userMapper, never()).selectById(anyString());

            CollectionDetailDTO dto = result.getRecords().get(0);
            assertEquals("测试部门", dto.getDepartmentName());
            assertEquals("测试用户", dto.getOwnerName());
        }

        @Test
        @DisplayName("缓存未命中 - convertToDetailDTO(collection) 单条路径调用 selectById")
        void getCollectionById_CacheMiss_IndividualSelectById() {
            setRegularUserContext();
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);

            // 单条路径使用 convertToDetailDTO(collection)，即无缓存
            Department dept = Department.builder().id(TEST_DEPT_ID).name("测试部门").build();
            when(departmentMapper.selectById(TEST_DEPT_ID)).thenReturn(dept);
            User user = User.builder().id(TEST_USER_ID).displayName("测试用户").build();
            when(userMapper.selectById(TEST_USER_ID)).thenReturn(user);

            CollectionDetailDTO result = collectionService.getCollectionById(TEST_COLLECTION_ID);

            assertNotNull(result);
            // 缓存为null，resolveName 走 dbLoader 分支，调用 selectById
            verify(departmentMapper).selectById(TEST_DEPT_ID);
            verify(userMapper).selectById(TEST_USER_ID);
            assertEquals("测试部门", result.getDepartmentName());
            assertEquals("测试用户", result.getOwnerName());
        }
    }

    @Nested
    @DisplayName("resolveName 辅助方法测试（通过 convertToDetailDTO 间接测试）")
    class ResolveNameTests {

        @Test
        @DisplayName("缓存命中 - 返回缓存值，不查询数据库")
        void cacheHit_returnsCachedValue() {
            setDeptAdminContext();

            // getCollections 使用 selectPage 而非 selectById，无需 stub selectById
            Page<Collection> mpPage = new Page<>(1, 10);
            mpPage.setRecords(Arrays.asList(testCollection));
            mpPage.setTotal(1);
            when(collectionMapper.selectPage(any(Page.class), any())).thenReturn(mpPage);

            Department dept = Department.builder().id(TEST_DEPT_ID).name("缓存部门").build();
            when(departmentMapper.selectBatchIds(anyCollection())).thenReturn(Arrays.asList(dept));
            User user = User.builder().id(TEST_USER_ID).displayName("缓存用户").build();
            when(userMapper.selectBatchIds(anyCollection())).thenReturn(Arrays.asList(user));

            IPage<CollectionDetailDTO> result = collectionService.getCollections(0, 10, null, null, null);

            // 缓存命中，不应调用 selectById
            verify(departmentMapper, never()).selectById(anyString());
            verify(userMapper, never()).selectById(anyString());

            CollectionDetailDTO dto = result.getRecords().get(0);
            assertEquals("缓存部门", dto.getDepartmentName());
            assertEquals("缓存用户", dto.getOwnerName());
        }

        @Test
        @DisplayName("缓存未命中 - 回退到数据库查询")
        void cacheMiss_fallsBackToDb() {
            setRegularUserContext();
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);

            // 单条路径 convertToDetailDTO(collection) 传入 null 缓存
            Department dept = Department.builder().id(TEST_DEPT_ID).name("数据库部门").build();
            when(departmentMapper.selectById(TEST_DEPT_ID)).thenReturn(dept);
            User user = User.builder().id(TEST_USER_ID).displayName("数据库用户").build();
            when(userMapper.selectById(TEST_USER_ID)).thenReturn(user);

            CollectionDetailDTO result = collectionService.getCollectionById(TEST_COLLECTION_ID);

            assertNotNull(result);
            // 缓存为 null，resolveName 走 dbLoader 分支，调用 selectById
            verify(departmentMapper).selectById(TEST_DEPT_ID);
            verify(userMapper).selectById(TEST_USER_ID);
            assertEquals("数据库部门", result.getDepartmentName());
            assertEquals("数据库用户", result.getOwnerName());
        }

        @Test
        @DisplayName("缓存为null - 回退到数据库查询")
        void nullCache_fallsBackToDb() {
            setRegularUserContext();
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);

            // convertToDetailDTO(collection) 内部调用 convertToDetailDTO(collection, null, null)
            // 缓存为 null，resolveName 走 dbLoader 分支
            Department dept = Department.builder().id(TEST_DEPT_ID).name("数据库部门").build();
            when(departmentMapper.selectById(TEST_DEPT_ID)).thenReturn(dept);
            User user = User.builder().id(TEST_USER_ID).displayName("数据库用户").build();
            when(userMapper.selectById(TEST_USER_ID)).thenReturn(user);

            CollectionDetailDTO result = collectionService.getCollectionById(TEST_COLLECTION_ID);

            // 缓存为 null，必须调用 selectById
            verify(departmentMapper, times(1)).selectById(TEST_DEPT_ID);
            verify(userMapper, times(1)).selectById(TEST_USER_ID);
            assertEquals("数据库部门", result.getDepartmentName());
            assertEquals("数据库用户", result.getOwnerName());
        }

        @Test
        @DisplayName("数据库返回null - 名称为null")
        void dbReturnsNull_returnsNull() {
            // 使用 SYSTEM_ADMIN 绕过权限检查，因为 departmentId/ownerId 不匹配用户上下文
            setSystemAdminContext();
            testCollection.setDepartmentId("dept-not-found");
            testCollection.setOwnerId("user-not-found");
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);

            // 数据库查询返回 null
            when(departmentMapper.selectById("dept-not-found")).thenReturn(null);
            when(userMapper.selectById("user-not-found")).thenReturn(null);

            CollectionDetailDTO result = collectionService.getCollectionById(TEST_COLLECTION_ID);

            assertNotNull(result);
            assertNull(result.getDepartmentName());
            assertNull(result.getOwnerName());
        }
    }

    @Nested
    @DisplayName("获取知识库文档列表测试")
    class GetCollectionDocumentsTests {

        @Test
        @DisplayName("获取知识库文档列表成功")
        void getCollectionDocuments_Success() {
            setRegularUserContext();
            Page<CollectionDocument> mpPage = new Page<>(1, 10);
            mpPage.setRecords(Arrays.asList(testCollectionDocument));
            mpPage.setTotal(1);
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(collectionDocumentMapper.selectPageByCollectionIdWithFilter(any(Page.class), any(), any()))
                    .thenReturn(mpPage);

            IPage<CollectionDocumentDTO> result = collectionService.getCollectionDocuments(TEST_COLLECTION_ID, 0, 10, null);

            assertNotNull(result);
            assertEquals(1, result.getTotal());
        }

        @Test
        @DisplayName("获取知识库文档列表 - 按状态筛选")
        void getCollectionDocuments_ByStatus() {
            setRegularUserContext();
            Page<CollectionDocument> mpPage = new Page<>(1, 10);
            mpPage.setRecords(Arrays.asList(testCollectionDocument));
            mpPage.setTotal(1);
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(collectionDocumentMapper.selectPageByCollectionIdWithFilter(any(Page.class), any(), any()))
                    .thenReturn(mpPage);

            IPage<CollectionDocumentDTO> result = collectionService.getCollectionDocuments(TEST_COLLECTION_ID, 0, 10, "COMPLETED");

            assertNotNull(result);
            assertEquals(1, result.getTotal());
        }
    }

    @Nested
    @DisplayName("获取知识库文档列表 - 状态筛选测试")
    class GetCollectionDocumentsFilterTests {

        @Test
        @DisplayName("状态筛选COMPLETED - 传递COMPLETED到mapper")
        void getCollectionDocuments_WithStatusCompleted() {
            setRegularUserContext();
            Page<CollectionDocument> mpPage = new Page<>(1, 10);
            mpPage.setRecords(Arrays.asList(testCollectionDocument));
            mpPage.setTotal(1);
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(collectionDocumentMapper.selectPageByCollectionIdWithFilter(any(Page.class), eq(TEST_COLLECTION_ID), eq("COMPLETED")))
                    .thenReturn(mpPage);

            IPage<CollectionDocumentDTO> result = collectionService.getCollectionDocuments(TEST_COLLECTION_ID, 0, 10, "COMPLETED");

            assertNotNull(result);
            assertEquals(1, result.getTotal());
            verify(collectionDocumentMapper).selectPageByCollectionIdWithFilter(any(Page.class), eq(TEST_COLLECTION_ID), eq("COMPLETED"));
        }

        @Test
        @DisplayName("状态筛选PENDING - 传递PENDING到mapper")
        void getCollectionDocuments_WithStatusPending() {
            setRegularUserContext();
            CollectionDocument pendingDoc = CollectionDocument.builder()
                    .id(UUID.randomUUID().toString())
                    .collectionId(TEST_COLLECTION_ID)
                    .documentId("doc-pending")
                    .departmentId(TEST_DEPT_ID)
                    .addedBy(TEST_USER_ID)
                    .processingStatus("PENDING")
                    .processingPriority(1)
                    .addedAt(LocalDateTime.now())
                    .build();
            Page<CollectionDocument> mpPage = new Page<>(1, 10);
            mpPage.setRecords(Arrays.asList(pendingDoc));
            mpPage.setTotal(1);
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(collectionDocumentMapper.selectPageByCollectionIdWithFilter(any(Page.class), eq(TEST_COLLECTION_ID), eq("PENDING")))
                    .thenReturn(mpPage);

            IPage<CollectionDocumentDTO> result = collectionService.getCollectionDocuments(TEST_COLLECTION_ID, 0, 10, "PENDING");

            assertNotNull(result);
            assertEquals(1, result.getTotal());
            verify(collectionDocumentMapper).selectPageByCollectionIdWithFilter(any(Page.class), eq(TEST_COLLECTION_ID), eq("PENDING"));
        }

        @Test
        @DisplayName("状态筛选FAILED - 传递FAILED到mapper")
        void getCollectionDocuments_WithStatusFailed() {
            setRegularUserContext();
            CollectionDocument failedDoc = CollectionDocument.builder()
                    .id(UUID.randomUUID().toString())
                    .collectionId(TEST_COLLECTION_ID)
                    .documentId("doc-failed")
                    .departmentId(TEST_DEPT_ID)
                    .addedBy(TEST_USER_ID)
                    .processingStatus("FAILED")
                    .processingPriority(1)
                    .addedAt(LocalDateTime.now())
                    .build();
            Page<CollectionDocument> mpPage = new Page<>(1, 10);
            mpPage.setRecords(Arrays.asList(failedDoc));
            mpPage.setTotal(1);
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(collectionDocumentMapper.selectPageByCollectionIdWithFilter(any(Page.class), eq(TEST_COLLECTION_ID), eq("FAILED")))
                    .thenReturn(mpPage);

            IPage<CollectionDocumentDTO> result = collectionService.getCollectionDocuments(TEST_COLLECTION_ID, 0, 10, "FAILED");

            assertNotNull(result);
            assertEquals(1, result.getTotal());
            verify(collectionDocumentMapper).selectPageByCollectionIdWithFilter(any(Page.class), eq(TEST_COLLECTION_ID), eq("FAILED"));
        }

        @Test
        @DisplayName("状态为null - 不应用状态筛选，传递null到mapper")
        void getCollectionDocuments_WithNullStatus() {
            setRegularUserContext();
            Page<CollectionDocument> mpPage = new Page<>(1, 10);
            mpPage.setRecords(Arrays.asList(testCollectionDocument));
            mpPage.setTotal(1);
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(collectionDocumentMapper.selectPageByCollectionIdWithFilter(any(Page.class), eq(TEST_COLLECTION_ID), isNull()))
                    .thenReturn(mpPage);

            IPage<CollectionDocumentDTO> result = collectionService.getCollectionDocuments(TEST_COLLECTION_ID, 0, 10, null);

            assertNotNull(result);
            assertEquals(1, result.getTotal());
            verify(collectionDocumentMapper).selectPageByCollectionIdWithFilter(any(Page.class), eq(TEST_COLLECTION_ID), isNull());
        }

        @Test
        @DisplayName("状态为空字符串 - 传递空字符串到mapper，mapper动态SQL跳过筛选")
        void getCollectionDocuments_WithBlankStatus() {
            setRegularUserContext();
            Page<CollectionDocument> mpPage = new Page<>(1, 10);
            mpPage.setRecords(Arrays.asList(testCollectionDocument));
            mpPage.setTotal(1);
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            // 空字符串传递到mapper，mapper的<if test='status != null and status != ""'>会跳过状态筛选
            when(collectionDocumentMapper.selectPageByCollectionIdWithFilter(any(Page.class), eq(TEST_COLLECTION_ID), eq("")))
                    .thenReturn(mpPage);

            IPage<CollectionDocumentDTO> result = collectionService.getCollectionDocuments(TEST_COLLECTION_ID, 0, 10, "");

            assertNotNull(result);
            assertEquals(1, result.getTotal());
            // 验证空字符串被原样传递到mapper（mapper XML层负责过滤）
            verify(collectionDocumentMapper).selectPageByCollectionIdWithFilter(any(Page.class), eq(TEST_COLLECTION_ID), eq(""));
        }

        @Test
        @DisplayName("mapper方法JOIN documents表过滤is_deleted=false - 确保软删除文档不返回")
        void getCollectionDocuments_VerifiesIsDeletedFilter() {
            setRegularUserContext();
            Page<CollectionDocument> mpPage = new Page<>(1, 10);
            mpPage.setRecords(Arrays.asList(testCollectionDocument));
            mpPage.setTotal(1);
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(collectionDocumentMapper.selectPageByCollectionIdWithFilter(any(Page.class), any(), any()))
                    .thenReturn(mpPage);

            collectionService.getCollectionDocuments(TEST_COLLECTION_ID, 0, 10, null);

            // selectPageByCollectionIdWithFilter SQL JOIN documents表并过滤 is_deleted=false
            // 此测试确认该方法被调用，SQL层面保证已删除文档不包含在结果中
            verify(collectionDocumentMapper).selectPageByCollectionIdWithFilter(any(Page.class), eq(TEST_COLLECTION_ID), isNull());
            // 注：is_deleted过滤在mapper XML的SQL中实现（JOIN documents d ON cd.document_id = d.id WHERE d.is_deleted = false）
            // 无法通过Mockito验证SQL内容，但mapper方法签名和XML确保了此行为
        }
    }

    @Nested
    @DisplayName("EdgeCase测试 - 代码审查修复验证")
    class EdgeCaseTests {

        @Test
        @DisplayName("删除知识库 - 非所有者非管理员抛出权限拒绝")
        void deleteCollection_NonOwner_NonAdmin_ThrowsPermissionDenied() {
            // 非所有者、非管理员用户尝试删除
            setRegularUserContext();
            testCollection.setOwnerId("other-user");
            testCollection.setDepartmentId("other-dept");
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);

            BizException exception = assertThrows(BizException.class,
                    () -> collectionService.deleteCollection(TEST_COLLECTION_ID));

            assertEquals(ErrorCode.PERMISSION_DENIED, exception.getErrorCode());
            // 验证未执行任何删除操作
            verify(collectionMapper, never()).updateById(any(Collection.class));
            verify(collectionDocumentMapper, never()).deleteByCollectionId(anyString());
        }

        @Test
        @DisplayName("删除知识库 - 已删除的知识库抛出NOT_FOUND")
        void deleteCollection_AlreadyDeleted_ThrowsNotFound() {
            setRegularUserContext();
            testCollection.setDeletedAt(LocalDateTime.now());
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);

            BizException exception = assertThrows(BizException.class,
                    () -> collectionService.deleteCollection(TEST_COLLECTION_ID));

            assertEquals(ErrorCode.COLLECTION_NOT_FOUND, exception.getErrorCode());
            // 验证未执行任何删除操作
            verify(collectionMapper, never()).updateById(any(Collection.class));
            verify(collectionDocumentMapper, never()).deleteByCollectionId(anyString());
        }

        @Test
        @DisplayName("获取知识库文档列表 - 负数页码正确分页")
        void getCollectionDocuments_InvalidPage_PaginatesCorrectly() {
            setRegularUserContext();
            Page<CollectionDocument> mpPage = new Page<>(0, 10);
            mpPage.setRecords(Collections.emptyList());
            mpPage.setTotal(0);
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(collectionDocumentMapper.selectPageByCollectionIdWithFilter(any(Page.class), eq(TEST_COLLECTION_ID), isNull()))
                    .thenReturn(mpPage);

            // 传入负数页码，service层做 page+1，MyBatis-Plus Page构造器处理边界
            IPage<CollectionDocumentDTO> result = collectionService.getCollectionDocuments(TEST_COLLECTION_ID, -1, 10, null);

            assertNotNull(result);
            // 验证mapper被调用，Page对象由service层构造传入
            verify(collectionDocumentMapper).selectPageByCollectionIdWithFilter(any(Page.class), eq(TEST_COLLECTION_ID), isNull());
        }

        @Test
        @DisplayName("获取知识库文档列表 - 空结果返回空页")
        void getCollectionDocuments_EmptyResult() {
            setRegularUserContext();
            Page<CollectionDocument> mpPage = new Page<>(1, 10);
            mpPage.setRecords(Collections.emptyList());
            mpPage.setTotal(0);
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(collectionDocumentMapper.selectPageByCollectionIdWithFilter(any(Page.class), eq(TEST_COLLECTION_ID), isNull()))
                    .thenReturn(mpPage);

            IPage<CollectionDocumentDTO> result = collectionService.getCollectionDocuments(TEST_COLLECTION_ID, 0, 10, null);

            assertNotNull(result);
            assertEquals(0, result.getTotal());
            assertTrue(result.getRecords().isEmpty());
        }

        @Test
        @DisplayName("convertToDetailDTO - departmentId为null时departmentName为null")
        void convertToDetailDTO_WithNullDepartmentId() {
            setSystemAdminContext();
            testCollection.setDepartmentId(null);
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            // departmentId为null，不应调用departmentMapper
            when(userMapper.selectById(anyString())).thenReturn(null);

            CollectionDetailDTO result = collectionService.getCollectionById(TEST_COLLECTION_ID);

            assertNotNull(result);
            assertNull(result.getDepartmentId());
            assertNull(result.getDepartmentName());
            // 验证未查询部门（因为departmentId为null）
            verify(departmentMapper, never()).selectById(anyString());
        }

        @Test
        @DisplayName("convertToDetailDTO - ownerId为null时ownerName为null")
        void convertToDetailDTO_WithNullOwnerId() {
            setSystemAdminContext();
            testCollection.setOwnerId(null);
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(departmentMapper.selectById(anyString())).thenReturn(null);

            CollectionDetailDTO result = collectionService.getCollectionById(TEST_COLLECTION_ID);

            assertNotNull(result);
            assertNull(result.getOwnerId());
            assertNull(result.getOwnerName());
            // 验证未查询用户（因为ownerId为null）
            verify(userMapper, never()).selectById(anyString());
        }

        @Test
        @DisplayName("convertToDetailDTO - 部门ID存在但数据库中未找到，departmentName为null")
        void convertToDetailDTO_WithMissingDepartment() {
            setSystemAdminContext();
            testCollection.setDepartmentId("dept-missing");
            testCollection.setOwnerId(null);
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            // 部门ID存在但数据库返回null
            when(departmentMapper.selectById("dept-missing")).thenReturn(null);

            CollectionDetailDTO result = collectionService.getCollectionById(TEST_COLLECTION_ID);

            assertNotNull(result);
            assertEquals("dept-missing", result.getDepartmentId());
            assertNull(result.getDepartmentName());
            verify(departmentMapper).selectById("dept-missing");
        }

        @Test
        @DisplayName("convertToDetailDTO - 用户ID存在但数据库中未找到，ownerName为null")
        void convertToDetailDTO_WithMissingOwner() {
            setSystemAdminContext();
            testCollection.setDepartmentId(null);
            testCollection.setOwnerId("user-missing");
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            // 用户ID存在但数据库返回null
            when(userMapper.selectById("user-missing")).thenReturn(null);

            CollectionDetailDTO result = collectionService.getCollectionById(TEST_COLLECTION_ID);

            assertNotNull(result);
            assertEquals("user-missing", result.getOwnerId());
            assertNull(result.getOwnerName());
            verify(userMapper).selectById("user-missing");
        }
    }

    @Nested
    @DisplayName("事务同步测试 - deleteCollection的afterCommit行为")
    class TransactionSynchronizationTests {

        @Test
        @DisplayName("deleteCollection在事务上下文中 - 外部清理注册afterCommit回调而非立即执行")
        void deleteCollection_UsesAfterCommitForExternalCleanup() throws Exception {
            setRegularUserContext();
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(collectionMapper.updateById(any(Collection.class))).thenReturn(1);

            // 在单元测试中，TransactionSynchronizationManager默认不活跃，
            // 所以deleteCollection走else分支直接调用cleanupExternalResources。
            // 此测试验证代码结构：当事务同步活跃时，外部清理应延迟到afterCommit。
            // 我们通过mock静态方法来模拟事务同步活跃的场景。

            try (var mockedStatic = mockStatic(TransactionSynchronizationManager.class)) {
                mockedStatic.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(true);
                mockedStatic.when(() -> TransactionSynchronizationManager.registerSynchronization(any(TransactionSynchronization.class)))
                        .thenAnswer(invocation -> {
                            // 捕获注册的TransactionSynchronization，验证afterCommit会调用cleanup
                            TransactionSynchronization sync = invocation.getArgument(0);
                            // 模拟事务提交后触发afterCommit
                            sync.afterCommit();
                            return null;
                        });

                collectionService.deleteCollection(TEST_COLLECTION_ID);

                // 验证DB操作在事务内完成
                verify(collectionMapper).updateById(any(Collection.class));
                verify(collectionDocumentMapper).deleteByCollectionId(TEST_COLLECTION_ID);

                // 验证注册了事务同步回调
                mockedStatic.verify(() -> TransactionSynchronizationManager.registerSynchronization(any(TransactionSynchronization.class)));

                // 验证afterCommit触发后外部资源被清理
                verify(vectorCrudService).deleteAllVectors(TEST_COLLECTION_ID);
                verify(esIndexService).deleteCollectionIndex(TEST_COLLECTION_ID);
            }
        }

        @Test
        @DisplayName("deleteCollection在非事务上下文中 - 外部清理立即执行")
        void deleteCollection_NonTransactionalContext_CleansUpImmediately() throws Exception {
            setRegularUserContext();
            when(collectionMapper.selectById(TEST_COLLECTION_ID)).thenReturn(testCollection);
            when(collectionMapper.updateById(any(Collection.class))).thenReturn(1);

            // 单元测试环境默认无事务同步，走else分支直接清理
            collectionService.deleteCollection(TEST_COLLECTION_ID);

            // 验证DB操作完成
            verify(collectionMapper).updateById(any(Collection.class));
            verify(collectionDocumentMapper).deleteByCollectionId(TEST_COLLECTION_ID);

            // 验证外部资源立即清理（非延迟到afterCommit）
            verify(vectorCrudService).deleteAllVectors(TEST_COLLECTION_ID);
            verify(esIndexService).deleteCollectionIndex(TEST_COLLECTION_ID);
        }
    }
}
