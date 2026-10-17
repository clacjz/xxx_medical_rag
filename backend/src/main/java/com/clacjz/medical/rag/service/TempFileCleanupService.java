package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.config.ParserProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class TempFileCleanupService {

    private final ParserProperties parserProperties;

    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOldFiles() {
        if (!parserProperties.getCleanup().isEnabled()) {
            log.debug("Temp file cleanup is disabled");
            return;
        }

        Path tempDir = Paths.get(parserProperties.getTempDir());
        if (!Files.exists(tempDir)) {
            log.debug("Temp directory does not exist: {}", tempDir);
            return;
        }

        int retentionHours = parserProperties.getCleanup().getRetentionHours();
        Instant cutoffTime = Instant.now().minus(retentionHours, ChronoUnit.HOURS);

        AtomicInteger deletedCount = new AtomicInteger(0);
        AtomicLong deletedSize = new AtomicLong(0);

        try {
            Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (attrs.isRegularFile()) {
                        Instant lastModified = attrs.lastModifiedTime().toInstant();
                        if (lastModified.isBefore(cutoffTime)) {
                            long size = Files.size(file);
                            Files.delete(file);
                            deletedCount.incrementAndGet();
                            deletedSize.addAndGet(size);
                            log.debug("Deleted temp file: {} (size: {} bytes)", file.getFileName(), size);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    try (var stream = Files.list(dir)) {
                        if (stream.findFirst().isEmpty()) {
                            Files.delete(dir);
                            log.debug("Deleted empty temp directory: {}", dir.getFileName());
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            if (deletedCount.get() > 0) {
                log.info("Temp file cleanup completed: {} files deleted, {} bytes freed",
                        deletedCount.get(), deletedSize.get());
            }
        } catch (IOException e) {
            log.error("Error during temp file cleanup", e);
        }
    }

    public void cleanupFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }

        Path path = Paths.get(filePath);
        try {
            if (Files.exists(path)) {
                Files.delete(path);
                log.debug("Deleted temp file: {}", path.getFileName());
            }
        } catch (IOException e) {
            log.warn("Failed to delete temp file: {}", filePath, e);
        }
    }

    public Path ensureTempDirectory() throws IOException {
        Path tempDir = Paths.get(parserProperties.getTempDir());
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
            log.info("Created temp directory: {}", tempDir);
        }
        return tempDir;
    }
}
