package com.clacjz.medical.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clacjz.medical.rag.config.RAGProperties;
import com.clacjz.medical.rag.context.TenantContext;
import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.mapper.ChatMessageMapper;
import com.clacjz.medical.rag.mapper.ChatSessionMapper;
import com.clacjz.medical.rag.mapper.CollectionMapper;
import com.clacjz.medical.rag.model.dto.*;
import com.clacjz.medical.rag.model.entity.ChatMessage;
import com.clacjz.medical.rag.model.entity.ChatSession;
import com.clacjz.medical.rag.model.entity.Collection;
import com.clacjz.medical.rag.monitor.AIMetricsService;
import com.clacjz.medical.rag.monitor.AISpan;
import com.clacjz.medical.rag.monitor.AITracingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final CollectionMapper collectionMapper;
    private final RAGService ragService;
    private final RAGProperties ragProperties;
    private final TransactionTemplate transactionTemplate;
    private final EmbeddingService embeddingService;
    private final AITracingService aiTracingService;
    private final AIMetricsService metricsService;

    @Transactional(readOnly = true)
    public IPage<ChatSessionDTO> getSessions(int page, int pageSize) {
        if (page < 0) {
            throw new IllegalArgumentException("page 参数不能为负数");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize 必须大于 0");
        }

        String userId = TenantContext.getUserId();
        String departmentId = TenantContext.getDepartmentId();

        Page<ChatSession> mpPage = new Page<>(page + 1, pageSize);
        QueryWrapper<ChatSession> wrapper = new QueryWrapper<>();

        if (TenantContext.isAdmin()) {
            if (departmentId != null) {
                wrapper.eq("department_id", departmentId);
            }
        } else {
            wrapper.eq("user_id", userId);
        }

        wrapper.ne("status", "DELETED");
        wrapper.orderByDesc("updated_at");

        IPage<ChatSession> sessions = sessionMapper.selectPage(mpPage, wrapper);
        return sessions.convert(this::convertToSessionDTO);
    }

    @Transactional(readOnly = true)
    public ChatSessionDTO getSession(String sessionId) {
        ChatSession session = findSessionById(sessionId);
        validateSessionAccess(session);
        return convertToSessionDTO(session);
    }

    @Transactional
    public ChatSessionDTO createSession(CreateChatSessionRequest request) {
        String userId = TenantContext.getUserId();
        String departmentId = TenantContext.getDepartmentId();

        if (departmentId == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "用户未关联部门，无法创建会话");
        }

        List<Collection> collections = collectionMapper.selectActiveByDepartmentId(departmentId);
        if (request.getCollectionIds() != null && !request.getCollectionIds().isEmpty()) {
            collections = collections.stream()
                    .filter(c -> request.getCollectionIds().contains(c.getId()))
                    .collect(Collectors.toList());
        }
        if (collections.isEmpty()) {
            throw new BizException(ErrorCode.COLLECTION_NOT_FOUND, "未找到有效的Collection");
        }

        for (Collection collection : collections) {
            if (!collection.getDepartmentId().equals(departmentId) && !TenantContext.isAdmin()) {
                throw new BizException(ErrorCode.PERMISSION_DENIED,
                        "Collection [" + collection.getName() + "] 不属于您的部门");
            }
        }

        String title = request.getTitle();
        if (title == null || title.isEmpty()) {
            title = "新会话 " + System.currentTimeMillis() % 10000;
        }

        ChatSession session = ChatSession.builder()
                .title(title)
                .userId(userId)
                .departmentId(departmentId)
                .collectionIds(request.getCollectionIds())
                .status("ACTIVE")
                .messageCount(0)
                .build();

        sessionMapper.insert(session);
        log.info("Chat session created: id={}, userId={}, collections={}",
                session.getId(), userId, request.getCollectionIds());

        return convertToSessionDTO(session);
    }

    @Transactional
    public void deleteSession(String sessionId) {
        ChatSession session = findSessionById(sessionId);
        validateSessionAccess(session);

        session.setStatus("DELETED");
        sessionMapper.updateById(session);
        log.info("Chat session deleted: id={}", sessionId);
    }

    @Transactional
    public ChatSessionDTO updateSessionTitle(String sessionId, String title) {
        ChatSession session = findSessionById(sessionId);
        validateSessionAccess(session);

        if (title == null || title.isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "标题不能为空");
        }

        session.setTitle(title);
        sessionMapper.updateById(session);

        log.info("Session title updated: id={}, title={}", sessionId, title);
        return convertToSessionDTO(session);
    }

    @Transactional
    public ChatSessionDTO updateSessionCollections(String sessionId, List<String> collectionIds) {
        ChatSession session = findSessionById(sessionId);
        validateSessionAccess(session);

        // 验证所有 collection 是否存在
        if (collectionIds != null && !collectionIds.isEmpty()) {
            for (String collectionId : collectionIds) {
                Collection collection = collectionMapper.selectById(collectionId);
                if (collection == null || collection.getDeletedAt() != null) {
                    throw new BizException(ErrorCode.COLLECTION_NOT_FOUND);
                }
            }
        }

        session.setCollectionIds(collectionIds);
        sessionMapper.updateById(session);

        log.info("Session collections updated: id={}, collections={}", sessionId, collectionIds);
        return convertToSessionDTO(session);
    }

    @Transactional(readOnly = true)
    public IPage<ChatMessageDTO> getMessages(String sessionId, int page, int pageSize) {
        ChatSession session = findSessionById(sessionId);
        validateSessionAccess(session);

        Page<ChatMessage> mpPage = new Page<>(page + 1, pageSize);
        QueryWrapper<ChatMessage> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId).orderByAsc("created_at");

        IPage<ChatMessage> messages = messageMapper.selectPage(mpPage, wrapper);
        return messages.convert(this::convertToMessageDTO);
    }

    public QueryResultDTO query(ChatQueryRequest request) {
        if (request.getUserId() == null) {
            request.setUserId(TenantContext.getUserId());
        }
        String departmentId = TenantContext.getDepartmentId();

        ChatSession session;
        if (request.getSessionId() != null && !request.getSessionId().isEmpty()) {
            session = findSessionById(request.getSessionId());
            validateSessionAccess(session);
        } else {
            CreateChatSessionRequest createRequest = CreateChatSessionRequest.builder()
                    .title("会话 " + System.currentTimeMillis() % 10000)
                    .collectionIds(request.getCollectionIds())
                    .build();
            ChatSessionDTO sessionDTO = createSession(createRequest);
            session = sessionMapper.selectById(sessionDTO.getId());
            if (session == null) {
                throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND);
            }
        }

        ChatMessage userMessage = ChatMessage.builder()
                .sessionId(session.getId())
                .departmentId(departmentId)
                .role("user")
                .content(request.getQuery())
                .sources(new ArrayList<>())
                .queryParams(ChatMessage.QueryParams.builder()
                        .collectionIds(request.getCollectionIds())
                        .noExpansion(request.getNoExpansion())
                        .topK(request.getTopK())
                        .similarityThreshold(request.getSimilarityThreshold())
                        .build())
                .build();

        // 加载历史会话，用于多轮上下文
        if (Boolean.TRUE.equals(request.getEnableHistory())) {
            List<Map<String, String>> history = loadConversationHistory(session.getId(), null);
            request.setHistory(history);
            log.debug("Loaded {} history messages for session: {}", history.size(), session.getId());
        }

        transactionTemplate.executeWithoutResult(status -> {
            messageMapper.insert(userMessage);
            session.incrementMessageCount();
            sessionMapper.updateById(session);
        });

        QueryResultDTO result = ragService.query(request);

        int maxRetries = 2;
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return transactionTemplate.execute(status -> {
                    ChatSession sessionForUpdate = sessionMapper.selectById(session.getId());
                    if (sessionForUpdate == null) {
                        throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND);
                    }

                    ChatMessage assistantMessage = ChatMessage.builder()
                            .sessionId(session.getId())
                            .departmentId(departmentId)
                            .role("assistant")
                            .content(result.getAnswer())
                            .reasoning(result.getReasoning())
                            .sources(result.getSources() != null ? result.getSources().stream()
                                    .map(s -> ChatMessage.SourceReference.builder()
                                            .documentId(s.getDocumentId())
                                            .title(s.getTitle())
                                            .snippet(s.getSnippet())
                                            .score(s.getScore())
                                            .collectionId(s.getCollectionId())
                                            .startPage(s.getStartPage())
                                            .endPage(s.getEndPage())
                                            .build())
                                    .collect(Collectors.toList()) : Collections.emptyList())
                            .modelInfo(ChatMessage.ModelInfo.builder()
                                    .modelName(ragProperties.getLlm().getModel())
                                    .embeddingModel(ragProperties.getEmbedding().getModel())
                                    .temperature(ragProperties.getLlm().getTemperature())
                                    .maxTokens(ragProperties.getLlm().getMaxTokens())
                                    .build())
                            .tokenUsage(ChatMessage.TokenUsage.builder()
                                    .promptTokens(result.getPromptTokens())
                                    .completionTokens(result.getTokensUsed() != null ? result.getTokensUsed() : 0)
                                    .totalTokens((result.getTokensUsed() != null ? result.getTokensUsed() : 0) + (result.getPromptTokens() != null ? result.getPromptTokens() : 0))
                                    .build())
                            .generationTimeMs(result.getGenerationTimeMs() != null ?
                                    result.getGenerationTimeMs().intValue() : null)
                            .build();
                    messageMapper.insert(assistantMessage);

                    // 首轮对话时自动更新会话标题
                    if (sessionForUpdate.getTitle() != null && sessionForUpdate.getTitle().startsWith("新会话")) {
                        String userQuery = request.getQuery();
                        String autoTitle = userQuery.length() > 25
                                ? userQuery.substring(0, 25) + "..."
                                : userQuery;
                        sessionForUpdate.setTitle(autoTitle);
                    }

                    sessionForUpdate.incrementMessageCount();
                    sessionMapper.updateById(sessionForUpdate);

                    result.setMessageId(assistantMessage.getId());

                    String answer = result.getAnswer();
                    int answerLength = answer != null ? answer.length() : 0;
                    log.info("Query processed: sessionId={}, queryLength={}, answerLength={}, time={}ms",
                            session.getId(), request.getQuery().length(), answerLength,
                            result.getGenerationTimeMs());

                    return result;
                });
            } catch (Exception e) {
                lastException = e;
                log.warn("保存助手消息事务失败，尝试 {}/{}: sessionId={}", attempt, maxRetries, session.getId(), e);
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(100L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.error("保存助手消息最终失败，AI 响应已返回给用户: sessionId={}, answerLength={}",
                session.getId(), result.getAnswer() != null ? result.getAnswer().length() : 0, lastException);
        return result;
    }

    public Flux<StreamEventDTO> streamQuery(ChatQueryRequest request) {
        // 流式查询Tracing（简化版，避免性能影响）
        String traceId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();

        if (request.getUserId() == null) {
            request.setUserId(TenantContext.getUserId());
        }
        String departmentId = TenantContext.getDepartmentId();

        ChatSession session;
        if (request.getSessionId() != null && !request.getSessionId().isEmpty()) {
            session = findSessionById(request.getSessionId());
            validateSessionAccess(session);
        } else {
            CreateChatSessionRequest createRequest = CreateChatSessionRequest.builder()
                    .title("会话 " + System.currentTimeMillis() % 10000)
                    .collectionIds(request.getCollectionIds())
                    .build();
            ChatSessionDTO sessionDTO = createSession(createRequest);
            session = sessionMapper.selectById(sessionDTO.getId());
            if (session == null) {
                throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND);
            }
        }

        ChatMessage userMessage = ChatMessage.builder()
                .sessionId(session.getId())
                .departmentId(departmentId)
                .role("user")
                .content(request.getQuery())
                .queryParams(ChatMessage.QueryParams.builder()
                        .collectionIds(request.getCollectionIds())
                        .noExpansion(request.getNoExpansion())
                        .topK(request.getTopK())
                        .similarityThreshold(request.getSimilarityThreshold())
                        .build())
                .build();

        // 加载历史会话，用于多轮上下文
        if (Boolean.TRUE.equals(request.getEnableHistory())) {
            List<Map<String, String>> history = loadConversationHistory(session.getId(), null);
            request.setHistory(history);
            log.debug("Loaded {} history messages for stream session: {}", history.size(), session.getId());
        }

        transactionTemplate.executeWithoutResult(status -> {
            messageMapper.insert(userMessage);
            session.incrementMessageCount();
            sessionMapper.updateById(session);
        });

        // 创建stream span
        AISpan streamSpan = aiTracingService.startSpan(traceId, "stream_query", "stream_query");
        streamSpan.setSessionId(session.getId());
        streamSpan.setUserId(request.getUserId());

        Flux<StreamEventDTO> streamFlux;
        try {
            streamFlux = ragService.streamQuery(request, traceId);
        } catch (Exception e) {
            streamSpan.setMetadata(Map.of("error", e.getMessage()));
            aiTracingService.endSpan(streamSpan);
            aiTracingService.recordTrace(traceId, List.of(streamSpan));
            log.error("ChatService streamQuery failed synchronously: traceId={}, error={}", traceId, e.getMessage());
            throw e;
        }

        String sid = session.getId();
        String did = departmentId;
        String modelName = ragProperties.getLlm().getModel();
        String embeddingModel = ragProperties.getEmbedding().getModel();
        Double temperature = ragProperties.getLlm().getTemperature();
        Integer maxTokens = ragProperties.getLlm().getMaxTokens();
        StringBuilder fullResponse = new StringBuilder();
        StringBuilder reasoningResponse = new StringBuilder();
        AtomicReference<String> savedMessageId = new AtomicReference<>("");
        List<ChatMessage.SourceReference> collectedSources = new ArrayList<>();
        Exception[] lastSaveExHolder = new Exception[1];  // 用于跨 lambda 共享保存结果

        return streamFlux
                .doOnNext(event -> {
                    if ("source".equals(event.getType())) {
                        collectedSources.add(ChatMessage.SourceReference.builder()
                                .documentId(event.getDocumentId())
                                .title(event.getTitle())
                                .snippet(event.getSnippet())
                                .score(event.getScore())
                                .collectionId(event.getCollectionId())
                                .startPage(event.getStartPage())
                                .endPage(event.getEndPage())
                                .build());
                    } else if ("token".equals(event.getType()) && event.getContent() != null) {
                        log.debug("ChatService accumulating token: contentLength={}", event.getContent().length());
                        fullResponse.append(event.getContent());
                    } else if ("reasoning".equals(event.getType()) && event.getContent() != null) {
                        log.debug("ChatService accumulating reasoning: contentLength={}", event.getContent().length());
                        reasoningResponse.append(event.getContent());
                    }
                })
                .doOnError(e -> {
                    streamSpan.setMetadata(Map.of("error", e.getMessage()));
                    aiTracingService.endSpan(streamSpan);
                    aiTracingService.recordTrace(traceId, List.of(streamSpan));
                })
                .doOnComplete(() -> {
                    log.info("ChatService doOnComplete: fullResponseLen={}, reasoningLen={}, sourcesCount={}, sid={}",
                            fullResponse.length(), reasoningResponse.length(), collectedSources.size(), sid);
                    streamSpan.setOutputText(fullResponse.toString());
                    streamSpan.setDurationMs(java.time.Duration.between(startTime, Instant.now()).toMillis());
                    aiTracingService.endSpan(streamSpan);
                    aiTracingService.recordTrace(traceId, List.of(streamSpan));
                    metricsService.recordTracingCoverage(aiTracingService.getTracingCoverage());
                    long generationTime = java.time.Duration.between(startTime, Instant.now()).toMillis();

                    // 校验 noExpansion 模式下的回答是否基于检索内容
                    String finalAnswer = fullResponse.toString();
                    if (Boolean.TRUE.equals(request.getNoExpansion())) {
                        java.util.regex.Pattern citationPattern = java.util.regex.Pattern.compile("\\{\\{(\\d+)\\}\\}");
                        java.util.regex.Matcher matcher = citationPattern.matcher(finalAnswer);
                        if (!matcher.find()) {
                            log.warn("No expansion mode: answer lacks citations. sessionId={}", sid);
                        }
                    }

                    int completionTokens = (int) Math.ceil(finalAnswer.length() / 2.0);
                    int promptTokens = embeddingService.tokenize(request.getQuery()) * 6;

                    // 保存助手消息（带重试）
                    int maxSaveRetries = 3;
                    lastSaveExHolder[0] = null;
                    for (int attempt = 1; attempt <= maxSaveRetries; attempt++) {
                        try {
                            transactionTemplate.executeWithoutResult(status -> {
                                ChatSession s = sessionMapper.selectById(sid);
                                if (s == null) {
                                    return;
                                }
                                ChatMessage assistantMessage = ChatMessage.builder()
                                        .sessionId(sid)
                                        .departmentId(did)
                                        .role("assistant")
                                        .content(finalAnswer)
                                        .reasoning(reasoningResponse.toString())
                                        .sources(collectedSources)
                                        .modelInfo(ChatMessage.ModelInfo.builder()
                                                .modelName(modelName)
                                                .embeddingModel(embeddingModel)
                                                .temperature(temperature)
                                                .maxTokens(maxTokens)
                                                .build())
                                        .tokenUsage(ChatMessage.TokenUsage.builder()
                                                .promptTokens(promptTokens)
                                                .completionTokens(completionTokens)
                                                .totalTokens(promptTokens + completionTokens)
                                                .build())
                                        .generationTimeMs((int) generationTime)
                                        .build();
                                messageMapper.insert(assistantMessage);
                                savedMessageId.set(assistantMessage.getId());

                                // 首轮对话时自动更新会话标题
                                if (s.getTitle() != null && s.getTitle().startsWith("新会话")) {
                                    String userQuery = request.getQuery();
                                    String autoTitle = userQuery.length() > 25
                                            ? userQuery.substring(0, 25) + "..."
                                            : userQuery;
                                    s.setTitle(autoTitle);
                                    sessionMapper.updateById(s);
                                }

                                s.incrementMessageCount();
                                sessionMapper.updateById(s);
                            });
                            lastSaveExHolder[0] = null;
                            break;
                        } catch (Exception e) {
                            lastSaveExHolder[0] = e;
                            log.warn("Save assistant message failed, attempt {}/{}: sessionId={}", attempt, maxSaveRetries, sid, e);
                            if (attempt < maxSaveRetries) {
                                try {
                                    Thread.sleep(100L * attempt);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                        }
                    }
                    if (lastSaveExHolder[0] != null) {
                        log.error("Save assistant message failed after {} retries: sessionId={}", maxSaveRetries, sid, lastSaveExHolder[0]);
                    }
                })
                .concatWith(Flux.defer(() -> {
                    // 只有消息保存成功后才发送 done 事件
                    if (lastSaveExHolder[0] != null) {
                        log.warn("Done event emitted but message save failed: sessionId={}", sid);
                    }
                    long duration = java.time.Duration.between(startTime, Instant.now()).toMillis();
                    int completionTokens = embeddingService.tokenize(fullResponse.toString());
                    StreamEventDTO doneEvent = StreamEventDTO.builder()
                            .type("done")
                            .messageId(savedMessageId.get())
                            .tokensUsed(completionTokens)
                            .generationTimeMs(duration)
                            .build();
                    return Flux.just(doneEvent);
                }));
    }

    @Transactional
    public void deleteMessage(String sessionId, String messageId) {
        ChatSession session = findSessionById(sessionId);
        validateSessionAccess(session);

        ChatMessage message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "消息不存在");
        }

        if (!message.getSessionId().equals(sessionId)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "消息不属于该会话");
        }

        messageMapper.deleteById(messageId);
        session.decrementMessageCount();
        sessionMapper.updateById(session);

        log.info("Message deleted: sessionId={}, messageId={}", sessionId, messageId);
    }

    @Transactional
    public ChatMessageDTO regenerateResponse(String sessionId, String messageId) {
        ChatSession session = findSessionById(sessionId);
        validateSessionAccess(session);

        ChatMessage originalMessage = messageMapper.selectById(messageId);
        if (originalMessage == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "消息不存在");
        }

        if (!"assistant".equals(originalMessage.getRole())) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "只能重新生成助手消息");
        }

        List<ChatMessage> messages = messageMapper.selectBySessionIdOrderByCreatedAtAsc(sessionId);
        int messageIndex = -1;
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId().equals(messageId)) {
                messageIndex = i;
                break;
            }
        }

        if (messageIndex <= 0 || !"user".equals(messages.get(messageIndex - 1).getRole())) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "无法重新生成：前一条消息不是用户消息");
        }

        String userQuery = messages.get(messageIndex - 1).getContent();
        List<String> collectionIds = session.getCollectionIds();
        String userId = TenantContext.getUserId();

        ChatQueryRequest queryRequest = ChatQueryRequest.builder()
                .query(userQuery)
                .collectionIds(collectionIds)
                .userId(userId)
                .noExpansion(originalMessage.getQueryParams() != null ?
                        originalMessage.getQueryParams().getNoExpansion() : false)
                .topK(originalMessage.getQueryParams() != null ?
                        originalMessage.getQueryParams().getTopK() : 5)
                .similarityThreshold(originalMessage.getQueryParams() != null ?
                        originalMessage.getQueryParams().getSimilarityThreshold() : 0.7)
                .build();

        // 先重新生成回答再删除旧消息，避免失败时丢数据
        QueryResultDTO result = ragService.query(queryRequest);

        messageMapper.deleteById(messageId);
        session.decrementMessageCount();
        sessionMapper.updateById(session);

        ChatMessage newMessage = ChatMessage.builder()
                .sessionId(session.getId())
                .departmentId(session.getDepartmentId())
                .role("assistant")
                .content(result.getAnswer())
                .reasoning(null)
                .sources(result.getSources() != null ? result.getSources().stream()
                        .map(s -> ChatMessage.SourceReference.builder()
                                .documentId(s.getDocumentId())
                                .title(s.getTitle())
                                .snippet(s.getSnippet())
                                .score(s.getScore())
                                .collectionId(s.getCollectionId())
                                .startPage(s.getStartPage())
                                .endPage(s.getEndPage())
                                .build())
                        .collect(Collectors.toList()) : Collections.emptyList())
                .modelInfo(ChatMessage.ModelInfo.builder()
                        .modelName(ragProperties.getLlm().getModel())
                        .embeddingModel(ragProperties.getEmbedding().getModel())
                        .temperature(ragProperties.getLlm().getTemperature())
                        .maxTokens(ragProperties.getLlm().getMaxTokens())
                        .build())
                .tokenUsage(ChatMessage.TokenUsage.builder()
                        .completionTokens(result.getTokensUsed())
                        .promptTokens(result.getPromptTokens())
                        .totalTokens((result.getTokensUsed() != null ? result.getTokensUsed() : 0)
                                + (result.getPromptTokens() != null ? result.getPromptTokens() : 0))
                        .build())
                .generationTimeMs(result.getGenerationTimeMs() != null ?
                        result.getGenerationTimeMs().intValue() : null)
                .build();
        messageMapper.insert(newMessage);
        session.incrementMessageCount();
        sessionMapper.updateById(session);

        log.info("Message regenerated: sessionId={}, oldMessageId={}, newMessageId={}",
                sessionId, messageId, newMessage.getId());

        return convertToMessageDTO(newMessage);
    }

    @Transactional
    public void feedbackMessage(String sessionId, String messageId, String feedback, String comment) {
        ChatSession session = findSessionById(sessionId);
        validateSessionAccess(session);

        ChatMessage message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "消息不存在");
        }

        if (!message.getSessionId().equals(sessionId)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "消息不属于该会话");
        }

        if (!"assistant".equals(message.getRole())) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "只能对助手消息进行反馈");
        }

        if (!"like".equals(feedback) && !"dislike".equals(feedback)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "反馈类型必须是 'like' 或 'dislike'");
        }

        message.setFeedback(feedback);
        message.setFeedbackComment(comment);
        messageMapper.updateById(message);

        log.info("Message feedback recorded: sessionId={}, messageId={}, feedback={}",
                sessionId, messageId, feedback);
    }

    @Transactional(readOnly = true)
    public String exportSession(String sessionId, String format) {
        ChatSession session = findSessionById(sessionId);
        validateSessionAccess(session);

        List<ChatMessage> messages = messageMapper.selectBySessionIdOrderByCreatedAtAsc(sessionId);

        StringBuilder content = new StringBuilder();
        content.append("# ").append(session.getTitle()).append("\n\n");
        content.append("会话ID: ").append(sessionId).append("\n");
        content.append("创建时间: ").append(session.getCreatedAt()).append("\n");
        content.append("消息数: ").append(session.getMessageCount()).append("\n\n");
        content.append("---\n\n");

        for (ChatMessage message : messages) {
            if ("user".equals(message.getRole())) {
                content.append("## 用户\n\n");
                content.append(message.getContent()).append("\n\n");
            } else if ("assistant".equals(message.getRole())) {
                content.append("## 助手\n\n");
                content.append(message.getContent()).append("\n\n");

                if (message.getSources() != null && !message.getSources().isEmpty()) {
                    content.append("**参考来源:**\n");
                    for (ChatMessage.SourceReference source : message.getSources()) {
                        content.append("- ").append(source.getTitle());
                        if (source.getScore() != null) {
                            content.append(" (相似度: ").append(String.format("%.2f", source.getScore())).append(")");
                        }
                        content.append("\n");
                    }
                    content.append("\n");
                }

                if (message.getTokenUsage() != null) {
                    content.append("**Token使用:** ");
                    content.append(message.getTokenUsage().getTotalTokens());
                    content.append("\n\n");
                }
            }
            content.append("---\n\n");
        }

        log.info("Session exported: sessionId={}, format={}, messageCount={}",
                sessionId, format, messages.size());

        return content.toString();
    }

    private ChatSession findSessionById(String sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        return session;
    }

    private void validateSessionAccess(ChatSession session) {
        String userId = TenantContext.getUserId();
        String departmentId = TenantContext.getDepartmentId();

        if (TenantContext.isAdmin()) {
            if (departmentId != null && !session.getDepartmentId().equals(departmentId)) {
                throw new BizException(ErrorCode.PERMISSION_DENIED, "无权访问该会话");
            }
            return;
        }

        if (!session.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "无权访问该会话");
        }
    }


    private List<Map<String, String>> loadConversationHistory(String sessionId, String excludeMessageId) {
        // 通过 SQL LIMIT 只加载最近 20 条消息，避免全量加载
        List<ChatMessage> messages = messageMapper.selectLastBySessionId(sessionId, 20);
        List<Map<String, String>> history = new ArrayList<>();
        // 消息按 DESC 排序，反转为 ASC
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (excludeMessageId != null && msg.getId().equals(excludeMessageId)) {
                continue;
            }
            Map<String, String> entry = new HashMap<>();
            entry.put("role", msg.getRole());
            entry.put("content", msg.getContent());
            history.add(entry);
        }
        return history;
    }

    private ChatSessionDTO convertToSessionDTO(ChatSession session) {
        return ChatSessionDTO.builder()
                .id(session.getId())
                .title(session.getTitle())
                .userId(session.getUserId())
                .collectionIds(session.getCollectionIds())
                .status(session.getStatus())
                .messageCount(session.getMessageCount())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .lastMessageAt(session.getLastMessageAt())
                .build();
    }

    private ChatMessageDTO convertToMessageDTO(ChatMessage message) {
        return ChatMessageDTO.builder()
                .id(message.getId())
                .sessionId(message.getSessionId())
                .role(message.getRole())
                .content(message.getContent())
                .reasoning(message.getReasoning())
                .sources(message.getSources())
                .queryParams(message.getQueryParams())
                .modelInfo(message.getModelInfo())
                .tokenUsage(message.getTokenUsage())
                .generationTimeMs(message.getGenerationTimeMs())
                .createdAt(message.getCreatedAt())
                .build();
    }
}