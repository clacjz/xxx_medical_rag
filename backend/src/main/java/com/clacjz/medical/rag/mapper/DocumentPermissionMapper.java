package com.clacjz.medical.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clacjz.medical.rag.model.entity.DocumentPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DocumentPermissionMapper extends BaseMapper<DocumentPermission> {

    @Select("SELECT * FROM document_permissions WHERE document_id = #{documentId} LIMIT 1")
    DocumentPermission selectByDocumentId(@Param("documentId") String documentId);
}