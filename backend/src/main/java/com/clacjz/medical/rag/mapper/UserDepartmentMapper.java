package com.clacjz.medical.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clacjz.medical.rag.model.entity.UserDepartment;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserDepartmentMapper extends BaseMapper<UserDepartment> {

    @Select("SELECT * FROM user_departments WHERE user_id = #{userId} AND is_primary = true")
    UserDepartment selectByUserIdAndIsPrimaryTrue(@Param("userId") String userId);

    @Select("SELECT * FROM user_departments WHERE user_id = #{userId}")
    List<UserDepartment> selectByUserId(@Param("userId") String userId);

    @Select("SELECT * FROM user_departments WHERE department_id = #{departmentId}")
    List<UserDepartment> selectByDepartmentId(@Param("departmentId") String departmentId);

    @Select("SELECT COUNT(*) FROM user_departments WHERE user_id = #{userId} AND department_id = #{departmentId}")
    int existsByUserIdAndDepartmentId(@Param("userId") String userId, @Param("departmentId") String departmentId);

    @Select("SELECT department_id FROM user_departments WHERE user_id = #{userId}")
    List<String> selectDepartmentIdsByUserId(@Param("userId") String userId);

    @Delete("DELETE FROM user_departments WHERE user_id = #{userId} AND department_id = #{departmentId}")
    void deleteByUserIdAndDepartmentId(@Param("userId") String userId, @Param("departmentId") String departmentId);
}