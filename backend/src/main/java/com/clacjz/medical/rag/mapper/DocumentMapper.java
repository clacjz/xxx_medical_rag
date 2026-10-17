package com.clacjz.medical.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.clacjz.medical.rag.model.entity.Document;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentMapper extends BaseMapper<Document> {

    @Select("SELECT * FROM documents WHERE is_deleted = false AND title ILIKE CONCAT('%', REPLACE(REPLACE(#{keyword}, '\\', '\\\\'), '%', '\\%'), '%') ESCAPE '\\' ORDER BY created_at DESC")
    List<Document> selectByTitleContainingIgnoreCase(IPage<Document> page, @Param("keyword") String keyword);

    @Select("SELECT d.* FROM documents d " +
            "JOIN collection_documents cd ON d.id = cd.document_id " +
            "WHERE d.is_deleted = false AND cd.collection_id = #{collectionId} ORDER BY d.created_at DESC")
    List<Document> selectByCollectionId(IPage<Document> page, @Param("collectionId") String collectionId);

    @Select("SELECT * FROM documents WHERE is_deleted = false AND status = #{status} ORDER BY created_at DESC")
    List<Document> selectByStatus(IPage<Document> page, @Param("status") String status);

    @Select("SELECT * FROM documents WHERE is_deleted = false AND status = 'active' ORDER BY created_at DESC")
    List<Document> selectAllActive();

    @Select("SELECT * FROM documents WHERE is_deleted = true ORDER BY created_at DESC")
    List<Document> selectDeletedDocuments();

    @Select("SELECT d.*, cd.collection_id AS primary_collection_id FROM documents d " +
            "LEFT JOIN collection_documents cd ON d.id = cd.document_id " +
            "WHERE d.is_deleted = false ORDER BY d.created_at DESC")
    List<Document> selectAllWithPrimaryCollection();

    @Select({"<script>",
             "SELECT d.* FROM documents d ",
             "JOIN collection_documents cd ON d.id = cd.document_id ",
             "WHERE d.is_deleted = false AND cd.collection_id IN ",
             "<foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach> ",
             "ORDER BY d.updated_at DESC LIMIT #{limit}",
             "</script>"})
    List<Document> selectRecentByCollectionIds(@Param("ids") List<String> ids, @Param("limit") int limit);
}