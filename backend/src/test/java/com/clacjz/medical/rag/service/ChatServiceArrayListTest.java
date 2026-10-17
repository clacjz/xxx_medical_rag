package com.clacjz.medical.rag.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatService ArrayList使用测试")
class ChatServiceArrayListTest {

    private static final String SOURCE_PATH = "src/main/java/com/clacjz/medical/rag/service/ChatService.java";

    private String readChatServiceSource() throws IOException {
        Path basePath = Paths.get("").toAbsolutePath();
        // 从 test 目录向上找到 backend 根目录
        Path sourceFile = basePath.resolve(SOURCE_PATH);
        if (!Files.exists(sourceFile)) {
            // 尝试从当前工作目录的父目录查找
            sourceFile = basePath.getParent().resolve(SOURCE_PATH);
        }
        if (!Files.exists(sourceFile)) {
            // 尝试标准项目结构
            sourceFile = Paths.get(System.getProperty("user.dir")).resolve(SOURCE_PATH);
        }
        assertTrue(Files.exists(sourceFile), "ChatService.java 源文件应存在: " + sourceFile);
        return Files.readString(sourceFile);
    }

    @Test
    @DisplayName("collectedSources 应使用 ArrayList 而非 CopyOnWriteArrayList")
    void collectedSourcesUsesArrayList() throws IOException {
        String source = readChatServiceSource();

        // 验证 collectedSources 声明使用 ArrayList
        Pattern arrayListPattern = Pattern.compile(
                "List<ChatMessage\\.SourceReference>\\s+collectedSources\\s*=\\s*new\\s+ArrayList<>\\(\\)");
        Matcher matcher = arrayListPattern.matcher(source);
        assertTrue(matcher.find(),
                "collectedSources 应声明为 new ArrayList<>()，" +
                "因为 Flux.doOnNext 保证顺序执行，无需线程安全集合");

        // 验证没有使用 CopyOnWriteArrayList
        assertFalse(source.contains("CopyOnWriteArrayList"),
                "ChatService 不应使用 CopyOnWriteArrayList，" +
                "Flux.doOnNext 保证顺序执行，ArrayList 即可满足需求");
    }

    @Test
    @DisplayName("ArrayList 足以满足需求，因为 Flux.doOnNext 保证顺序执行")
    void arrayListIsSufficientBecauseFluxIsSequential() throws IOException {
        String source = readChatServiceSource();

        // 验证 collectedSources 的修改仅在 doOnNext lambda 中
        // Flux.doOnNext 的契约保证：每个 onNext 调用顺序执行，不会并发
        // 因此 ArrayList（非线程安全）在此场景下是安全的

        // 1. 确认 collectedSources.add 调用在 doOnNext 内
        Pattern doOnNextAddPattern = Pattern.compile(
                "doOnNext.*?collectedSources\\.add", Pattern.DOTALL);
        Matcher matcher = doOnNextAddPattern.matcher(source);
        assertTrue(matcher.find(),
                "collectedSources.add() 应在 doOnNext 回调内调用，" +
                "Flux 规范保证 doOnNext 顺序执行不并发");

        // 2. 确认 collectedSources.size() 的读取在 doOnComplete 内（也是顺序的）
        Pattern doOnCompleteSizePattern = Pattern.compile(
                "doOnComplete.*?collectedSources\\.size", Pattern.DOTALL);
        Matcher sizeMatcher = doOnCompleteSizePattern.matcher(source);
        assertTrue(sizeMatcher.find(),
                "collectedSources.size() 应在 doOnComplete 回调内读取，" +
                "doOnComplete 在所有 doOnNext 之后顺序执行，不存在竞态条件");

        // 文档化：Flux.doOnNext 的 Reactive Streams 规范保证
        // - onNext 信号按顺序传递给订阅者
        // - 不会并发调用 onNext（即使 Publisher 使用多线程调度）
        // - doOnComplete 在所有 onNext 之后调用
        // 因此 ArrayList 在此场景下线程安全，无需 CopyOnWriteArrayList
    }
}
