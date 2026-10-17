package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.*;
import com.clacjz.medical.rag.model.enums.CollectionStatus;
import com.clacjz.medical.rag.model.vo.PageResult;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.CollectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CollectionController 单元测试")
class CollectionControllerTest {

    @Mock
    private CollectionService collectionService;

    @InjectMocks
    private CollectionController collectionController;

    private MockHttpServletRequest mockRequest;
    private UserDetails mockUserDetails;
    private CollectionDetailDTO testCollection;

    @BeforeEach
    void setUp() {
        mockRequest = new MockHttpServletRequest();
        ServletRequestAttributes attributes = new ServletRequestAttributes(mockRequest);
        RequestContextHolder.setRequestAttributes(attributes);

        mockUserDetails = User.builder()
                .username("testuser")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        testCollection = CollectionDetailDTO.builder()
                .id(UUID.randomUUID().toString())
                .name("Test Collection")
                .description("Test Description")
                .status(CollectionStatus.ACTIVE)
                .documentCount(10)
                .createdAt(java.time.LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("获取Collection列表测试")
    class ListCollectionsTests {

        @Test
        @DisplayName("获取Collection列表成功 - 默认参数")
        void listCollections_Success_DefaultParams() {
            Page<CollectionDetailDTO> page = new Page<>(1, 20);
            page.setRecords(List.of(testCollection));
            page.setTotal(1);
            IPage<CollectionDetailDTO> collections = page;
            when(collectionService.getCollections(0, 20, null, null, null)).thenReturn(collections);

            ResponseEntity<Result<PageResult<CollectionDetailDTO>>> response =
                    collectionController.listCollections(0, 20, null, null, null, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(200, response.getBody().getCode());
            assertEquals(1, response.getBody().getData().getTotal());
        }

        @Test
        @DisplayName("获取Collection列表成功 - 按关键词筛选")
        void listCollections_Success_WithKeyword() {
            Page<CollectionDetailDTO> page = new Page<>(1, 20);
            page.setRecords(List.of(testCollection));
            page.setTotal(1);
            IPage<CollectionDetailDTO> collections = page;
            when(collectionService.getCollections(0, 20, "test", null, null)).thenReturn(collections);

            ResponseEntity<Result<PageResult<CollectionDetailDTO>>> response =
                    collectionController.listCollections(0, 20, "test", null, null, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("获取Collection列表成功 - 按状态筛选")
        void listCollections_Success_WithStatus() {
            Page<CollectionDetailDTO> page = new Page<>(1, 20);
            page.setRecords(List.of(testCollection));
            page.setTotal(1);
            IPage<CollectionDetailDTO> collections = page;
            when(collectionService.getCollections(0, 20, null, null, CollectionStatus.ACTIVE))
                    .thenReturn(collections);

            ResponseEntity<Result<PageResult<CollectionDetailDTO>>> response =
                    collectionController.listCollections(0, 20, null, null, CollectionStatus.ACTIVE, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("获取Collection列表返回空结果")
        void listCollections_EmptyResult() {
            Page<CollectionDetailDTO> emptyPage = new Page<>(1, 20);
            emptyPage.setRecords(List.of());
            emptyPage.setTotal(0);
            IPage<CollectionDetailDTO> collections = emptyPage;
            when(collectionService.getCollections(anyInt(), anyInt(), any(), any(), any()))
                    .thenReturn(collections);

            ResponseEntity<Result<PageResult<CollectionDetailDTO>>> response =
                    collectionController.listCollections(0, 20, null, null, null, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(0, response.getBody().getData().getTotal());
        }
    }

    @Nested
    @DisplayName("获取Collection详情测试")
    class GetCollectionTests {

        @Test
        @DisplayName("获取Collection详情成功")
        void getCollection_Success() {
            String collectionId = testCollection.getId();
            when(collectionService.getCollectionById(collectionId)).thenReturn(testCollection);

            ResponseEntity<Result<CollectionDetailDTO>> response =
                    collectionController.getCollection(collectionId, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(testCollection.getId(), response.getBody().getData().getId());
        }

        @Test
        @DisplayName("获取Collection详情失败 - Collection不存在")
        void getCollection_Failure_NotFound() {
            String collectionId = "nonexistent";
            when(collectionService.getCollectionById(collectionId)).thenThrow(
                    new BizException(ErrorCode.COLLECTION_NOT_FOUND, "Collection不存在")
            );

            assertThrows(BizException.class,
                    () -> collectionController.getCollection(collectionId, mockUserDetails));
        }
    }

    @Nested
    @DisplayName("创建Collection测试")
    class CreateCollectionTests {

        @Test
        @DisplayName("创建Collection成功")
        void createCollection_Success() {
            CreateCollectionRequest request = CreateCollectionRequest.builder()
                    .name("New Collection")
                    .description("Description")
                    .build();

            CollectionDetailDTO newCollection = CollectionDetailDTO.builder()
                    .id(UUID.randomUUID().toString())
                    .name("New Collection")
                    .description("Description")
                    .status(CollectionStatus.ACTIVE)
                    .build();

            when(collectionService.createCollection(request)).thenReturn(newCollection);

            ResponseEntity<Result<CollectionDetailDTO>> response =
                    collectionController.createCollection(request, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Collection创建成功", response.getBody().getMessage());
        }

        @Test
        @DisplayName("创建Collection失败 - 名称为空")
        void createCollection_Failure_EmptyName() {
            CreateCollectionRequest request = CreateCollectionRequest.builder()
                    .name("")
                    .build();

            when(collectionService.createCollection(request)).thenThrow(
                    new BizException(ErrorCode.INVALID_PARAMETER, "Collection名称不能为空")
            );

            assertThrows(BizException.class,
                    () -> collectionController.createCollection(request, mockUserDetails));
        }
    }

    @Nested
    @DisplayName("删除Collection测试")
    class DeleteCollectionTests {

        @Test
        @DisplayName("删除Collection成功")
        void deleteCollection_Success() {
            String collectionId = testCollection.getId();
            doNothing().when(collectionService).deleteCollection(collectionId);

            ResponseEntity<Result<Void>> response =
                    collectionController.deleteCollection(collectionId, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("Collection删除成功", response.getBody().getMessage());
            verify(collectionService, times(1)).deleteCollection(collectionId);
        }

        @Test
        @DisplayName("删除Collection失败 - Collection不存在")
        void deleteCollection_Failure_NotFound() {
            String collectionId = "nonexistent";
            doThrow(new BizException(ErrorCode.COLLECTION_NOT_FOUND, "Collection不存在"))
                    .when(collectionService).deleteCollection(collectionId);

            assertThrows(BizException.class,
                    () -> collectionController.deleteCollection(collectionId, mockUserDetails));
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class BoundaryTests {

        @Test
        @DisplayName("负数页码")
        void listCollections_NegativePage() {
            Page<CollectionDetailDTO> page = new Page<>(1, 20);
            page.setRecords(List.of(testCollection));
            page.setTotal(1);
            IPage<CollectionDetailDTO> collections = page;
            when(collectionService.getCollections(-1, 20, null, null, null))
                    .thenReturn(collections);

            ResponseEntity<Result<PageResult<CollectionDetailDTO>>> response =
                    collectionController.listCollections(-1, 20, null, null, null, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("零页大小")
        void listCollections_ZeroPageSize() {
            Page<CollectionDetailDTO> emptyPage = new Page<>(1, 20);
            emptyPage.setRecords(List.of());
            emptyPage.setTotal(0);
            IPage<CollectionDetailDTO> collections = emptyPage;
            when(collectionService.getCollections(0, 0, null, null, null))
                    .thenReturn(collections);

            ResponseEntity<Result<PageResult<CollectionDetailDTO>>> response =
                    collectionController.listCollections(0, 0, null, null, null, mockUserDetails);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }
}
