package com.clacjz.medical.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clacjz.medical.rag.model.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {

    @Select("SELECT * FROM user_roles WHERE user_id = #{userId}")
    List<UserRole> selectByUserId(@Param("userId") String userId);

    @Select("SELECT * FROM user_roles WHERE user_id = #{userId} AND is_primary = true LIMIT 1")
    UserRole selectByUserIdAndIsPrimaryTrue(@Param("userId") String userId);

    @Select("SELECT * FROM user_roles WHERE user_id = #{userId} AND role_id = #{roleId} LIMIT 1")
    UserRole selectByUserIdAndRoleId(@Param("userId") String userId, @Param("roleId") String roleId);

    @Select("SELECT EXISTS(SELECT 1 FROM user_roles WHERE user_id = #{userId} AND role_id = #{roleId})")
    boolean existsByUserIdAndRoleId(@Param("userId") String userId, @Param("roleId") String roleId);

    @Select("SELECT COUNT(*) FROM user_roles WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") String userId);

    @Select("SELECT * FROM user_roles WHERE role_id = #{roleId}")
    List<UserRole> selectByRoleId(@Param("roleId") String roleId);
}