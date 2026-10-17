package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.CreateDepartmentRequest;
import com.clacjz.medical.rag.model.dto.DepartmentDTO;
import com.clacjz.medical.rag.model.dto.UpdateDepartmentRequest;
import com.clacjz.medical.rag.model.dto.UserDepartmentDTO;
import com.clacjz.medical.rag.model.entity.Department;
import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.entity.UserDepartment;
import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import com.clacjz.medical.rag.mapper.DepartmentMapper;
import com.clacjz.medical.rag.mapper.DocumentMapper;
import com.clacjz.medical.rag.mapper.UserDepartmentMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.mapper.CollectionMapper;
import com.clacjz.medical.rag.mapper.DocumentPermissionMapper;
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
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DepartmentService 单元测试")
class DepartmentServiceTest {

    @Mock
    private DepartmentMapper departmentRepository;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private UserDepartmentMapper userDepartmentRepository;

    @Mock
    private UserMapper userRepository;

    @Mock
    private CollectionMapper collectionMapper;

    @Mock
    private DocumentPermissionMapper documentPermissionMapper;

    @InjectMocks
    private DepartmentService departmentService;

    private Department rootDepartment;
    private Department childDepartment;
    private User testUser;
    private User adminUser;
    private UserDepartment userDepartment;

    @BeforeEach
    void setUp() {
        rootDepartment = Department.builder()
                .id(UUID.randomUUID().toString())
                .code("ROOT")
                .name("根部门")
                .parentId(null)
                .managerId(null)
                .description("根部门描述")
                .status("active")
                .isDefault(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        childDepartment = Department.builder()
                .id(UUID.randomUUID().toString())
                .code("CHILD")
                .name("子部门")
                .parentId(rootDepartment.getId())
                .managerId(null)
                .description("子部门描述")
                .status("active")
                .isDefault(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testUser = User.builder()
                .id(UUID.randomUUID().toString())
                .username("testuser")
                .displayName("Test User")
                .role(UserRoleEnum.DEPT_USER)
                .build();

        adminUser = User.builder()
                .id(UUID.randomUUID().toString())
                .username("admin")
                .displayName("Admin User")
                .role(UserRoleEnum.SYSTEM_ADMIN)
                .build();

        userDepartment = UserDepartment.builder()
                .id(UUID.randomUUID().toString())
                .userId(testUser.getId())
                .departmentId(rootDepartment.getId())
                .isPrimary(true)
                .position("成员")
                .build();
    }

    @Nested
    @DisplayName("获取部门树测试")
    class GetDepartmentTreeTests {

        @Test
        @DisplayName("获取部门树成功 - 包含子部门")
        void getDepartmentTree_Success_WithChildren() {
            when(departmentRepository.selectRootDepartments()).thenReturn(Arrays.asList(rootDepartment));
            when(departmentRepository.countUsersByDepartmentId(rootDepartment.getId())).thenReturn(10L);
            when(departmentRepository.selectByParentId(rootDepartment.getId())).thenReturn(Arrays.asList(childDepartment));
            when(departmentRepository.countUsersByDepartmentId(childDepartment.getId())).thenReturn(5L);

            List<DepartmentDTO> tree = departmentService.getDepartmentTree();

            assertNotNull(tree);
            assertEquals(1, tree.size());
            assertEquals("根部门", tree.get(0).getName());
            assertEquals(10L, tree.get(0).getUserCount());
            assertNotNull(tree.get(0).getChildren());
            assertEquals(1, tree.get(0).getChildren().size());
            assertEquals("子部门", tree.get(0).getChildren().get(0).getName());
        }

        @Test
        @DisplayName("获取部门树成功 - 空树")
        void getDepartmentTree_Success_Empty() {
            when(departmentRepository.selectRootDepartments()).thenReturn(Collections.emptyList());

            List<DepartmentDTO> tree = departmentService.getDepartmentTree();

            assertNotNull(tree);
            assertTrue(tree.isEmpty());
        }

        @Test
        @DisplayName("获取部门树成功 - 无子部门的根部门")
        void getDepartmentTree_Success_NoChildren() {
            when(departmentRepository.selectRootDepartments()).thenReturn(Arrays.asList(rootDepartment));
            when(departmentRepository.countUsersByDepartmentId(rootDepartment.getId())).thenReturn(10L);
            when(departmentRepository.selectByParentId(rootDepartment.getId())).thenReturn(Collections.emptyList());

            List<DepartmentDTO> tree = departmentService.getDepartmentTree();

            assertNotNull(tree);
            assertEquals(1, tree.size());
            assertNull(tree.get(0).getChildren());
        }
    }

    @Nested
    @DisplayName("获取单个部门测试")
    class GetDepartmentByIdTests {

        @Test
        @DisplayName("获取部门成功 - 无管理员")
        void getDepartmentById_Success_NoManager() {
            when(departmentRepository.selectById(rootDepartment.getId())).thenReturn(rootDepartment);
            when(departmentRepository.countUsersByDepartmentId(rootDepartment.getId())).thenReturn(10L);

            DepartmentDTO dto = departmentService.getDepartmentById(rootDepartment.getId());

            assertNotNull(dto);
            assertEquals("根部门", dto.getName());
            assertEquals(10L, dto.getUserCount());
            assertNull(dto.getManagerName());
        }

        @Test
        @DisplayName("获取部门成功 - 包含管理员名称")
        void getDepartmentById_Success_WithManager() {
            rootDepartment.setManagerId(adminUser.getId());
            when(departmentRepository.selectById(rootDepartment.getId())).thenReturn(rootDepartment);
            when(departmentRepository.countUsersByDepartmentId(rootDepartment.getId())).thenReturn(10L);
            when(userRepository.selectById(adminUser.getId())).thenReturn(adminUser);

            DepartmentDTO dto = departmentService.getDepartmentById(rootDepartment.getId());

            assertNotNull(dto);
            assertEquals("Admin User", dto.getManagerName());
        }

        @Test
        @DisplayName("获取部门失败 - 部门不存在")
        void getDepartmentById_NotFound() {
            when(departmentRepository.selectById("nonexistent")).thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> departmentService.getDepartmentById("nonexistent"));

            assertEquals(ErrorCode.DEPARTMENT_NOT_FOUND, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("创建部门测试")
    class CreateDepartmentTests {

        @Test
        @DisplayName("创建部门成功 - 根部门")
        void createDepartment_Success_RootDepartment() {
            CreateDepartmentRequest request = CreateDepartmentRequest.builder()
                    .code("NEW")
                    .name("新部门")
                    .description("新部门描述")
                    .build();

            when(departmentRepository.existsByCode("NEW")).thenReturn(false);
            when(departmentRepository.insert(any(Department.class))).thenAnswer(invocation -> {
                Department dept = invocation.getArgument(0);
                dept.setId(UUID.randomUUID().toString());
                return 1;
            });

            DepartmentDTO result = departmentService.createDepartment(request, testUser.getId());

            assertNotNull(result);
            assertEquals("新部门", result.getName());
            verify(departmentRepository).insert(any(Department.class));
        }

        @Test
        @DisplayName("创建部门成功 - 有上级部门")
        void createDepartment_Success_WithParent() {
            CreateDepartmentRequest request = CreateDepartmentRequest.builder()
                    .code("CHILD_NEW")
                    .name("新子部门")
                    .parentId(rootDepartment.getId())
                    .build();

            when(departmentRepository.existsByCode("CHILD_NEW")).thenReturn(false);
            when(departmentRepository.selectById(rootDepartment.getId())).thenReturn(rootDepartment);
            when(departmentRepository.insert(any(Department.class))).thenAnswer(invocation -> {
                Department dept = invocation.getArgument(0);
                dept.setId(UUID.randomUUID().toString());
                return 1;
            });

            DepartmentDTO result = departmentService.createDepartment(request, testUser.getId());

            assertNotNull(result);
        }

        @Test
        @DisplayName("创建部门成功 - 包含管理员")
        void createDepartment_Success_WithManager() {
            CreateDepartmentRequest request = CreateDepartmentRequest.builder()
                    .code("NEW_ADMIN")
                    .name("新部门")
                    .managerId(adminUser.getId())
                    .build();

            when(departmentRepository.existsByCode("NEW_ADMIN")).thenReturn(false);
            when(userRepository.selectById(adminUser.getId())).thenReturn(adminUser);
            when(departmentRepository.insert(any(Department.class))).thenAnswer(invocation -> {
                Department dept = invocation.getArgument(0);
                dept.setId(UUID.randomUUID().toString());
                return 1;
            });

            DepartmentDTO result = departmentService.createDepartment(request, testUser.getId());

            assertNotNull(result);
        }

        @Test
        @DisplayName("创建部门失败 - 部门编码已存在")
        void createDepartment_Failure_CodeExists() {
            CreateDepartmentRequest request = CreateDepartmentRequest.builder()
                    .code("ROOT")
                    .name("新部门")
                    .build();

            when(departmentRepository.existsByCode("ROOT")).thenReturn(true);

            BizException exception = assertThrows(BizException.class,
                    () -> departmentService.createDepartment(request, testUser.getId()));

            assertEquals(ErrorCode.DEPARTMENT_ALREADY_EXISTS, exception.getErrorCode());
        }

        @Test
        @DisplayName("创建部门失败 - 上级部门不存在")
        void createDepartment_Failure_ParentNotFound() {
            CreateDepartmentRequest request = CreateDepartmentRequest.builder()
                    .code("NEW")
                    .name("新部门")
                    .parentId("nonexistent")
                    .build();

            when(departmentRepository.existsByCode("NEW")).thenReturn(false);
            when(departmentRepository.selectById("nonexistent")).thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> departmentService.createDepartment(request, testUser.getId()));

            assertEquals(ErrorCode.DEPARTMENT_NOT_FOUND, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("上级部门"));
        }

        @Test
        @DisplayName("创建部门失败 - 管理员不存在")
        void createDepartment_Failure_ManagerNotFound() {
            CreateDepartmentRequest request = CreateDepartmentRequest.builder()
                    .code("NEW")
                    .name("新部门")
                    .managerId("nonexistent")
                    .build();

            when(departmentRepository.existsByCode("NEW")).thenReturn(false);
            when(userRepository.selectById("nonexistent")).thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> departmentService.createDepartment(request, testUser.getId()));

            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("更新部门测试")
    class UpdateDepartmentTests {

        @Test
        @DisplayName("更新部门成功 - 更新名称")
        void updateDepartment_Success_UpdateName() {
            UpdateDepartmentRequest request = UpdateDepartmentRequest.builder()
                    .name("更新后的名称")
                    .build();

            when(departmentRepository.selectById(rootDepartment.getId())).thenReturn(rootDepartment);
            when(departmentRepository.updateById(any(Department.class))).thenReturn(1);

            DepartmentDTO result = departmentService.updateDepartment(rootDepartment.getId(), request, testUser.getId());

            assertNotNull(result);
            verify(departmentRepository).updateById(any(Department.class));
        }

        @Test
        @DisplayName("更新部门成功 - 更新管理员")
        void updateDepartment_Success_UpdateManager() {
            UpdateDepartmentRequest request = UpdateDepartmentRequest.builder()
                    .managerId(adminUser.getId())
                    .build();

            when(departmentRepository.selectById(rootDepartment.getId())).thenReturn(rootDepartment);
            when(userRepository.selectById(adminUser.getId())).thenReturn(adminUser);
            when(departmentRepository.updateById(any(Department.class))).thenReturn(1);

            DepartmentDTO result = departmentService.updateDepartment(rootDepartment.getId(), request, testUser.getId());

            assertNotNull(result);
        }

        @Test
        @DisplayName("更新部门成功 - 更新描述")
        void updateDepartment_Success_UpdateDescription() {
            UpdateDepartmentRequest request = UpdateDepartmentRequest.builder()
                    .description("更新后的描述")
                    .build();

            when(departmentRepository.selectById(rootDepartment.getId())).thenReturn(rootDepartment);
            when(departmentRepository.updateById(any(Department.class))).thenReturn(1);

            DepartmentDTO result = departmentService.updateDepartment(rootDepartment.getId(), request, testUser.getId());

            assertNotNull(result);
        }

        @Test
        @DisplayName("更新部门成功 - 更新状态")
        void updateDepartment_Success_UpdateStatus() {
            UpdateDepartmentRequest request = UpdateDepartmentRequest.builder()
                    .status("inactive")
                    .build();

            when(departmentRepository.selectById(rootDepartment.getId())).thenReturn(rootDepartment);
            when(departmentRepository.updateById(any(Department.class))).thenReturn(1);

            DepartmentDTO result = departmentService.updateDepartment(rootDepartment.getId(), request, testUser.getId());

            assertNotNull(result);
        }

        @Test
        @DisplayName("更新部门失败 - 部门不存在")
        void updateDepartment_Failure_NotFound() {
            UpdateDepartmentRequest request = UpdateDepartmentRequest.builder()
                    .name("新名称")
                    .build();

            when(departmentRepository.selectById("nonexistent")).thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> departmentService.updateDepartment("nonexistent", request, testUser.getId()));

            assertEquals(ErrorCode.DEPARTMENT_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("更新部门失败 - 新管理员不存在")
        void updateDepartment_Failure_ManagerNotFound() {
            UpdateDepartmentRequest request = UpdateDepartmentRequest.builder()
                    .managerId("nonexistent")
                    .build();

            when(departmentRepository.selectById(rootDepartment.getId())).thenReturn(rootDepartment);
            when(userRepository.selectById("nonexistent")).thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> departmentService.updateDepartment(rootDepartment.getId(), request, testUser.getId()));

            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("删除部门测试")
    class DeleteDepartmentTests {

        @Test
        @DisplayName("删除部门成功 - 无用户无子部门")
        void deleteDepartment_Success() {
            when(departmentRepository.selectById(rootDepartment.getId())).thenReturn(rootDepartment);
            when(departmentRepository.countUsersByDepartmentId(rootDepartment.getId())).thenReturn(0L);
            when(departmentRepository.selectByParentId(rootDepartment.getId())).thenReturn(Collections.emptyList());

            assertDoesNotThrow(() -> departmentService.deleteDepartment(rootDepartment.getId(), testUser.getId()));
            verify(departmentRepository).deleteById(rootDepartment.getId());
        }

        @Test
        @DisplayName("删除部门失败 - 部门不存在")
        void deleteDepartment_Failure_NotFound() {
            when(departmentRepository.selectById("nonexistent")).thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> departmentService.deleteDepartment("nonexistent", testUser.getId()));

            assertEquals(ErrorCode.DEPARTMENT_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("删除部门失败 - 部门下有用户")
        void deleteDepartment_Failure_HasUsers() {
            when(departmentRepository.selectById(rootDepartment.getId())).thenReturn(rootDepartment);
            when(departmentRepository.countUsersByDepartmentId(rootDepartment.getId())).thenReturn(5L);

            BizException exception = assertThrows(BizException.class,
                    () -> departmentService.deleteDepartment(rootDepartment.getId(), testUser.getId()));

            assertEquals(ErrorCode.OPERATION_FAILED, exception.getErrorCode());
        }

        @Test
        @DisplayName("删除部门失败 - 部门下有子部门")
        void deleteDepartment_Failure_HasChildren() {
            when(departmentRepository.selectById(rootDepartment.getId())).thenReturn(rootDepartment);
            when(departmentRepository.countUsersByDepartmentId(rootDepartment.getId())).thenReturn(0L);
            when(departmentRepository.selectByParentId(rootDepartment.getId())).thenReturn(Arrays.asList(childDepartment));

            BizException exception = assertThrows(BizException.class,
                    () -> departmentService.deleteDepartment(rootDepartment.getId(), testUser.getId()));

            assertEquals(ErrorCode.OPERATION_FAILED, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("子部门"));
        }
    }

    @Nested
    @DisplayName("获取部门用户列表测试")
    class GetDepartmentUsersTests {

        @Test
        @DisplayName("获取部门用户列表成功")
        void getDepartmentUsers_Success() {
            userDepartment.setUser(testUser);
            when(departmentRepository.selectById(rootDepartment.getId())).thenReturn(rootDepartment);
            when(userDepartmentRepository.selectByDepartmentId(rootDepartment.getId()))
                    .thenReturn(Arrays.asList(userDepartment));
            when(userRepository.selectById(testUser.getId())).thenReturn(testUser);

            List<UserDepartmentDTO> result = departmentService.getDepartmentUsers(rootDepartment.getId());

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(testUser.getUsername(), result.get(0).getUsername());
            assertEquals("根部门", result.get(0).getDepartmentName());
        }

        @Test
        @DisplayName("获取部门用户列表成功 - 空列表")
        void getDepartmentUsers_Success_Empty() {
            when(departmentRepository.selectById(rootDepartment.getId())).thenReturn(rootDepartment);
            when(userDepartmentRepository.selectByDepartmentId(rootDepartment.getId()))
                    .thenReturn(Collections.emptyList());

            List<UserDepartmentDTO> result = departmentService.getDepartmentUsers(rootDepartment.getId());

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("获取部门用户列表失败 - 部门不存在")
        void getDepartmentUsers_Failure_NotFound() {
            when(departmentRepository.selectById("nonexistent")).thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> departmentService.getDepartmentUsers("nonexistent"));

            assertEquals(ErrorCode.DEPARTMENT_NOT_FOUND, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("添加用户到部门测试")
    class AddUserToDepartmentTests {

        @Test
        @DisplayName("添加用户到部门成功 - 非主要部门")
        void addUserToDepartment_Success_NonPrimary() {
            when(departmentRepository.selectById(rootDepartment.getId())).thenReturn(rootDepartment);
            when(userRepository.selectById(testUser.getId())).thenReturn(testUser);
            when(userDepartmentRepository.existsByUserIdAndDepartmentId(testUser.getId(), rootDepartment.getId()))
                    .thenReturn(0);
            when(userDepartmentRepository.insert(any(UserDepartment.class))).thenReturn(1);

            assertDoesNotThrow(() -> departmentService.addUserToDepartment(
                    testUser.getId(), rootDepartment.getId(), false, "成员", adminUser.getId()));

            verify(userDepartmentRepository).insert(any(UserDepartment.class));
            verify(userDepartmentRepository, never()).selectByUserIdAndIsPrimaryTrue(anyString());
        }

        @Test
        @DisplayName("添加用户到部门成功 - 设置为主要部门")
        void addUserToDepartment_Success_SetPrimary() {
            UserDepartment existingPrimary = UserDepartment.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(testUser.getId())
                    .departmentId(childDepartment.getId())
                    .isPrimary(true)
                    .build();

            when(departmentRepository.selectById(rootDepartment.getId())).thenReturn(rootDepartment);
            when(userRepository.selectById(testUser.getId())).thenReturn(testUser);
            when(userDepartmentRepository.existsByUserIdAndDepartmentId(testUser.getId(), rootDepartment.getId()))
                    .thenReturn(0);
            when(userDepartmentRepository.selectByUserIdAndIsPrimaryTrue(testUser.getId()))
                    .thenReturn(existingPrimary);
            when(userDepartmentRepository.updateById(any(UserDepartment.class))).thenReturn(1);
            when(userDepartmentRepository.insert(any(UserDepartment.class))).thenReturn(1);

            assertDoesNotThrow(() -> departmentService.addUserToDepartment(
                    testUser.getId(), rootDepartment.getId(), true, "主管", adminUser.getId()));

            verify(userDepartmentRepository, times(1)).insert(any(UserDepartment.class));
            assertFalse(existingPrimary.getIsPrimary());
        }

        @Test
        @DisplayName("添加用户到部门失败 - 部门不存在")
        void addUserToDepartment_Failure_DepartmentNotFound() {
            when(departmentRepository.selectById("nonexistent")).thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> departmentService.addUserToDepartment(
                            testUser.getId(), "nonexistent", false, "成员", adminUser.getId()));

            assertEquals(ErrorCode.DEPARTMENT_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("添加用户到部门失败 - 用户不存在")
        void addUserToDepartment_Failure_UserNotFound() {
            when(departmentRepository.selectById(rootDepartment.getId())).thenReturn(rootDepartment);
            when(userRepository.selectById("nonexistent")).thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                    () -> departmentService.addUserToDepartment(
                            "nonexistent", rootDepartment.getId(), false, "成员", adminUser.getId()));

            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("添加用户到部门失败 - 用户已在该部门")
        void addUserToDepartment_Failure_AlreadyExists() {
            when(departmentRepository.selectById(rootDepartment.getId())).thenReturn(rootDepartment);
            when(userRepository.selectById(testUser.getId())).thenReturn(testUser);
            when(userDepartmentRepository.existsByUserIdAndDepartmentId(testUser.getId(), rootDepartment.getId()))
                    .thenReturn(1);

            BizException exception = assertThrows(BizException.class,
                    () -> departmentService.addUserToDepartment(
                            testUser.getId(), rootDepartment.getId(), false, "成员", adminUser.getId()));

            assertEquals(ErrorCode.USER_ALREADY_EXISTS, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("从部门移除用户测试")
    class RemoveUserFromDepartmentTests {

        @Test
        @DisplayName("从部门移除用户成功")
        void removeUserFromDepartment_Success() {
            assertDoesNotThrow(() -> departmentService.removeUserFromDepartment(
                    testUser.getId(), rootDepartment.getId(), adminUser.getId()));

            verify(userDepartmentRepository).deleteByUserIdAndDepartmentId(testUser.getId(), rootDepartment.getId());
        }
    }

    @Nested
    @DisplayName("获取用户部门ID列表测试")
    class GetUserDepartmentIdsTests {

        @Test
        @DisplayName("获取用户部门ID列表成功")
        void getUserDepartmentIds_Success() {
            when(userDepartmentRepository.selectDepartmentIdsByUserId(testUser.getId()))
                    .thenReturn(Arrays.asList(rootDepartment.getId(), childDepartment.getId()));

            Set<String> deptIds = departmentService.getUserDepartmentIds(testUser.getId());

            assertNotNull(deptIds);
            assertEquals(2, deptIds.size());
            assertTrue(deptIds.contains(rootDepartment.getId()));
            assertTrue(deptIds.contains(childDepartment.getId()));
        }

        @Test
        @DisplayName("获取用户部门ID列表成功 - 无部门")
        void getUserDepartmentIds_Success_Empty() {
            when(userDepartmentRepository.selectDepartmentIdsByUserId(testUser.getId()))
                    .thenReturn(Collections.emptyList());

            Set<String> deptIds = departmentService.getUserDepartmentIds(testUser.getId());

            assertNotNull(deptIds);
            assertTrue(deptIds.isEmpty());
        }
    }

    @Nested
    @DisplayName("检查用户是否在部门中测试")
    class IsUserInDepartmentTests {

        @Test
        @DisplayName("检查用户在部门中 - 是")
        void isUserInDepartment_True() {
            when(userDepartmentRepository.existsByUserIdAndDepartmentId(testUser.getId(), rootDepartment.getId()))
                    .thenReturn(1);

            assertTrue(departmentService.isUserInDepartment(testUser.getId(), rootDepartment.getId()));
        }

        @Test
        @DisplayName("检查用户在部门中 - 否")
        void isUserInDepartment_False() {
            when(userDepartmentRepository.existsByUserIdAndDepartmentId(testUser.getId(), rootDepartment.getId()))
                    .thenReturn(0);

            assertFalse(departmentService.isUserInDepartment(testUser.getId(), rootDepartment.getId()));
        }
    }

    @Nested
    @DisplayName("检查用户是否可以管理部门测试")
    class CanManageDepartmentTests {

        @Test
        @DisplayName("系统管理员可以管理任何部门")
        void canManageDepartment_SystemAdmin() {
            when(userRepository.selectById(adminUser.getId())).thenReturn(adminUser);

            assertTrue(departmentService.canManageDepartment(adminUser.getId(), rootDepartment.getId()));
        }

        @Test
        @DisplayName("部门管理员可以管理本部门")
        void canManageDepartment_DeptAdmin_InSameDepartment() {
            User deptAdmin = User.builder()
                    .id(UUID.randomUUID().toString())
                    .role(UserRoleEnum.DEPT_ADMIN)
                    .build();

            when(userRepository.selectById(deptAdmin.getId())).thenReturn(deptAdmin);
            when(userDepartmentRepository.selectDepartmentIdsByUserId(deptAdmin.getId()))
                    .thenReturn(Arrays.asList(rootDepartment.getId()));

            assertTrue(departmentService.canManageDepartment(deptAdmin.getId(), rootDepartment.getId()));
        }

        @Test
        @DisplayName("部门管理员不能管理其他部门")
        void canManageDepartment_DeptAdmin_OtherDepartment() {
            User deptAdmin = User.builder()
                    .id(UUID.randomUUID().toString())
                    .role(UserRoleEnum.DEPT_ADMIN)
                    .build();

            when(userRepository.selectById(deptAdmin.getId())).thenReturn(deptAdmin);
            when(userDepartmentRepository.selectDepartmentIdsByUserId(deptAdmin.getId()))
                    .thenReturn(Arrays.asList(childDepartment.getId()));

            assertFalse(departmentService.canManageDepartment(deptAdmin.getId(), rootDepartment.getId()));
        }

        @Test
        @DisplayName("普通用户不能管理任何部门")
        void canManageDepartment_RegularUser() {
            when(userRepository.selectById(testUser.getId())).thenReturn(testUser);

            assertFalse(departmentService.canManageDepartment(testUser.getId(), rootDepartment.getId()));
        }

        @Test
        @DisplayName("用户不存在")
        void canManageDepartment_UserNotFound() {
            when(userRepository.selectById("nonexistent")).thenReturn(null);

            assertFalse(departmentService.canManageDepartment("nonexistent", rootDepartment.getId()));
        }

        @Test
        @DisplayName("用户无部门关联")
        void canManageDepartment_NoDepartment() {
            User deptAdmin = User.builder()
                    .id(UUID.randomUUID().toString())
                    .role(UserRoleEnum.DEPT_ADMIN)
                    .build();

            when(userRepository.selectById(deptAdmin.getId())).thenReturn(deptAdmin);
            when(userDepartmentRepository.selectDepartmentIdsByUserId(deptAdmin.getId()))
                    .thenReturn(Collections.emptyList());

            assertFalse(departmentService.canManageDepartment(deptAdmin.getId(), rootDepartment.getId()));
        }
    }
}
