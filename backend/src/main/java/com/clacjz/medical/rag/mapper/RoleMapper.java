package com.clacjz.medical.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clacjz.medical.rag.model.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    @Select("SELECT * FROM roles WHERE code = #{code} LIMIT 1")
    Role selectByCode(@Param("code") String code);

    @Select("SELECT * FROM roles WHERE level = #{level}")
    List<Role> selectByLevel(@Param("level") Integer level);

    @Select("SELECT * FROM roles WHERE active = true ORDER BY level ASC")
    List<Role> selectAllActiveOrderByLevel();

    @Select("SELECT COUNT(*) FROM user_roles WHERE role_id = #{roleId}")
    long countUsersByRoleId(@Param("roleId") String roleId);

    /**
     * 批量统计每个角色的用户数量，避免N+1查询。
     * 返回Object数组列表，每个元素为[roleId, count]。
     */
    @Select("SELECT role_id, COUNT(*) as cnt FROM user_roles GROUP BY role_id")
    List<Map<String, Object>> countUsersByRoleIds();

    @Select("SELECT EXISTS(SELECT 1 FROM roles WHERE code = #{code})")
    boolean existsByCode(@Param("code") String code);
}