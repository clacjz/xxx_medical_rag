package com.clacjz.medical.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.mapper.*;
import com.clacjz.medical.rag.model.dto.*;
import com.clacjz.medical.rag.model.entity.*;
import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final UserDepartmentMapper userDepartmentMapper;
    private final UserMapper userMapper;
    private final DocumentMapper documentMapper;
    private final DocumentPermissionMapper documentPermissionMapper;
    private final CollectionMapper collectionMapper;
    private final DepartmentConfigLogMapper departmentConfigLogMapper;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<DepartmentDTO> getDepartmentTree() {
        List<Department> rootDepartments = departmentMapper.selectRootDepartments();
        return rootDepartments.stream()
                .map(this::buildDepartmentTree)
                .collect(Collectors.toList());
    }

    private DepartmentDTO buildDepartmentTree(Department department) {
        DepartmentDTO dto = DepartmentDTO.fromEntity(department);
        long userCount = departmentMapper.countUsersByDepartmentId(department.getId());
        dto.setUserCount(userCount);

        List<Department> children = departmentMapper.selectByParentId(department.getId());
        if (children != null && !children.isEmpty()) {
            dto.setChildren(children.stream()
                    .map(child -> {
                        DepartmentDTO childDto = DepartmentDTO.fromEntity(child);
                        childDto.setUserCount(departmentMapper.countUsersByDepartmentId(child.getId()));
                        return childDto;
                    })
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    @Transactional(readOnly = true)
    public DepartmentDTO getDepartmentById(String departmentId) {
        Department department = departmentMapper.selectById(departmentId);
        if (department == null) {
            throw new BizException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }

        DepartmentDTO dto = DepartmentDTO.fromEntity(department);
        dto.setUserCount(departmentMapper.countUsersByDepartmentId(departmentId));

        if (department.getManagerId() != null) {
            User manager = userMapper.selectById(department.getManagerId());
            if (manager != null) {
                dto.setManagerName(manager.getDisplayName());
            }
        }

        return dto;
    }

    @Transactional
    public DepartmentDTO createDepartment(CreateDepartmentRequest request, String operatorId) {
        if (departmentMapper.existsByCode(request.getCode())) {
            throw new BizException(ErrorCode.DEPARTMENT_ALREADY_EXISTS, "部门编码已存在: " + request.getCode());
        }

        if (request.getParentId() != null && departmentMapper.selectById(request.getParentId()) == null) {
            throw new BizException(ErrorCode.DEPARTMENT_NOT_FOUND, "上级部门不存在");
        }

        if (request.getManagerId() != null && userMapper.selectById(request.getManagerId()) == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "部门管理员不存在");
        }

        Department department = Department.builder()
                .code(request.getCode())
                .name(request.getName())
                .parentId(request.getParentId())
                .managerId(request.getManagerId())
                .description(request.getDescription())
                .status("active")
                .build();

        departmentMapper.insert(department);

        // 审计日志
        logDepartmentChange(operatorId, "CREATE", department.getId(),
                null, department, "创建部门");

        log.info("Department created: id={}, code={}, operator={}", department.getId(), department.getCode(), operatorId);

        return DepartmentDTO.fromEntity(department);
    }

    @Transactional
    public DepartmentDTO updateDepartment(String departmentId, UpdateDepartmentRequest request, String operatorId) {
        Department department = departmentMapper.selectById(departmentId);
        if (department == null) {
            throw new BizException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }

        // 保存旧值用于审计
        Department oldValue = copyDepartment(department);

        if (request.getName() != null) {
            department.setName(request.getName());
        }

        if (request.getManagerId() != null) {
            if (userMapper.selectById(request.getManagerId()) == null) {
                throw new BizException(ErrorCode.USER_NOT_FOUND, "部门管理员不存在");
            }
            department.setManagerId(request.getManagerId());
        }

        if (request.getDescription() != null) {
            department.setDescription(request.getDescription());
        }

        if (request.getStatus() != null) {
            department.setStatus(request.getStatus());
        }

        departmentMapper.updateById(department);

        // 审计日志
        logDepartmentChange(operatorId, "UPDATE", departmentId,
                oldValue, department, "更新部门");

        log.info("Department updated: id={}, operator={}", departmentId, operatorId);

        return DepartmentDTO.fromEntity(department);
    }

    @Transactional
    public void deleteDepartment(String departmentId, String operatorId) {
        // 先检查
        DepartmentDeleteCheckResult checkResult = checkDepartmentDelete(departmentId);
        if (!checkResult.isCanDelete()) {
            throw new BizException(ErrorCode.OPERATION_FAILED,
                    "无法删除部门: " + String.join("; ", checkResult.getErrors()));
        }

        departmentMapper.deleteById(departmentId);
        log.info("Department deleted: id={}, operator={}", departmentId, operatorId);
    }

    @Transactional(readOnly = true)
    public List<UserDepartmentDTO> getDepartmentUsers(String departmentId) {
        Department department = departmentMapper.selectById(departmentId);
        if (department == null) {
            throw new BizException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }

        List<UserDepartment> userDepartments = userDepartmentMapper.selectByDepartmentId(departmentId);

        return userDepartments.stream()
                .map(ud -> {
                    UserDepartmentDTO dto = UserDepartmentDTO.fromEntity(ud);
                    dto.setDepartmentName(department.getName());
                    User user = userMapper.selectById(ud.getUserId());
                    if (user != null) {
                        dto.setUsername(user.getUsername());
                        dto.setDisplayName(user.getDisplayName());
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void addUserToDepartment(String userId, String departmentId, boolean isPrimary, String position, String operatorId) {
        if (departmentMapper.selectById(departmentId) == null) {
            throw new BizException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }

        if (userMapper.selectById(userId) == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        if (userDepartmentMapper.existsByUserIdAndDepartmentId(userId, departmentId) > 0) {
            throw new BizException(ErrorCode.USER_ALREADY_EXISTS, "用户已在该部门中");
        }

        if (isPrimary) {
            UserDepartment existingPrimary = userDepartmentMapper.selectByUserIdAndIsPrimaryTrue(userId);
            if (existingPrimary != null) {
                existingPrimary.setIsPrimary(false);
                userDepartmentMapper.updateById(existingPrimary);
            }
        }

        UserDepartment userDepartment = UserDepartment.builder()
                .userId(userId)
                .departmentId(departmentId)
                .isPrimary(isPrimary)
                .position(position)
                .build();

        userDepartmentMapper.insert(userDepartment);
        log.info("User added to department: userId={}, departmentId={}, operator={}", userId, departmentId, operatorId);
    }

    @Transactional
    public void removeUserFromDepartment(String userId, String departmentId, String operatorId) {
        userDepartmentMapper.deleteByUserIdAndDepartmentId(userId, departmentId);
        log.info("User removed from department: userId={}, departmentId={}, operator={}", userId, departmentId, operatorId);
    }

    @Transactional(readOnly = true)
    public Set<String> getUserDepartmentIds(String userId) {
        return new HashSet<>(userDepartmentMapper.selectDepartmentIdsByUserId(userId));
    }

    @Transactional(readOnly = true)
    public boolean isUserInDepartment(String userId, String departmentId) {
        return userDepartmentMapper.existsByUserIdAndDepartmentId(userId, departmentId) > 0;
    }

    @Transactional(readOnly = true)
    public boolean canManageDepartment(String userId, String departmentId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.debug("User not found for department management check: userId={}", userId);
            return false;
        }

        if (user.getRole() == UserRoleEnum.SYSTEM_ADMIN) {
            return true;
        }

        if (user.getRole() == UserRoleEnum.DEPT_ADMIN) {
            Set<String> userDeptIds = getUserDepartmentIds(userId);
            if (userDeptIds == null) {
                log.debug("User has no departments: userId={}", userId);
                return false;
            }
            return userDeptIds.contains(departmentId);
        }

        return false;
    }

    /**
     * 检查部门是否可以删除
     * @param departmentId 部门ID
     * @return 删除检查结果
     */
    @Transactional(readOnly = true)
    public DepartmentDeleteCheckResult checkDepartmentDelete(String departmentId) {
        Department department = departmentMapper.selectById(departmentId);
        if (department == null) {
            throw new BizException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }

        List<String> errors = new ArrayList<>();
        List<DocumentSimpleDTO> relatedDocuments = new ArrayList<>();
        List<CollectionSimpleDTO> relatedCollections = new ArrayList<>();

        // 1. 检查是否有子部门
        List<Department> children = departmentMapper.selectByParentId(departmentId);
        if (children != null && !children.isEmpty()) {
            errors.add("部门下存在 " + children.size() + " 个子部门");
        }

        // 2. 检查是否有用户
        long userCount = departmentMapper.countUsersByDepartmentId(departmentId);
        if (userCount > 0) {
            errors.add("部门下存在 " + userCount + " 个用户");
        }

        // 3. 检查部门关联的文档
        // 查询所有文档，匹配departmentId字段包含该部门ID的
        List<Document> allDocuments = documentMapper.selectAllActive();
        for (Document doc : allDocuments) {
            if (doc.belongsToDepartment(departmentId)) {
                DocumentPermission permission = documentPermissionMapper.selectByDocumentId(doc.getId());
                if (permission != null) {
                    List<String> deptIds = permission.getDepartmentIdList();
                    // 如果文档仅属于待删除部门，则不可删除
                    if (deptIds.size() == 1 && deptIds.contains(departmentId)) {
                        errors.add("文档《" + doc.getTitle() + "》仅属于此部门，需先转移或删除");
                        relatedDocuments.add(DocumentSimpleDTO.builder()
                                .id(doc.getId())
                                .title(doc.getTitle())
                                .build());
                    }
                } else {
                    // 无权限配置时检查文档的部门字段
                    List<String> docDeptIds = doc.getDepartmentIdList();
                    if (docDeptIds.size() == 1 && docDeptIds.contains(departmentId)) {
                        errors.add("文档《" + doc.getTitle() + "》仅属于此部门，需先转移或删除");
                        relatedDocuments.add(DocumentSimpleDTO.builder()
                                .id(doc.getId())
                                .title(doc.getTitle())
                                .build());
                    }
                }
            }
        }

        // 4. 检查部门关联的知识库
        List<Collection> collections = collectionMapper.selectActiveByDepartmentId(departmentId);
        for (Collection col : collections) {
            errors.add("知识库《" + col.getName() + "》关联此部门");
            relatedCollections.add(CollectionSimpleDTO.builder()
                    .id(col.getId())
                    .name(col.getName())
                    .build());
        }

        return DepartmentDeleteCheckResult.builder()
                .canDelete(errors.isEmpty())
                .errors(errors)
                .relatedDocuments(relatedDocuments)
                .relatedCollections(relatedCollections)
                .build();
    }

    /**
     * 记录部门变更审计日志
     */
    private void logDepartmentChange(String operatorId, String operationType,
                                     String departmentId, Department oldValue,
                                     Department newValue, String description) {
        try {
            String beforeValue = oldValue != null ? objectMapper.writeValueAsString(Map.of(
                    "name", oldValue.getName() != null ? oldValue.getName() : "",
                    "code", oldValue.getCode() != null ? oldValue.getCode() : "",
                    "managerId", oldValue.getManagerId() != null ? oldValue.getManagerId() : "",
                    "description", oldValue.getDescription() != null ? oldValue.getDescription() : "",
                    "status", oldValue.getStatus() != null ? oldValue.getStatus() : ""
            )) : null;

            String afterValue = newValue != null ? objectMapper.writeValueAsString(Map.of(
                    "name", newValue.getName() != null ? newValue.getName() : "",
                    "code", newValue.getCode() != null ? newValue.getCode() : "",
                    "managerId", newValue.getManagerId() != null ? newValue.getManagerId() : "",
                    "description", newValue.getDescription() != null ? newValue.getDescription() : "",
                    "status", newValue.getStatus() != null ? newValue.getStatus() : ""
            )) : null;

            DepartmentConfigLog auditLog = DepartmentConfigLog.builder()
                    .operatorId(operatorId)
                    .targetType("DEPARTMENT")
                    .targetId(departmentId)
                    .operationType(operationType)
                    .beforeValue(beforeValue)
                    .afterValue(afterValue)
                    .description(description)
                    .build();

            departmentConfigLogMapper.insert(auditLog);
            log.debug("Department audit log created: operation={}, departmentId={}", operationType, departmentId);
        } catch (Exception e) {
            log.error("Failed to create department audit log: operation={}, departmentId={}, error={}",
                    operationType, departmentId, e.getMessage());
        }
    }

    /**
     * 复制Department对象用于审计
     */
    private Department copyDepartment(Department original) {
        if (original == null) return null;
        return Department.builder()
                .id(original.getId())
                .code(original.getCode())
                .name(original.getName())
                .parentId(original.getParentId())
                .managerId(original.getManagerId())
                .description(original.getDescription())
                .status(original.getStatus())
                .isDefault(original.getIsDefault())
                .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class DepartmentDeleteCheckResult {
        private boolean canDelete;
        private List<String> errors;
        private List<DocumentSimpleDTO> relatedDocuments;
        private List<CollectionSimpleDTO> relatedCollections;
    }

    @lombok.Data
    @lombok.Builder
    public static class DocumentSimpleDTO {
        private String id;
        private String title;
    }

    @lombok.Data
    @lombok.Builder
    public static class CollectionSimpleDTO {
        private String id;
        private String name;
    }
}