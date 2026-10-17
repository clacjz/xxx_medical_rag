package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.config.ParserProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TempFileCleanupService 单元测试")
class TempFileCleanupServiceTest {

    @Mock
    private ParserProperties parserProperties;

    @Mock
    private ParserProperties.CleanupConfig cleanupConfig;

    @InjectMocks
    private TempFileCleanupService tempFileCleanupService;

    @Nested
    @DisplayName("cleanupFile - 清理单个文件")
    class CleanupFileTests {

        @Test
        @DisplayName("文件存在时删除文件")
        void shouldDeleteFileWhenExists() throws IOException {
            Path tempPath = Files.createTempDirectory("test");
            Path file = tempPath.resolve("test.txt");
            Files.writeString(file, "test content");
            assertTrue(Files.exists(file));

            tempFileCleanupService.cleanupFile(file.toString());

            assertFalse(Files.exists(file));
        }

        @Test
        @DisplayName("文件不存在时不抛异常")
        void shouldNotThrowWhenFileNotExists() {
            assertDoesNotThrow(() ->
                    tempFileCleanupService.cleanupFile("/nonexistent/path/file.txt"));
        }

        @Test
        @DisplayName("null路径时不抛异常")
        void shouldNotThrowWhenPathNull() {
            assertDoesNotThrow(() ->
                    tempFileCleanupService.cleanupFile(null));
        }

        @Test
        @DisplayName("空路径时不抛异常")
        void shouldNotThrowWhenPathBlank() {
            assertDoesNotThrow(() ->
                    tempFileCleanupService.cleanupFile("   "));
        }

        @Test
        @DisplayName("路径为空白字符时不抛异常")
        void shouldNotThrowWhenPathOnlyWhitespace() {
            assertDoesNotThrow(() ->
                    tempFileCleanupService.cleanupFile("\t\n  "));
        }
    }

    @Nested
    @DisplayName("cleanupOldFiles - 清理过期文件")
    class CleanupOldFilesTests {

        @Test
        @DisplayName("清理禁用时不执行")
        void shouldNotCleanupWhenDisabled() {
            when(parserProperties.getCleanup()).thenReturn(cleanupConfig);
            when(cleanupConfig.isEnabled()).thenReturn(false);

            assertDoesNotThrow(() ->
                    tempFileCleanupService.cleanupOldFiles());
        }

        @Test
        @DisplayName("目录不存在时不抛异常")
        void shouldNotThrowWhenDirNotExists() {
            when(parserProperties.getTempDir()).thenReturn("/nonexistent/dir");
            when(parserProperties.getCleanup()).thenReturn(cleanupConfig);
            when(cleanupConfig.isEnabled()).thenReturn(true);

            assertDoesNotThrow(() ->
                    tempFileCleanupService.cleanupOldFiles());
        }

        @Test
        @DisplayName("保留未过期的文件")
        void shouldKeepRecentFiles() throws IOException {
            Path tempPath = Files.createTempDirectory("test");
            when(parserProperties.getTempDir()).thenReturn(tempPath.toString());
            when(parserProperties.getCleanup()).thenReturn(cleanupConfig);
            when(cleanupConfig.isEnabled()).thenReturn(true);
            when(cleanupConfig.getRetentionHours()).thenReturn(24);

            Path recentFile = tempPath.resolve("recent_file.txt");
            Files.writeString(recentFile, "recent content");

            tempFileCleanupService.cleanupOldFiles();

            assertTrue(Files.exists(recentFile));
        }

        @Test
        @DisplayName("过期文件被删除")
        void shouldDeleteOldFiles() throws IOException {
            Path tempPath = Files.createTempDirectory("test");
            when(parserProperties.getTempDir()).thenReturn(tempPath.toString());
            when(parserProperties.getCleanup()).thenReturn(cleanupConfig);
            when(cleanupConfig.isEnabled()).thenReturn(true);
            when(cleanupConfig.getRetentionHours()).thenReturn(24);

            Path oldFile = tempPath.resolve("old_file.txt");
            Files.writeString(oldFile, "old content");
            Files.setLastModifiedTime(oldFile,
                    java.nio.file.attribute.FileTime.from(
                            Instant.now().minus(25, ChronoUnit.HOURS)));

            assertTrue(Files.exists(oldFile));

            tempFileCleanupService.cleanupOldFiles();

            assertFalse(Files.exists(oldFile));
        }

