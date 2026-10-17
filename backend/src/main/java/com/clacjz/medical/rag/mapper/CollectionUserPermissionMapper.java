package com.clacjz.medical.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clacjz.medical.rag.model.entity.CollectionUserPermission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CollectionUserPermissionMapper extends BaseMapper<CollectionUserPermission> {

    @Select("SELECT * FROM collection_user_permissions WHERE collection_id = #{collectionId}")
    List<CollectionUserPermission> selectByCollectionId(@Param("collectionId") String collectionId);

    @Select("SELECT * FROM collection_user_permissions WHERE collection_id = #{collectionId} AND user_id = #{userId}")
    List<CollectionUserPermission> selectByCollectionIdAndUserId(@Param("collectionId") String collectionId, @Param("userId") String userId);

    @Delete("DELETE FROM collection_user_permissions WHERE user_id = #{userId}")
    void deleteByUserId(@Param("userId") String userId);
}