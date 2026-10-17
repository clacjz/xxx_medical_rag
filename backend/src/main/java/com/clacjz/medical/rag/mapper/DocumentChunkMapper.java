package com.clacjz.medical.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clacjz.medical.rag.model.entity.DocumentChunk;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    @Select("SELECT * FROM document_chunks WHERE document_id = #{documentId} ORDER BY chunk_index")
    List<DocumentChunk> selectByDocumentId(@Param("documentId") String documentId);

    @Select("SELECT * FROM document_chunks WHERE point_id = #{pointId}")
    DocumentChunk selectByPointId(@Param("pointId") String pointId);

    @Delete("DELETE FROM document_chunks WHERE document_id = #{documentId}")
    int deleteByDocumentId(@Param("documentId") String documentId);

    /**
     * 批量查询多个文档的 chunks，用于 ES-only 结果补全
     */
    @Select("<script>SELECT * FROM document_chunks WHERE document_id IN " +
            "<foreach item='id' collection='documentIds' open='(' separator=',' close=')'>" +
            "#{id}</foreach> ORDER BY document_id, chunk_index</script>")
    List<DocumentChunk> selectByDocumentIds(@Param("documentIds") List<String> documentIds);

    /**
     * 插入 chunk（metadata 需要 cast 到 jsonb）
     */
    @Insert("INSERT INTO document_chunks (id, document_id, point_id, chunk_index, content, chunk_size, " +
            "start_pos, end_pos, start_page, end_page, metadata, created_at, updated_at) " +
            "VALUES (#{id}, #{documentId}, #{pointId}, #{chunkIndex}, #{content}, #{chunkSize}, " +
            "#{startPos}, #{endPos}, #{startPage}, #{endPage}, #{metadata}::jsonb, #{createdAt}, #{updatedAt})")
    int insertWithJsonb(DocumentChunk chunk);

    /**
     * 批量插入 chunks（metadata 需要 cast 到 jsonb）
     */
    @Insert("<script>" +
            "INSERT INTO document_chunks (id, document_id, point_id, chunk_index, content, chunk_size, " +
            "start_pos, end_pos, start_page, end_page, metadata, created_at, updated_at) VALUES " +
            "<foreach item='chunk' collection='chunks' separator=','>" +
            "(#{chunk.id}, #{chunk.documentId}, #{chunk.pointId}, #{chunk.chunkIndex}, #{chunk.content}, #{chunk.chunkSize}, " +
            "#{chunk.startPos}, #{chunk.endPos}, #{chunk.startPage}, #{chunk.endPage}, #{chunk.metadata}::jsonb, #{chunk.createdAt}, #{chunk.updatedAt})" +
            "</foreach>" +
            "</script>")
    int batchInsertWithJsonb(@Param("chunks") List<DocumentChunk> chunks);
}