        @Test
        @DisplayName("清理空目录")
        void shouldDeleteEmptyDirectories() throws IOException {
            Path tempPath = Files.createTempDirectory("test");
            when(parserProperties.getTempDir()).thenReturn(tempPath.toString());
            when(parserProperties.getCleanup()).thenReturn(cleanupConfig);
            when(cleanupConfig.isEnabled()).thenReturn(true);
            when(cleanupConfig.getRetentionHours()).thenReturn(24);

            Path emptyDir = tempPath.resolve("empty_dir");
            Files.createDirectories(emptyDir);

            tempFileCleanupService.cleanupOldFiles();

            assertFalse(Files.exists(emptyDir));
        }

        @Test
        @DisplayName("清理精确到保留边界时间的文件")
        void shouldHandleRetentionBoundary() throws IOException {
            Path tempPath = Files.createTempDirectory("test");
            when(parserProperties.getTempDir()).thenReturn(tempPath.toString());
            when(parserProperties.getCleanup()).thenReturn(cleanupConfig);
            when(cleanupConfig.isEnabled()).thenReturn(true);
            when(cleanupConfig.getRetentionHours()).thenReturn(24);

            Path boundaryFile = tempPath.resolve("boundary_file.txt");
            Files.writeString(boundaryFile, "boundary content");
            // 设置为正好25小时前
            Files.setLastModifiedTime(boundaryFile,
                    java.nio.file.attribute.FileTime.from(
                            Instant.now().minus(25, ChronoUnit.HOURS)));

            tempFileCleanupService.cleanupOldFiles();

            // 25小时前应该被删除
            assertFalse(Files.exists(boundaryFile));
        }

        @Test
        @DisplayName("非空目录不应被删除")
        void shouldKeepNonEmptyDirectories() throws IOException {
            Path tempPath = Files.createTempDirectory("test");
            when(parserProperties.getTempDir()).thenReturn(tempPath.toString());
            when(parserProperties.getCleanup()).thenReturn(cleanupConfig);
            when(cleanupConfig.isEnabled()).thenReturn(true);
            when(cleanupConfig.getRetentionHours()).thenReturn(24);

            Path subDir = tempPath.resolve("subdir");
            Files.createDirectories(subDir);
            Path recentFile = subDir.resolve("recent.txt");
            Files.writeString(recentFile, "recent");

            tempFileCleanupService.cleanupOldFiles();

            assertTrue(Files.exists(subDir));
            assertTrue(Files.exists(recentFile));
        }

        @Test
        @DisplayName("嵌套目录中的过期文件应被清理")
        void shouldCleanNestedOldFiles() throws IOException {
            Path tempPath = Files.createTempDirectory("test");
            when(parserProperties.getTempDir()).thenReturn(tempPath.toString());
            when(parserProperties.getCleanup()).thenReturn(cleanupConfig);
            when(cleanupConfig.isEnabled()).thenReturn(true);
            when(cleanupConfig.getRetentionHours()).thenReturn(24);

            Path subDir = tempPath.resolve("nested");
            Files.createDirectories(subDir);
            Path oldFile = subDir.resolve("old.txt");
            Files.writeString(oldFile, "old");
            Files.setLastModifiedTime(oldFile,
                    java.nio.file.attribute.FileTime.from(
                            Instant.now().minus(48, ChronoUnit.HOURS)));

            tempFileCleanupService.cleanupOldFiles();

            assertFalse(Files.exists(oldFile));
        }
    }

    @Nested
    @DisplayName("ensureTempDirectory - 确保临时目录存在")
    class EnsureTempDirectoryTests {

        @Test
        @DisplayName("目录不存在时创建目录")
        void shouldCreateDirectoryWhenNotExists() throws IOException {
            Path tempBase = Files.createTempDirectory("testbase");
            Path newDir = tempBase.resolve("new_temp_dir");
            when(parserProperties.getTempDir()).thenReturn(newDir.toString());

            Path result = tempFileCleanupService.ensureTempDirectory();

            assertTrue(Files.exists(result));
            assertEquals(newDir, result);
        }

        @Test
        @DisplayName("目录已存在时直接返回")
        void shouldReturnExistingDirectory() throws IOException {
            Path tempPath = Files.createTempDirectory("test");
            when(parserProperties.getTempDir()).thenReturn(tempPath.toString());

            Path result = tempFileCleanupService.ensureTempDirectory();

            assertEquals(tempPath, result);
            assertTrue(Files.exists(result));
        }
    }
}
