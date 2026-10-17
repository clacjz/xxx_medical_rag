package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.context.TenantContext;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.model.dto.*;
import com.clacjz.medical.rag.model.vo.PageResult;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.ChatService;
import com.clacjz.medical.rag.service.ExportService;
import com.clacjz.medical.rag.util.JsonUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Tag(name = "Chat API", description = "聊天和RAG问答相关接口")
public class ChatController {

    private final ChatService chatService;
    private final ExportService exportService;
    private final JsonUtil jsonUtil;

    private static final long SSE_TIMEOUT = TimeUnit.MINUTES.toMillis(10);

    @GetMapping("/sessions")
    @Operation(summary = "获取会话列表", description = "获取当前用户的聊天会话列表")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<PageResult<ChatSessionDTO>>> listSessions(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int pageSize,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "listSessions");
        log.info("List chat sessions: page={}, pageSize={}, user={}", page, pageSize, username);

        IPage<ChatSessionDTO> sessions = chatService.getSessions(page, pageSize);
        PageResult<ChatSessionDTO> result = PageResult.from(sessions);
        return ResponseEntity.ok(Result.success(result));
    }

    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "获取会话详情", description = "获取指定会话的详细信息")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<ChatSessionDTO>> getSession(
            @Parameter(description = "会话ID") @PathVariable String sessionId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "getSession");
        log.info("Get chat session: sessionId={}, user={}", sessionId, username);

        ChatSessionDTO session = chatService.getSession(sessionId);
        return ResponseEntity.ok(Result.success(session));
    }

    @PostMapping("/sessions")
    @Operation(summary = "创建会话", description = "创建新的聊天会话")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<ChatSessionDTO>> createSession(
            @Valid @RequestBody CreateChatSessionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "createSession");
        log.info("Create chat session: title={}, collections={}, user={}",
                request.getTitle(), request.getCollectionIds(), username);

        ChatSessionDTO session = chatService.createSession(request);
        return ResponseEntity.ok(Result.success("会话创建成功", session));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "删除会话", description = "删除指定的聊天会话")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<Void>> deleteSession(
            @Parameter(description = "会话ID") @PathVariable String sessionId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "deleteSession");
        log.info("Delete chat session: sessionId={}, user={}", sessionId, username);

        chatService.deleteSession(sessionId);
        return ResponseEntity.ok(Result.success("会话删除成功", null));
    }

    @PutMapping("/sessions/{sessionId}/title")
    @Operation(summary = "重命名会话", description = "更新会话标题")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<ChatSessionDTO>> updateSessionTitle(
            @Parameter(description = "会话ID") @PathVariable String sessionId,
            @Parameter(description = "新标题") @RequestParam String title,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "updateSessionTitle");
        log.info("Update session title: sessionId={}, title={}, user={}",
                sessionId, title, username);

        ChatSessionDTO session = chatService.updateSessionTitle(sessionId, title);
        return ResponseEntity.ok(Result.success("会话重命名成功", session));
    }

    @PutMapping("/sessions/{sessionId}/collections")
    @Operation(summary = "更新会话Collection", description = "更新会话关联的Collection")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<ChatSessionDTO>> updateSessionCollections(
            @Parameter(description = "会话ID") @PathVariable String sessionId,
            @RequestBody java.util.List<String> collectionIds,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "updateSessionCollections");
        log.info("Update session collections: sessionId={}, collections={}, user={}",
                sessionId, collectionIds, username);

        ChatSessionDTO session = chatService.updateSessionCollections(sessionId, collectionIds);
        return ResponseEntity.ok(Result.success("会话Collection更新成功", session));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    @Operation(summary = "获取会话消息", description = "获取指定会话的消息历史")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<PageResult<ChatMessageDTO>>> getMessages(
            @Parameter(description = "会话ID") @PathVariable String sessionId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "50") int pageSize,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "getMessages");
        log.info("Get chat messages: sessionId={}, page={}, pageSize={}, user={}",
                sessionId, page, pageSize, username);

        IPage<ChatMessageDTO> messages = chatService.getMessages(sessionId, page, pageSize);
        PageResult<ChatMessageDTO> result = PageResult.from(messages);
        return ResponseEntity.ok(Result.success(result));
    }

    @PostMapping("/query")
    @Operation(summary = "RAG问答（非流式）", description = "发送问题并获取完整回答")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<QueryResultDTO>> query(
            @Valid @RequestBody ChatQueryRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "query");
        log.info("Chat query: sessionId={}, queryLength={}, collections={}, noExpansion={}, user={}",
                request.getSessionId(), request.getQuery().length(), request.getCollectionIds(),
                request.getNoExpansion(), username);

        request.setUserId(TenantContext.getUserId());
        QueryResultDTO result = chatService.query(request);
        return ResponseEntity.ok(Result.success(result));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "RAG问答（流式）", description = "发送问题并获取流式回答（SSE）")
    @PreAuthorize("isAuthenticated()")
    public SseEmitter streamQuery(
            @Valid @RequestBody ChatQueryRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "streamQuery");
        String sessionId = request.getSessionId() != null ? request.getSessionId() : "new";
        log.info("Chat stream query: sessionId={}, queryLength={}, collections={}, noExpansion={}, user={}",
                sessionId, request.getQuery().length(), request.getCollectionIds(),
                request.getNoExpansion(), username);

        request.setUserId(TenantContext.getUserId());
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        AtomicBoolean completed = new AtomicBoolean(false);

        log.info("Ctrl subscribing to streamQuery...");
        Disposable disposable = chatService.streamQuery(request)
                .subscribe(
                        event -> {
                            if (completed.get()) return;
                            try {
                                String json = jsonUtil.toJson(event);
                                if (json != null) {
                                    emitter.send(SseEmitter.event()
                                            .id(UUID.randomUUID().toString())
                                            .name(event.getType())
                                            .data(json));
                                }
                            } catch (IOException e) {
                                log.warn("Failed to send SSE event, client may have disconnected: type={}", event.getType());
                                if (completed.compareAndSet(false, true)) {
                                    emitter.complete();
                                }
                            }
                        },
                        error -> {
                            log.error("Stream query error", error);
                            if (completed.compareAndSet(false, true)) {
                                try {
                                    String json = jsonUtil.toJson(StreamEventDTO.builder()
                                            .type("error")
                                            .message(error.getMessage())
                                            .build());
                                    if (json != null) {
                                        emitter.send(SseEmitter.event()
                                                .name("error")
                                                .data(json));
                                    }
                                    emitter.completeWithError(error);
                                } catch (IOException e) {
                                    emitter.complete();
                                }
                            }
                        },
                        () -> {
                            log.info("Ctrl subscriber onComplete");
                            if (completed.compareAndSet(false, true)) {
                                emitter.complete();
                            }
                        }
                );

        emitter.onCompletion(() -> {
            if (!disposable.isDisposed()) {
                disposable.dispose();
            }
        });
        emitter.onTimeout(() -> {
            log.debug("SSE stream timeout, cancelling subscription");
            if (!disposable.isDisposed()) {
                disposable.dispose();
            }
            if (completed.compareAndSet(false, true)) {
                emitter.complete();
            }
        });
        emitter.onError(e -> {
            log.debug("SSE stream error: {}", e.getMessage());
            if (!disposable.isDisposed()) {
                disposable.dispose();
            }
            if (completed.compareAndSet(false, true)) {
                emitter.complete();
            }
        });

        return emitter;
    }

    @DeleteMapping("/sessions/{sessionId}/messages/{messageId}")
    @Operation(summary = "删除消息", description = "删除指定的消息")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<Void>> deleteMessage(
            @Parameter(description = "会话ID") @PathVariable String sessionId,
            @Parameter(description = "消息ID") @PathVariable String messageId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "deleteMessage");
        log.info("Delete message: sessionId={}, messageId={}, user={}", sessionId, messageId, username);

        chatService.deleteMessage(sessionId, messageId);
        return ResponseEntity.ok(Result.success("消息删除成功", null));
    }

    @PostMapping("/sessions/{sessionId}/messages/{messageId}/regenerate")
    @Operation(summary = "重新生成回复", description = "重新生成指定消息的回复")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<ChatMessageDTO>> regenerateResponse(
            @Parameter(description = "会话ID") @PathVariable String sessionId,
            @Parameter(description = "消息ID") @PathVariable String messageId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "regenerateResponse");
        log.info("Regenerate response: sessionId={}, messageId={}, user={}", sessionId, messageId, username);

        ChatMessageDTO message = chatService.regenerateResponse(sessionId, messageId);
        return ResponseEntity.ok(Result.success("回复重新生成成功", message));
    }

    @PostMapping("/sessions/{sessionId}/messages/{messageId}/feedback")
    @Operation(summary = "消息反馈", description = "对助手消息进行反馈（点赞/点踩）")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<Void>> feedbackMessage(
            @Parameter(description = "会话ID") @PathVariable String sessionId,
            @Parameter(description = "消息ID") @PathVariable String messageId,
            @Parameter(description = "反馈类型") @RequestParam String feedback,
            @Parameter(description = "反馈评论") @RequestParam(required = false) String comment,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "feedbackMessage");
        log.info("Message feedback: sessionId={}, messageId={}, feedback={}, user={}",
                sessionId, messageId, feedback, username);

        chatService.feedbackMessage(sessionId, messageId, feedback, comment);
        return ResponseEntity.ok(Result.success("反馈已提交", null));
    }

    @GetMapping("/sessions/{sessionId}/export")
    @Operation(summary = "导出会话", description = "导出会话内容，支持markdown/pdf格式")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> exportSession(
            @Parameter(description = "会话ID") @PathVariable String sessionId,
            @Parameter(description = "导出格式：markdown/pdf") @RequestParam(defaultValue = "markdown") String format,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = getUsernameSafely(userDetails, "exportSession");
        log.info("Export session: sessionId={}, format={}, user={}", sessionId, format, username);

        String normalizedFormat = format.toLowerCase().trim();
        
        if ("pdf".equals(normalizedFormat)) {
            byte[] pdfContent = exportService.exportSessionToPdf(sessionId);
            String filename = "session_" + sessionId + "_" + System.currentTimeMillis() + ".pdf";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", encodedFilename);
            headers.setContentLength(pdfContent.length);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfContent);
        } else {
            String markdownContent = exportService.exportSessionToMarkdown(sessionId);
            String filename = "session_" + sessionId + "_" + System.currentTimeMillis() + ".md";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"));
            headers.setContentDispositionFormData("attachment", encodedFilename);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(markdownContent);
        }
    }

    private String getUsernameSafely(UserDetails userDetails, String location) {
        return Optional.ofNullable(userDetails)
                .map(UserDetails::getUsername)
                .orElseThrow(() -> new BizException(ErrorCode.AUTHENTICATION_FAILED,
                        String.format("[%s] 用户信息不存在，请重新登录", location)));
    }
}
