package com.clacjz.medical.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clacjz.medical.rag.model.entity.DepartmentConfigLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DepartmentConfigLogMapper extends BaseMapper<DepartmentConfigLog> {

    @Select("SELECT * FROM department_config_logs WHERE operator_id = #{operatorId} ORDER BY created_at DESC")
    List<DepartmentConfigLog> selectByOperatorIdOrderByCreatedAtDesc(@Param("operatorId") String operatorId);

    @Select("SELECT * FROM department_config_logs WHERE operation_type = #{operationType} ORDER BY created_at DESC")
    List<DepartmentConfigLog> selectByOperationTypeOrderByCreatedAtDesc(@Param("operationType") String operationType);

    @Select("SELECT * FROM department_config_logs WHERE target_type = #{targetType} AND target_id = #{targetId} ORDER BY created_at DESC")
    List<DepartmentConfigLog> selectByTargetTypeAndTargetIdOrderByCreatedAtDesc(
            @Param("targetType") String targetType,
            @Param("targetId") String targetId);

    @Select("SELECT * FROM department_config_logs ORDER BY created_at DESC")
    IPage<DepartmentConfigLog> selectPaginated(Page<DepartmentConfigLog> page);
}