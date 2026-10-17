package com.clacjz.medical.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clacjz.medical.rag.model.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    @Select("<script>" +
            "SELECT * FROM audit_logs " +
            "<where>" +
            "<if test='userId != null'> AND user_id = #{userId} </if>" +
            "<if test='action != null'> AND action = #{action} </if>" +
            "<if test='level != null'> AND level = #{level} </if>" +
            "<if test='startTime != null'> AND timestamp >= #{startTime} </if>" +
            "<if test='endTime != null'> AND timestamp &lt;= #{endTime} </if>" +
            "</where>" +
            "ORDER BY timestamp DESC" +
            "</script>")
    IPage<AuditLog> selectByFilters(
            @Param("userId") String userId,
            @Param("action") String action,
            @Param("level") String level,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Page<AuditLog> page);

    @Select("<script>" +
            "DELETE FROM audit_logs WHERE category = #{category} AND timestamp &lt; #{retentionDate}" +
            "</script>")
    int deleteExpiredByCategory(@Param("category") String category, @Param("retentionDate") LocalDateTime retentionDate);

    @Select("SELECT COUNT(*) FROM audit_logs WHERE category = #{category}")
    long countByCategory(@Param("category") String category);
}