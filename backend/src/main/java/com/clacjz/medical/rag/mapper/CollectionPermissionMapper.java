package com.clacjz.medical.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clacjz.medical.rag.model.entity.CollectionPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CollectionPermissionMapper extends BaseMapper<CollectionPermission> {

    @Select("SELECT * FROM collection_permissions WHERE collection_id = #{collectionId} LIMIT 1")
    CollectionPermission selectByCollectionId(@Param("collectionId") String collectionId);
}