package com.clacjz.medical.rag.mapper;

import org.apache.ibatis.annotations.Insert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DocumentChunkMapper SQL一致性测试")
class DocumentChunkMapperSqlConsistencyTest {

    /**
     * 从 @Insert 注解中提取列名列表
     */
    private List<String> extractColumns(String sql) {
        // 匹配 INSERT INTO table (col1, col2, ...) 中的列名部分
        Pattern columnPattern = Pattern.compile("INSERT\\s+INTO\\s+\\w+\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = columnPattern.matcher(sql);
        assertTrue(matcher.find(), "SQL应包含INSERT INTO ... (columns) 子句: " + sql);
        String columnsPart = matcher.group(1);
        return Arrays.stream(columnsPart.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }

    private String getInsertSql(String methodName) throws NoSuchMethodException {
        Method method = DocumentChunkMapper.class.getMethod(methodName,
                methodName.equals("insertWithJsonb")
                        ? com.clacjz.medical.rag.model.entity.DocumentChunk.class
                        : java.util.List.class);
        Insert insertAnnotation = method.getAnnotation(Insert.class);
        assertNotNull(insertAnnotation, methodName + " 应有 @Insert 注解");
        return insertAnnotation.value()[0];
    }

    @Test
    @DisplayName("batchInsertWithJsonb 与 insertWithJsonb 应具有相同的列列表")
    void batchInsertHasSameColumnsAsSingleInsert() throws NoSuchMethodException {
        String singleSql = getInsertSql("insertWithJsonb");
        String batchSql = getInsertSql("batchInsertWithJsonb");

        List<String> singleColumns = extractColumns(singleSql);
        List<String> batchColumns = extractColumns(batchSql);

        assertEquals(singleColumns, batchColumns,
                "batchInsertWithJsonb 的列列表应与 insertWithJsonb 完全一致，" +
                "避免因列不一致导致批量插入时字段错位");
    }

    @Test
    @DisplayName("insertWithJsonb 和 batchInsertWithJsonb 的 metadata 列都应使用 ::jsonb 类型转换")
    void bothUseJsonbCastForMetadata() throws NoSuchMethodException {
        String singleSql = getInsertSql("insertWithJsonb");
        String batchSql = getInsertSql("batchInsertWithJsonb");

        // 单条插入中 metadata 应有 ::jsonb
        assertTrue(singleSql.contains("::jsonb"),
                "insertWithJsonb 的 metadata 值应使用 ::jsonb 类型转换，实际SQL: " + singleSql);

        // 批量插入中 metadata 应有 ::jsonb
        assertTrue(batchSql.contains("::jsonb"),
                "batchInsertWithJsonb 的 metadata 值应使用 ::jsonb 类型转换，实际SQL: " + batchSql);

        // 验证 ::jsonb 出现在 metadata 对应的位置（紧跟 #{metadata} 或 #{chunk.metadata} 之后）
        assertTrue(singleSql.contains("#{metadata}::jsonb"),
                "insertWithJsonb 中 ::jsonb 应紧跟 #{metadata}，实际SQL: " + singleSql);
        assertTrue(batchSql.contains("#{chunk.metadata}::jsonb"),
                "batchInsertWithJsonb 中 ::jsonb 应紧跟 #{chunk.metadata}，实际SQL: " + batchSql);
    }

    @Test
    @DisplayName("batchInsertWithJsonb 应使用 <foreach> 实现批量插入")
    void batchInsertUsesForeach() throws NoSuchMethodException {
        String batchSql = getInsertSql("batchInsertWithJsonb");

        assertTrue(batchSql.contains("<foreach"),
                "batchInsertWithJsonb 应使用 <foreach> 标签实现批量插入，实际SQL: " + batchSql);
        assertTrue(batchSql.contains("collection='chunks'"),
                "<foreach> 的 collection 属性应为 'chunks'，实际SQL: " + batchSql);
        assertTrue(batchSql.contains("item='chunk'"),
                "<foreach> 的 item 属性应为 'chunk'，实际SQL: " + batchSql);
    }
}
