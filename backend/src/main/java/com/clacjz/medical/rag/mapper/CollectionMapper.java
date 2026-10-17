package com.clacjz.medical.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clacjz.medical.rag.model.entity.Collection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CollectionMapper extends BaseMapper<Collection> {

    @Select("SELECT * FROM collections WHERE department_id = #{departmentId} AND deleted_at IS NULL ORDER BY created_at DESC")
    List<Collection> selectActiveByDepartmentId(@Param("departmentId") String departmentId);

    @Select("SELECT * FROM collections WHERE department_id = #{departmentId} AND type = #{type}::collection_type AND deleted_at IS NULL ORDER BY created_at DESC")
    List<Collection> selectByDepartmentIdAndType(@Param("departmentId") String departmentId, @Param("type") String type);

    @Select("SELECT * FROM collections WHERE department_id = #{departmentId} AND status = #{status}::varchar AND deleted_at IS NULL ORDER BY created_at DESC")
    List<Collection> selectByDepartmentIdAndStatus(@Param("departmentId") String departmentId, @Param("status") String status);

    @Select("SELECT * FROM collections WHERE deleted_at IS NULL ORDER BY created_at DESC")
    List<Collection> selectAllActive();

    @Select("SELECT * FROM collections WHERE name = #{name} AND deleted_at IS NULL LIMIT 1")
    Collection selectByNameAndDeletedAtIsNull(@Param("name") String name);

    @Select("SELECT EXISTS(SELECT 1 FROM collections WHERE name = #{name} AND deleted_at IS NULL)")
    boolean existsByNameAndDeletedAtIsNull(@Param("name") String name);

    @Select("<script>SELECT * FROM collections WHERE id IN <foreach item='id' collection=\"ids\" open='(' separator=',' close=')'>#{id}</foreach> AND deleted_at IS NULL</script>")
    List<Collection> selectActiveByIds(@Param("ids") List<String> ids);
}