package com.clacjz.medical.rag.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Value("${rag.thread-pool.document-parser.core-size:8}")
    private int docParserCoreSize;

    @Value("${rag.thread-pool.document-parser.max-size:8}")
    private int docParserMaxSize;

    @Value("${rag.thread-pool.document-parser.queue-capacity:20}")
    private int docParserQueueCapacity;

    @Value("${rag.thread-pool.embedding.core-size:4}")
    private int embeddingCoreSize;

    @Value("${rag.thread-pool.embedding.max-size:4}")
    private int embeddingMaxSize;

    @Value("${rag.thread-pool.embedding.queue-capacity:64}")
    private int embeddingQueueCapacity;

    @Bean(name = "taskExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("rag-async-");
        executor.setKeepAliveSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean(name = "documentParserExecutor")
    public Executor getDocumentParserExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(docParserCoreSize);
        executor.setMaxPoolSize(docParserMaxSize);
        executor.setQueueCapacity(docParserQueueCapacity);
        executor.setThreadNamePrefix("doc-parser-");
        executor.setKeepAliveSeconds(120);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("documentParserExecutor: core={}, max={}, queue={}",
                docParserCoreSize, docParserMaxSize, docParserQueueCapacity);
        return executor;
    }

    @Bean(name = "embeddingExecutor")
    public Executor getEmbeddingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(embeddingCoreSize);
        executor.setMaxPoolSize(embeddingMaxSize);
        executor.setQueueCapacity(embeddingQueueCapacity);
        executor.setThreadNamePrefix("embedding-");
        executor.setKeepAliveSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("embeddingExecutor: core={}, max={}, queue={}",
                embeddingCoreSize, embeddingMaxSize, embeddingQueueCapacity);
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new CustomAsyncUncaughtExceptionHandler();
    }

    private static class CustomAsyncUncaughtExceptionHandler implements AsyncUncaughtExceptionHandler {
        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            log.error("Async method {} threw exception", method.getName(), ex);
        }
    }
}
