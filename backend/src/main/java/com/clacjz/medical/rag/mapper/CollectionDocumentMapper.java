package com.clacjz.medical.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.clacjz.medical.rag.model.entity.CollectionDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CollectionDocumentMapper extends BaseMapper<CollectionDocument> {

    @Select("SELECT cd.* FROM collection_documents cd " +
            "JOIN documents d ON cd.document_id = d.id " +
            "WHERE cd.collection_id = #{collectionId} AND d.is_deleted = false")
    List<CollectionDocument> selectByCollectionId(@Param("collectionId") String collectionId);

    @Select("SELECT cd.* FROM collection_documents cd " +
            "JOIN documents d ON cd.document_id = d.id " +
            "WHERE cd.collection_id = #{collectionId} AND cd.document_id = #{documentId} AND d.is_deleted = false LIMIT 1")
    CollectionDocument selectByCollectionIdAndDocumentId(@Param("collectionId") String collectionId, @Param("documentId") String documentId);

    @Select("SELECT EXISTS(SELECT 1 FROM collection_documents cd " +
            "JOIN documents d ON cd.document_id = d.id " +
            "WHERE cd.collection_id = #{collectionId} AND cd.document_id = #{documentId} AND d.is_deleted = false)")
    boolean existsByCollectionIdAndDocumentId(@Param("collectionId") String collectionId, @Param("documentId") String documentId);

    @Select("DELETE FROM collection_documents WHERE collection_id = #{collectionId}")
    void deleteByCollectionId(@Param("collectionId") String collectionId);

    @Select("<script>" +
            "SELECT cd.* FROM collection_documents cd " +
            "JOIN documents d ON cd.document_id = d.id " +
            "WHERE cd.collection_id = #{collectionId} AND d.is_deleted = false " +
            "<if test='status != null and status != \"\"'>" +
            " AND cd.processing_status = #{status}" +
            "</if>" +
            " ORDER BY cd.added_at DESC" +
            "</script>")
    List<CollectionDocument> selectByCollectionIdWithFilter(@Param("collectionId") String collectionId, @Param("status") String status);

    /**
     * 分页查询Collection中未删除的文档（JOIN documents表过滤is_deleted）
     */
    @Select("<script>" +
            "SELECT cd.* FROM collection_documents cd " +
            "JOIN documents d ON cd.document_id = d.id " +
            "WHERE cd.collection_id = #{collectionId} AND d.is_deleted = false " +
            "<if test='status != null and status != \"\"'>" +
            " AND cd.processing_status = #{status}" +
            "</if>" +
            " ORDER BY cd.added_at DESC" +
            "</script>")
    IPage<CollectionDocument> selectPageByCollectionIdWithFilter(IPage<CollectionDocument> page,
                                                                  @Param("collectionId") String collectionId,
                                                                  @Param("status") String status);
}