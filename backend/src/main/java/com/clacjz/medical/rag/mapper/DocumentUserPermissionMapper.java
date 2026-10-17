package com.clacjz.medical.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clacjz.medical.rag.model.entity.DocumentUserPermission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentUserPermissionMapper extends BaseMapper<DocumentUserPermission> {

    @Select("SELECT * FROM document_user_permissions WHERE document_id = #{documentId} AND user_id = #{userId} LIMIT 1")
    DocumentUserPermission selectByDocumentIdAndUserId(@Param("documentId") String documentId, @Param("userId") String userId);

    @Select("SELECT * FROM document_user_permissions WHERE document_id = #{documentId}")
    List<DocumentUserPermission> selectByDocumentId(@Param("documentId") String documentId);

    @Select("SELECT EXISTS(SELECT 1 FROM document_user_permissions WHERE document_id = #{documentId} AND user_id = #{userId})")
    boolean existsByDocumentIdAndUserId(@Param("documentId") String documentId, @Param("userId") String userId);

    @Delete("DELETE FROM document_user_permissions WHERE document_id = #{documentId}")
    void deleteByDocumentId(@Param("documentId") String documentId);

    @Delete("DELETE FROM document_user_permissions WHERE document_id = #{documentId} AND user_id = #{userId}")
    void deleteByDocumentIdAndUserId(@Param("documentId") String documentId, @Param("userId") String userId);

    @Delete("DELETE FROM document_user_permissions WHERE user_id = #{userId}")
    void deleteByUserId(@Param("userId") String userId);
}