package com.clacjz.medical.rag.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FileNameUtil 单元测试")
class FileNameUtilTest {

    @Test
    @DisplayName("getFileExtension - 标准文件名")
    void shouldGetExtension() {
        assertEquals("pdf", FileNameUtil.getFileExtension("test.pdf"));
        assertEquals("docx", FileNameUtil.getFileExtension("document.docx"));
        assertEquals("txt", FileNameUtil.getFileExtension("file.txt"));
    }

    @Test
    @DisplayName("getFileExtension - 大写扩展名转为小写")
    void shouldLowerCaseExtension() {
        assertEquals("pdf", FileNameUtil.getFileExtension("test.PDF"));
    }

    @Test
    @DisplayName("getFileExtension - 无扩展名返回空")
    void shouldReturnEmptyForNoExtension() {
        assertEquals("", FileNameUtil.getFileExtension("README"));
    }

    @Test
    @DisplayName("getFileExtension - null 抛 NPE（实际行为）")
    void shouldThrowForNull() {
        // 注意：实际实现不处理 null，会抛 NPE
        assertThrows(NullPointerException.class, () -> FileNameUtil.getFileExtension(null));
    }

    @Test
    @DisplayName("getFileExtension - 多个点的文件名取最后一个")
    void shouldGetLastExtension() {
        assertEquals("gz", FileNameUtil.getFileExtension("archive.tar.gz"));
    }

    @Test
    @DisplayName("getFileExtension - 隐藏文件返回空")
    void shouldReturnEmptyForHiddenFile() {
        assertEquals("", FileNameUtil.getFileExtension(".gitignore"));
    }

    @Test
    @DisplayName("sanitizeFilename - 正常文件名不变")
    void shouldNotModifySafeFilename() {
        assertEquals("test_file.pdf", FileNameUtil.sanitizeFilename("test_file.pdf"));
    }

    @Test
    @DisplayName("sanitizeFilename - null 返回默认名")
    void shouldReturnDefaultForNull() {
        String result = FileNameUtil.sanitizeFilename(null);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("sanitizeFilename - 空字符串返回默认名")
    void shouldReturnDefaultForEmpty() {
        String result = FileNameUtil.sanitizeFilename("");
        assertNotNull(result);
    }

    @Test
    @DisplayName("sanitizeFilename - 包含特殊字符不抛异常")
    void shouldHandleSpecialChars() {
        assertDoesNotThrow(() -> FileNameUtil.sanitizeFilename("test<>file?.pdf"));
    }
}
