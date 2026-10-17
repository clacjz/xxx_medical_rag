package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.config.DefaultDepartmentProperties;
import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.mapper.DepartmentConfigLogMapper;
import com.clacjz.medical.rag.mapper.DepartmentMapper;
import com.clacjz.medical.rag.mapper.UserDepartmentMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.model.dto.BatchAssignUserRequest;
import com.clacjz.medical.rag.model.dto.DepartmentConfigLogDTO;
import com.clacjz.medical.rag.model.dto.DepartmentDTO;
import com.clacjz.medical.rag.model.dto.UserDTO;
import com.clacjz.medical.rag.model.entity.Department;
import com.clacjz.medical.rag.model.entity.DepartmentConfigLog;
import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.entity.UserDepartment;
import com.clacjz.medical.rag.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentConfigService {

    private final DepartmentMapper departmentMapper;
    private final UserDepartmentMapper userDepartmentMapper;
    private final UserMapper userMapper;
    private final DepartmentConfigLogMapper configLogMapper;
    private final DefaultDepartmentProperties defaultDepartmentProperties;
    private final DepartmentService departmentService;
    private final JsonUtil jsonUtil;

    private static final String TARGET_TYPE_DEPARTMENT = "DEPARTMENT";
    private static final String TARGET_TYPE_USER = "USER";
    private static final String OPERATION_SET_DEFAULT = "SET_DEFAULT_DEPARTMENT";
    private static final String OPERATION_UNSET_DEFAULT = "UNSET_DEFAULT_DEPARTMENT";
    private static final String OPERATION_ASSIGN_USER = "ASSIGN_USER_TO_DEPARTMENT";
    private static final String OPERATION_BATCH_ASSIGN = "BATCH_ASSIGN_USERS";
    private static final String OPERATION_AUTO_ASSIGN = "AUTO_ASSIGN_DEFAULT_DEPARTMENT";

    @Transactional(readOnly = true)
    public DepartmentDTO getDefaultDepartment() {
        Department defaultDept = departmentMapper.selectByIsDefaultTrue();
        if (defaultDept == null) {
            return null;
        }
        return DepartmentDTO.fromEntity(defaultDept);
    }

    @Transactional
    public DepartmentDTO setDefaultDepartment(String departmentId, String operatorId,
            Boolean autoAssignNewUsers, Boolean autoAssignUnassignedUsers) {
        Department department = departmentMapper.selectById(departmentId);
        if (department == null) {
            throw new BizException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }

        Department oldDefault = departmentMapper.selectByIsDefaultTrue();
        String beforeValue = null;
        String operationType = OPERATION_SET_DEFAULT;

        if (oldDefault != null && oldDefault.getId().equals(departmentId)) {
            throw new BizException(ErrorCode.OPERATION_FAILED, "该部门已经是默认部门");
        }

        if (oldDefault != null) {
            beforeValue = buildDepartmentChangeLog(oldDefault, null, "unset");
            String afterValueForUnset = buildDepartmentChangeLog(oldDefault, null, "unset");
            operationType = OPERATION_SET_DEFAULT;
            oldDefault.setIsDefault(false);
            departmentMapper.updateById(oldDefault);
            logOperation(oldDefault.getId(), TARGET_TYPE_DEPARTMENT,
                    OPERATION_UNSET_DEFAULT, beforeValue,
                    afterValueForUnset,
                    String.format("取消默认部门: %s", oldDefault.getName()), operatorId);
        }

        String afterValue = buildDepartmentChangeLog(department, null, "set");
        department.setIsDefault(true);
        departmentMapper.updateById(department);

        logOperation(department.getId(), TARGET_TYPE_DEPARTMENT, operationType,
                beforeValue, afterValue,
                String.format("设置默认部门: %s", department.getName()), operatorId);

        if (autoAssignNewUsers != null) {
            defaultDepartmentProperties.setAutoAssignNewUsers(autoAssignNewUsers);
        }
        if (autoAssignUnassignedUsers != null) {
            defaultDepartmentProperties.setAutoAssignUnassignedUsers(autoAssignUnassignedUsers);
        }

        log.info("Default department set: id={}, name={}, operator={}",
                department.getId(), department.getName(), operatorId);

        return DepartmentDTO.fromEntity(department);
    }

    @Transactional
    public void unsetDefaultDepartment(String departmentId, String operatorId) {
        Department department = departmentMapper.selectById(departmentId);
        if (department == null) {
            throw new BizException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }

        if (!Boolean.TRUE.equals(department.getIsDefault())) {
            throw new BizException(ErrorCode.OPERATION_FAILED, "该部门不是默认部门");
        }

        String beforeValue = buildDepartmentChangeLog(department, null, "set");
        department.setIsDefault(false);
        departmentMapper.updateById(department);

        logOperation(department.getId(), TARGET_TYPE_DEPARTMENT, OPERATION_UNSET_DEFAULT,
                beforeValue, buildDepartmentChangeLog(department, null, "unset"),
                String.format("取消默认部门: %s", department.getName()), operatorId);

        log.info("Default department unset: id={}, operator={}", departmentId, operatorId);
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getUnassignedUsers() {
        List<UserDepartment> allUserDepartments = userDepartmentMapper.selectList(null);
        Set<String> assignedUserIds = allUserDepartments.stream()
                .map(UserDepartment::getUserId)
                .collect(Collectors.toSet());

        List<User> unassignedUsers;
        if (assignedUserIds.isEmpty()) {
            unassignedUsers = userMapper.selectList(null);
        } else {
            List<User> allUsers = userMapper.selectList(null);
            unassignedUsers = allUsers.stream()
                    .filter(user -> !assignedUserIds.contains(user.getId()))
                    .collect(Collectors.toList());
        }

        return unassignedUsers.stream()
                .map(UserDTO::fromUser)
                .collect(Collectors.toList());
    }

    @Transactional
    public int assignUnassignedUsersToDefault(String operatorId) {
        Department defaultDept = departmentMapper.selectByIsDefaultTrue();
        if (defaultDept == null) {
            throw new BizException(ErrorCode.DEPARTMENT_NOT_FOUND, "系统未设置默认部门");
        }

        List<UserDepartment> allUserDepartments = userDepartmentMapper.selectList(null);
        Set<String> assignedUserIds = allUserDepartments.stream()
                .map(UserDepartment::getUserId)
                .collect(Collectors.toSet());

        List<User> allUsers = userMapper.selectList(null);
        List<User> unassignedUsers = allUsers.stream()
                .filter(user -> !assignedUserIds.contains(user.getId()))
                .collect(Collectors.toList());

        if (unassignedUsers.isEmpty()) {
            log.info("No unassigned users found");
            return 0;
        }

        int count = 0;
        for (User user : unassignedUsers) {
            try {
                assignUserToDepartment(user.getId(), defaultDept.getId(), true, null, operatorId, true);
                count++;
            } catch (Exception e) {
                log.warn("Failed to assign user {} to default department: {}",
                        user.getUsername(), e.getMessage());
            }
        }

        log.info("Assigned {} unassigned users to default department, operator={}",
                count, operatorId);
        return count;
    }

    @Transactional
    public Map<String, Object> batchAssignUsersToDepartment(
            String departmentId, BatchAssignUserRequest request, String operatorId) {

        Department department = departmentMapper.selectById(departmentId);
        if (department == null) {
            throw new BizException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }

        List<String> userIds = request.getUserIds();
        Boolean isPrimary = request.getIsPrimary() != null ? request.getIsPrimary() : false;
        String position = request.getPosition();

        Map<String, Object> result = new HashMap<>();
        List<String> successUsers = new ArrayList<>();
        Map<String, String> failedUsers = new HashMap<>();

        for (String userId : userIds) {
            try {
                assignUserToDepartment(userId, departmentId, isPrimary, position, operatorId, false);
                successUsers.add(userId);
            } catch (BizException e) {
                failedUsers.put(userId, e.getMessage());
            } catch (Exception e) {
                failedUsers.put(userId, "分配失败: " + e.getMessage());
            }
        }

        if (!successUsers.isEmpty()) {
            String afterValue = buildBatchAssignLog(department, successUsers, isPrimary, position);
            logOperation(departmentId, TARGET_TYPE_DEPARTMENT, OPERATION_BATCH_ASSIGN,
                    null, afterValue,
                    String.format("批量分配%d个用户到部门: %s", successUsers.size(), department.getName()),
                    operatorId);
        }

        result.put("successCount", successUsers.size());
        result.put("failedCount", failedUsers.size());
        result.put("successUserIds", successUsers);
        result.put("failedUsers", failedUsers);

        log.info("Batch assign users to department: deptId={}, success={}, failed={}, operator={}",
                departmentId, successUsers.size(), failedUsers.size(), operatorId);

        return result;
    }

    @Transactional
    public void assignUserToDepartment(String userId, String departmentId,
            Boolean isPrimary, String position, String operatorId, boolean isAutoAssign) {

        if (userMapper.selectById(userId) == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        if (departmentMapper.selectById(departmentId) == null) {
            throw new BizException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }

        if (userDepartmentMapper.existsByUserIdAndDepartmentId(userId, departmentId) > 0) {
            throw new BizException(ErrorCode.USER_ALREADY_EXISTS, "用户已在该部门中");
        }

        if (Boolean.TRUE.equals(isPrimary)) {
            UserDepartment primaryUd = userDepartmentMapper.selectByUserIdAndIsPrimaryTrue(userId);
            if (primaryUd != null) {
                primaryUd.setIsPrimary(false);
                userDepartmentMapper.updateById(primaryUd);
            }
        }

        UserDepartment userDepartment = UserDepartment.builder()
                .userId(userId)
                .departmentId(departmentId)
                .isPrimary(isPrimary != null ? isPrimary : false)
                .position(position)
                .build();

        userDepartmentMapper.insert(userDepartment);

        String operationType = isAutoAssign ? OPERATION_AUTO_ASSIGN : OPERATION_ASSIGN_USER;
        String description = isAutoAssign ?
                "自动分配用户到默认部门" : "手动分配用户到部门";

        logOperation(userId, TARGET_TYPE_USER, operationType,
                null, buildUserDepartmentLog(userId, departmentId, isPrimary, position),
                description, operatorId);

        log.info("User assigned to department: userId={}, deptId={}, isPrimary={}, operator={}",
                userId, departmentId, isPrimary, operatorId);
    }

    @Transactional(readOnly = true)
    public List<DepartmentConfigLogDTO> getConfigLogsByTarget(String targetType, String targetId) {
        List<DepartmentConfigLog> logs =
                configLogMapper.selectByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId);

        return logs.stream()
                .map(log -> {
                    DepartmentConfigLogDTO dto = DepartmentConfigLogDTO.fromEntity(log);
                    User operator = userMapper.selectById(log.getOperatorId());
                    if (operator != null) {
                        dto.setOperatorUsername(operator.getUsername());
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public com.baomidou.mybatisplus.core.metadata.IPage<DepartmentConfigLogDTO> getConfigLogs(
            int page, int pageSize, String operatorId, String targetType, String targetId,
            String operationType, java.time.LocalDateTime startTime, java.time.LocalDateTime endTime) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<DepartmentConfigLog> mpPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize);
        com.baomidou.mybatisplus.core.metadata.IPage<DepartmentConfigLog> logs = configLogMapper.selectPaginated(mpPage);

        return logs.convert(log -> {
            DepartmentConfigLogDTO dto = DepartmentConfigLogDTO.fromEntity(log);
            User operator = userMapper.selectById(log.getOperatorId());
            if (operator != null) {
                dto.setOperatorUsername(operator.getUsername());
            }
            return dto;
        });
    }

    private void logOperation(String targetId, String targetType, String operationType,
            String beforeValue, String afterValue, String description, String operatorId) {

        DepartmentConfigLog configLog = DepartmentConfigLog.builder()
                .operatorId(operatorId)
                .targetType(targetType)
                .targetId(targetId)
                .operationType(operationType)
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .description(description)
                .build();

        configLogMapper.insert(configLog);
    }

    private String buildDepartmentChangeLog(Department department, String changes, String action) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("departmentId", department.getId());
        logData.put("departmentCode", department.getCode());
        logData.put("departmentName", department.getName());
        logData.put("action", action);
        if (changes != null) {
            logData.put("changes", changes);
        }
        String json = jsonUtil.toJson(logData);
        return json != null ? json : "{}";
    }

    private String buildUserDepartmentLog(String userId, String departmentId,
            Boolean isPrimary, String position) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("userId", userId);
        logData.put("departmentId", departmentId);
        logData.put("isPrimary", isPrimary);
        if (position != null) {
            logData.put("position", position);
        }
        String json = jsonUtil.toJson(logData);
        return json != null ? json : "{}";
    }

    private String buildBatchAssignLog(Department department, List<String> userIds,
            Boolean isPrimary, String position) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("departmentId", department.getId());
        logData.put("departmentName", department.getName());
        logData.put("userCount", userIds.size());
        logData.put("userIds", userIds);
        logData.put("isPrimary", isPrimary);
        if (position != null) {
            logData.put("position", position);
        }
        String json = jsonUtil.toJson(logData);
        return json != null ? json : "{}";
    }
}
