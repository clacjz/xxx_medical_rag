package com.clacjz.medical.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clacjz.medical.rag.model.entity.PermissionAuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PermissionAuditLogMapper extends BaseMapper<PermissionAuditLog> {

    @Select("<script>" +
            "SELECT * FROM permission_audit_logs WHERE 1=1" +
            "<if test='operatorId != null'> AND operator_id = #{operatorId}</if>" +
            "<if test='targetType != null'> AND target_type = #{targetType}::audit_target_type</if>" +
            "<if test='targetId != null'> AND target_id = #{targetId}</if>" +
            "<if test='operationType != null'> AND operation_type = #{operationType}::audit_operation_type</if>" +
            "<if test='startTime != null'> AND created_at &gt;= #{startTime}</if>" +
            "<if test='endTime != null'> AND created_at &lt;= #{endTime}</if>" +
            "ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<PermissionAuditLog> selectByFilters(
            @Param("operatorId") String operatorId,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("operationType") String operationType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select("<script>" +
            "SELECT COUNT(*) FROM permission_audit_logs WHERE 1=1" +
            "<if test='operatorId != null'> AND operator_id = #{operatorId}</if>" +
            "<if test='targetType != null'> AND target_type = #{targetType}::audit_target_type</if>" +
            "<if test='targetId != null'> AND target_id = #{targetId}</if>" +
            "<if test='operationType != null'> AND operation_type = #{operationType}::audit_operation_type</if>" +
            "<if test='startTime != null'> AND created_at &gt;= #{startTime}</if>" +
            "<if test='endTime != null'> AND created_at &lt;= #{endTime}</if>" +
            "</script>")
    long countByFilters(
            @Param("operatorId") String operatorId,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("operationType") String operationType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("SELECT * FROM permission_audit_logs WHERE " +
            "target_type = #{targetType}::audit_target_type AND target_id = #{targetId} " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<PermissionAuditLog> selectRecentByTarget(
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM permission_audit_logs")
    long countAll();
}