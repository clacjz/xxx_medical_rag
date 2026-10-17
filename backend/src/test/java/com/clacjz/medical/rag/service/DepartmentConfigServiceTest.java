package com.clacjz.medical.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.clacjz.medical.rag.BaseIntegrationTest;
import com.clacjz.medical.rag.config.DefaultDepartmentProperties;
import com.clacjz.medical.rag.mapper.DepartmentConfigLogMapper;
import com.clacjz.medical.rag.mapper.DepartmentMapper;
import com.clacjz.medical.rag.mapper.UserDepartmentMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.model.entity.Department;
import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.entity.UserDepartment;
import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import com.clacjz.medical.rag.model.enums.UserStatusEnums;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DepartmentConfig Service Integration Tests")
class DepartmentConfigServiceTest extends BaseIntegrationTest {

    @Autowired
    private DepartmentConfigService departmentConfigService;

    @Autowired
    private DepartmentMapper departmentMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserDepartmentMapper userDepartmentMapper;

    @Autowired
    private DepartmentConfigLogMapper configLogMapper;

    @Autowired
    private DefaultDepartmentProperties defaultDepartmentProperties;

    private User adminUser;
    private Department testDepartment;
    private String testDeptCode;

    @BeforeEach
    void setUp() {
        long timestamp = System.currentTimeMillis();
        testDeptCode = "TEST001-" + timestamp;

        // Clean up tables
        userDepartmentMapper.delete(new QueryWrapper<>());
        configLogMapper.delete(new QueryWrapper<>());
        departmentMapper.delete(new QueryWrapper<>());
        userMapper.delete(new QueryWrapper<>());

        adminUser = User.builder()
                .id(UUID.randomUUID().toString())
                .username("testadmin-" + timestamp)
                .email("testadmin" + timestamp + "@test.com")
                .passwordHash("hashed_password")
                .displayName("Test Admin")
                .role(UserRoleEnum.SYSTEM_ADMIN)
                .status(UserStatusEnums.ACTIVE)
                .loginFailCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userMapper.insert(adminUser);

        testDepartment = Department.builder()
                .id(UUID.randomUUID().toString())
                .code(testDeptCode)
                .name("测试部门")
                .description("测试用部门")
                .status("active")
                .isDefault(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        departmentMapper.insert(testDepartment);
    }

    @Test
    @DisplayName("Should get all user departments")
    void testGetUnassignedUsers() {
        List<UserDepartment> allUserDepartments = userDepartmentMapper.selectList(new QueryWrapper<>());
        assertNotNull(allUserDepartments);
    }

    @Test
    @DisplayName("Should load default department properties")
    void testConfigurationProperties() {
        assertFalse(defaultDepartmentProperties.isEnabled());
        assertEquals("DEFAULT", defaultDepartmentProperties.getCode());
        assertEquals("默认部门", defaultDepartmentProperties.getName());
    }

    @Test
    @DisplayName("Should check department exists by id")
    void testDepartmentExists() {
        Optional<Department> existingDept = Optional.ofNullable(departmentMapper.selectById(testDepartment.getId()));
        assertTrue(existingDept.isPresent());
        assertEquals(testDeptCode, existingDept.get().getCode());
        assertEquals("测试部门", existingDept.get().getName());
    }

    @Test
    @DisplayName("Should get department by id")
    void testGetDepartmentById() {
        Optional<Department> dept = Optional.ofNullable(departmentMapper.selectById(testDepartment.getId()));
        assertTrue(dept.isPresent());
        assertEquals("测试部门", dept.get().getName());
    }

    @Test
    @DisplayName("Should get department by code")
    void testGetDepartmentByCode() {
        Department dept = departmentMapper.selectByCode(testDeptCode);
        assertNotNull(dept);
        assertEquals(testDeptCode, dept.getCode());
    }
}
