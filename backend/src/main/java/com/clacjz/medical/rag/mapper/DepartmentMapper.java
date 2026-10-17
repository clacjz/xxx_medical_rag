package com.clacjz.medical.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clacjz.medical.rag.model.entity.Department;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DepartmentMapper extends BaseMapper<Department> {

    @Select("SELECT * FROM departments WHERE is_default = true LIMIT 1")
    Department selectByIsDefaultTrue();

    @Select("SELECT * FROM departments WHERE code = #{code}")
    Department selectByCode(@Param("code") String code);

    @Select("SELECT * FROM departments WHERE parent_id = #{parentId}")
    List<Department> selectByParentId(@Param("parentId") String parentId);

    @Select("SELECT * FROM departments WHERE parent_id IS NULL")
    List<Department> selectRootDepartments();

    @Select("SELECT COUNT(*) FROM user_departments WHERE department_id = #{departmentId}")
    long countUsersByDepartmentId(@Param("departmentId") String departmentId);

    @Select("SELECT EXISTS(SELECT 1 FROM departments WHERE code = #{code})")
    boolean existsByCode(@Param("code") String code);
}