package com.smart.agent.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.common.error.AgentException;
import com.smart.agent.conversation.ConversationService;
import com.smart.agent.attachment.AttachmentService;
import com.smart.agent.attachment.AttachmentStatus;
import com.smart.agent.conversation.Message;
import com.smart.agent.knowledge.KnowledgeCitation;
import com.smart.agent.knowledge.KnowledgeSearchQuery;
import com.smart.agent.knowledge.KnowledgeSearchService;
import com.smart.agent.model.ModelEvent;
import com.smart.agent.model.ModelGateway;
import com.smart.agent.model.ModelRequest;
import com.smart.agent.run.AgentRun;
import com.smart.agent.run.AgentRunService;
import com.smart.agent.run.AgentRunStatus;
import com.smart.agent.security.AgentUserContext;
import com.smart.agent.tool.AgentTool;
import com.smart.agent.tool.ToolExecutor;
import com.smart.agent.tool.ToolRegistry;
import com.smart.agent.tool.web.WebSearchPolicy;
import com.smart.agent.tool.web.WebSearchResult;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.Disposables;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.scheduler.Schedulers;
import java.util.concurrent.Callable;

public class ChatOrchestrator {
    static final int MAX_MODEL_TURNS = 6;
    static final int MAX_TOOL_CALLS = 5;
    static final int MAX_CITATIONS = 20;
    static final int MAX_HISTORY_MESSAGES = 20;
    static final int MAX_HISTORY_BYTES = 32 * 1024;
    static final Duration MAX_RUN_DURATION = Duration.ofSeconds(90);

    private final ConversationService conversationService;
    private final AgentRunService runService;
    private final ModelGateway modelGateway;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;
    private final KnowledgeSearchService knowledgeSearchService;
    private final Duration runBudget;
    private final AttachmentService attachmentService;
    private final String attachmentPublicBaseUrl;

    public ChatOrchestrator(
            ConversationService conversationService,
            AgentRunService runService,
            ModelGateway modelGateway,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            ObjectMapper objectMapper) {
        this(conversationService, runService, modelGateway, toolRegistry, toolExecutor, objectMapper, null, null, null);
    }

    public ChatOrchestrator(
            ConversationService conversationService,
            AgentRunService runService,
            ModelGateway modelGateway,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            ObjectMapper objectMapper,
            KnowledgeSearchService knowledgeSearchService) {
        this(conversationService, runService, modelGateway, toolRegistry, toolExecutor, objectMapper,
                knowledgeSearchService, MAX_RUN_DURATION, null, null);
    }

    ChatOrchestrator(
            ConversationService conversationService,
            AgentRunService runService,
            ModelGateway modelGateway,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            ObjectMapper objectMapper,
            KnowledgeSearchService knowledgeSearchService,
            Duration runBudget) {
        this(conversationService, runService, modelGateway, toolRegistry, toolExecutor, objectMapper, knowledgeSearchService, runBudget, null, null);
    }
    ChatOrchestrator(ConversationService conversationService, AgentRunService runService, ModelGateway modelGateway,
            ToolRegistry toolRegistry, ToolExecutor toolExecutor, ObjectMapper objectMapper,
            KnowledgeSearchService knowledgeSearchService, Duration runBudget, AttachmentService attachmentService) {
        this(conversationService, runService, modelGateway, toolRegistry, toolExecutor, objectMapper,
                knowledgeSearchService, runBudget, attachmentService, null);
    }
    ChatOrchestrator(ConversationService conversationService, AgentRunService runService, ModelGateway modelGateway,
            ToolRegistry toolRegistry, ToolExecutor toolExecutor, ObjectMapper objectMapper,
            KnowledgeSearchService knowledgeSearchService, Duration runBudget, AttachmentService attachmentService,
            String attachmentPublicBaseUrl) {
        this.conversationService = conversationService;
        this.runService = runService;
        this.modelGateway = modelGateway;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.objectMapper = objectMapper;
        this.knowledgeSearchService = knowledgeSearchService;
        this.runBudget = runBudget;
        this.attachmentService = attachmentService;
        this.attachmentPublicBaseUrl = attachmentPublicBaseUrl == null ? "" : attachmentPublicBaseUrl.trim();
    }

    public Flux<ChatEvent> stream(ChatCommand command, AgentUserContext context, String traceId) {
        return Flux.<ChatEvent>create(
                        sink -> new Session(command, context, traceId, sink).start(), FluxSink.OverflowStrategy.BUFFER)
                .onBackpressureBuffer(256, BufferOverflowStrategy.ERROR);
    }

    private final class Session {
        private final ChatCommand command;
        private final AgentUserContext context;
        private final String traceId;
        private final FluxSink<ChatEvent> sink;
        private final Instant deadline = Instant.now().plus(runBudget);
        private final AtomicBoolean terminated = new AtomicBoolean();
        private final Object terminalLock = new Object();
        private final reactor.core.Disposable.Swap modelSubscription = Disposables.swap();
        private final AtomicReference<CompletableFuture<?>> activeToolOperation = new AtomicReference<>();
        private final List<ModelRequest.ConversationEntry> messages = new ArrayList<>();
        private final Map<String, CachedToolResult> toolResultCache = new HashMap<>();
        private long sequence;
        private AgentRun run;
        private AgentRunStatus status;
        private int modelTurns;
        private int toolCalls;
        private List<KnowledgeCitation> citations = List.of();
        private final StringBuilder turnDeltas = new StringBuilder();
        private final List<ModelEvent.ToolRequested> turnTools = new ArrayList<>();
        private ModelEvent.Completed turnCompleted;

        private Session(ChatCommand command, AgentUserContext context, String traceId, FluxSink<ChatEvent> sink) {
            this.command = command;
            this.context = context;
            this.traceId = traceId;
            this.sink = sink;
        }

        void start() {
            sink.onCancel(this::cancel);
            sink.onDispose(this::cancel);
            try {
                run = withinBudget(() -> runService.start(
                        context.tenantId(), context.userId(), command.conversationId(), traceId));
                status = AgentRunStatus.RECEIVED;
                validateAttachments();
                List<ModelRequest.ConversationEntry> history = withinBudget(this::loadConversationHistory);
                String attachmentUrls = attachmentReferences().stream()
                        .collect(java.util.stream.Collectors.joining("\n"));
                Message userMessage = withinBudget(() -> conversationService.appendMessage(
                        context.tenantId(), context.userId(), command.conversationId(), Message.Role.USER,
                        command.question(), attachmentUrls));
                emit(ChatEvent.messageStart(run.id(), traceId, userMessage.id()));
                messages.addAll(history);
                messages.add(new ModelRequest.ConversationMessage("user", modelQuestion(), attachmentParts()));
                validatePageProject();
                moveTo(AgentRunStatus.ROUTING);
                emit(ChatEvent.status(run.id(), traceId, status));
                moveTo(AgentRunStatus.PLANNING);
                emit(ChatEvent.status(run.id(), traceId, status));
                retrieveKnowledgeIfRequired();
                callModel();
            } catch (RuntimeException exception) {
                if (isTimeout(exception)) {
                    fail("AGENT_RUN_TIMEOUT", AgentRunStatus.TIMEOUT);
                } else {
                    fail(safeCode(exception), exception instanceof AgentException agentException
                            && agentException.status().value() == 403
                                    ? AgentRunStatus.PERMISSION_DENIED : AgentRunStatus.FAILED);
                }
            }
        }

        private void validatePageProject() {
            ChatCommand.PageContext page = command.pageContext();
            if (page != null && page.projectId() != null && !page.projectId().isBlank()
                    && !context.canAccessProject(page.projectId())) {
                throw new AgentException(
                        "AGENT_PROJECT_FORBIDDEN", org.springframework.http.HttpStatus.FORBIDDEN, "Project is not permitted");
            }
        }

        private String modelQuestion() {
            ChatCommand.PageContext page = command.pageContext();
            if (page == null) return command.question();
            StringBuilder safe = new StringBuilder(command.question()).append("\n[trusted-page-context");
            appendContext(safe, "pageCode", page.pageCode());
            appendContext(safe, "projectId", page.projectId());
            appendContext(safe, "businessType", page.businessType());
            appendContext(safe, "businessId", page.businessId());
            return safe.append(']').toString();
        }

        private List<ModelRequest.AttachmentPart> attachmentParts() {
            if (attachmentService == null || attachmentPublicBaseUrl.isBlank()) return List.of();
            return command.attachmentIds().stream().map(id -> withinBudget(() -> attachmentService.get(id,
                    context.tenantId(), context.userId()))).filter(a -> a.detectedMediaType() != null
                    && a.detectedMediaType().startsWith("image/"))
                    .map(a -> new ModelRequest.AttachmentPart(attachmentPublicBaseUrl + "/" + a.objectKey(),
                            a.detectedMediaType(), a.originalFilename())).toList();
        }

        private List<String> attachmentReferences() {
            if (attachmentService == null || attachmentPublicBaseUrl.isBlank()) return List.of();
            return command.attachmentIds().stream()
                    .map(id -> withinBudget(() -> attachmentService.get(id, context.tenantId(), context.userId())))
                    .map(a -> attachmentPublicBaseUrl + "/" + a.objectKey()).toList();
        }

        private void appendContext(StringBuilder target, String key, String value) {
            if (value != null && !value.isBlank()) target.append(' ').append(key).append('=').append(escape(value));
        }

        private void retrieveKnowledgeIfRequired() {
            if (knowledgeSearchService == null || !context.permissions().contains("knowledge:read")
                    || context.knowledgeSpaceIds().isEmpty() || command.pageContext() == null
                    || command.pageContext().projectId() == null || !requiresKnowledge(command.question())) {
                return;
            }
            moveTo(AgentRunStatus.RETRIEVING);
            emit(ChatEvent.status(run.id(), traceId, status));
            citations = withinBudget(() -> knowledgeSearchService.search(new KnowledgeSearchQuery(command.question(),
                    context.knowledgeSpaceIds(), command.pageContext().projectId(), MAX_CITATIONS), context))
                    .stream().limit(MAX_CITATIONS).toList();
            if (terminated.get() || sink.isCancelled()) return;
            withinBudget(() -> { runService.recordStep(context.tenantId(), context.userId(), run.id(), "KNOWLEDGE_SEARCH",
                    "{\"projectScoped\":true,\"spaceCount\":" + context.knowledgeSpaceIds().size() + "}",
                    "{\"citationCount\":" + citations.size() + "}"); return null; });
            for (KnowledgeCitation citation : citations) {
                run = withinBudget(() -> runService.recordAudit(context.tenantId(), context.userId(), run.id(),
                        0, 0, null, citation.citationToken(), null));
                emit(ChatEvent.citation(run.id(), traceId, Map.of(
                        "citationToken", citation.citationToken(), "documentId", citation.documentId(),
                        "title", citation.title(), "location", citation.location())));
            }
        }

        private boolean requiresKnowledge(String question) {
            String normalized = question.toLowerCase(java.util.Locale.ROOT);
            return normalized.contains("知识") || normalized.contains("文档") || normalized.contains("规范")
                    || normalized.contains("依据") || normalized.contains("citation");
        }

        private void callModel() {
            if (terminated.get()) {
                return;
            }
            if (modelTurns >= MAX_MODEL_TURNS || !Instant.now().isBefore(deadline)) {
                fail(modelTurns >= MAX_MODEL_TURNS ? "AGENT_MODEL_TURN_LIMIT" : "AGENT_RUN_TIMEOUT",
                        modelTurns >= MAX_MODEL_TURNS ? AgentRunStatus.FAILED : AgentRunStatus.TIMEOUT);
                return;
            }
            moveTo(AgentRunStatus.GENERATING);
            emit(ChatEvent.status(run.id(), traceId, status));
            if (terminated.get() || sink.isCancelled()) {
                cancel();
                return;
            }
            modelTurns++;
            turnDeltas.setLength(0);
            turnTools.clear();
            turnCompleted = null;
            List<ModelRequest.AllowedToolSpecification> tools = toolRegistry.allowedReadOnlyTools(context).stream()
                    .filter(this::isRelevantTool)
                    .map(this::toolSpecification)
                    .toList();
            List<ModelRequest.RetrievedEvidence> evidence = citations.stream()
                    .map(citation -> new ModelRequest.RetrievedEvidence(citation.citationToken(), citation.excerpt()))
                    .toList();
            ModelRequest request = new ModelRequest(run.id(), "v1", List.copyOf(messages), tools, evidence);
            try {
                Disposable subscription = modelGateway.stream(request)
                        .timeout(remaining())
                        .publishOn(Schedulers.boundedElastic())
                        .subscribe(this::handleModelEventSafely, this::handleModelError, this::finishModelTurnSafely);
                modelSubscription.update(subscription);
            } catch (RuntimeException exception) {
                fail("AGENT_MODEL_FAILED", AgentRunStatus.FAILED);
            }
        }

        private boolean isRelevantTool(AgentTool<?, ?> tool) {
            if (!tool.key().startsWith("project.")) return true;
            StringBuilder contextText = new StringBuilder(command.question());
            messages.stream().filter(ModelRequest.ConversationMessage.class::isInstance)
                    .map(ModelRequest.ConversationMessage.class::cast)
                    .forEach(message -> contextText.append('\n').append(message.content()));
            String question = contextText.toString().toLowerCase(java.util.Locale.ROOT);
            return question.contains("项目") || question.contains("project") || question.contains("当前")
                    || question.contains("合同") || question.contains("材料") || question.contains("劳务")
                    || question.contains("机械") || question.contains("分包") || question.contains("付款")
                    || question.contains("回款")
                    || question.contains("统计") || question.contains("数量") || question.contains("多少")
                    || question.contains("筛选") || question.contains("查询") || question.contains("搜索")
                    || question.contains("分组") || question.contains("排序") || question.contains("预算")
                    || question.contains("立项") || question.contains("已建") || question.contains("已立")
                    || question.contains("查看更多") || question.contains("下一页")
                    || command.pageContext() != null && command.pageContext().projectId() != null;
        }

        private ModelRequest.AllowedToolSpecification toolSpecification(AgentTool<?, ?> tool) {
            return new ModelRequest.AllowedToolSpecification(
                    tool.key(), tool.description(), tool.argumentsSchemaJson());
        }

        private void handleModelEventSafely(ModelEvent event) {
            try {
                handleModelEvent(event);
            } catch (RuntimeException callbackFailure) {
                fail(isTimeout(callbackFailure) ? "AGENT_RUN_TIMEOUT" : "AGENT_CHAT_FAILED",
                        isTimeout(callbackFailure) ? AgentRunStatus.TIMEOUT : AgentRunStatus.FAILED);
            }
        }

        private void handleModelEvent(ModelEvent event) {
            if (terminated.get()) {
                return;
            }
            if (event instanceof ModelEvent.TextDelta delta) {
                if (delta.text() != null && !delta.text().isEmpty()) {
                    turnDeltas.append(delta.text());
                    if (turnDeltas.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 64 * 1024) {
                        fail("AGENT_ANSWER_TOO_LARGE", AgentRunStatus.FAILED);
                    }
                }
            } else if (event instanceof ModelEvent.ToolRequested requested) {
                turnTools.add(requested);
                String debugArguments = safeDebugToolRequest(requested.toolKey(), requested.argumentsJson());
                persistDebug("TOOL_REQUEST", requested.toolKey(), debugArguments);
                emit(ChatEvent.debug(run.id(), traceId, "TOOL_REQUEST", Map.of(
                        "toolKey", requested.toolKey(), "arguments", debugArguments)));
            } else if (event instanceof ModelEvent.Completed completed) {
                turnCompleted = completed;
                persistDebug("MODEL_RESPONSE", null, completed.text());
                emit(ChatEvent.debug(run.id(), traceId, "MODEL_RESPONSE", Map.of(
                        "text", completed.text() == null ? "" : completed.text(),
                        "inputTokens", completed.inputTokens(), "outputTokens", completed.outputTokens())));
            } else if (event instanceof ModelEvent.Failed failed) {
                String safeModelCode = modelFailureCode(failed.code());
                runService.recordStep(context.tenantId(), context.userId(), run.id(), "MODEL",
                        "{\"turn\":" + modelTurns + "}", "{\"outcome\":\"FAILED\",\"code\":\""
                                + safeModelCode + "\"}");
                fail(safeModelCode, safeModelCode.equals("AGENT_MODEL_TIMEOUT")
                        ? AgentRunStatus.TIMEOUT : AgentRunStatus.FAILED);
            }
        }

        private void handleModelError(Throwable error) {
            if (isTimeout(error)) {
                fail("AGENT_RUN_TIMEOUT", AgentRunStatus.TIMEOUT);
            } else {
                fail("AGENT_MODEL_FAILED", AgentRunStatus.FAILED);
            }
        }

        private void finishModelTurnSafely() {
            try {
                finishModelTurn();
            } catch (RuntimeException callbackFailure) {
                fail(isTimeout(callbackFailure) ? "AGENT_RUN_TIMEOUT" : "AGENT_CHAT_FAILED",
                        isTimeout(callbackFailure) ? AgentRunStatus.TIMEOUT : AgentRunStatus.FAILED);
            }
        }

        private void finishModelTurn() {
            if (terminated.get()) return;
            if (turnCompleted != null && !turnTools.isEmpty()) {
                run = withinBudget(() -> runService.recordAudit(context.tenantId(), context.userId(), run.id(),
                        turnCompleted.inputTokens(), turnCompleted.outputTokens(), null, null, null));
                withinBudget(() -> { runService.recordStep(context.tenantId(), context.userId(), run.id(), "MODEL",
                        "{\"turn\":" + modelTurns + ",\"toolCount\":" + turnTools.size() + "}",
                        "{\"inputTokens\":" + turnCompleted.inputTokens() + ",\"outputTokens\":"
                                + turnCompleted.outputTokens() + "}"); return null; });
            }
            if (!turnTools.isEmpty()) {
                for (ModelEvent.ToolRequested requested : List.copyOf(turnTools)) {
                    if (terminated.get() || !executeTool(requested)) return;
                }
                callModel();
                return;
            }
            completeAnswer();
        }

        private boolean executeTool(ModelEvent.ToolRequested request) {
            long toolStarted = System.nanoTime();
            try {
                JsonNode input = objectMapper.readTree(request.argumentsJson());
                if (input == null || !input.isObject()) {
                    throw new IllegalArgumentException("tool input must be an object");
                }
                String normalizedArguments = objectMapper.writeValueAsString(input);
                String cacheKey = request.toolKey() + "\n" + normalizedArguments;
                CachedToolResult cached = toolResultCache.get(cacheKey);
                if (cached != null) {
                    moveTo(AgentRunStatus.TOOL_SELECTING);
                    emit(ChatEvent.toolStart(run.id(), traceId, request.toolKey()));
                    moveTo(AgentRunStatus.TOOL_EXECUTING);
                    emit(ChatEvent.status(run.id(), traceId, status));
                    messages.add(new ModelRequest.ToolResultMessage(
                            request.callId(), request.toolKey(), normalizedArguments, cached.serializedResult()));
                    persistDebug("TOOL_RESULT", request.toolKey(), cached.debugResult());
                    emit(ChatEvent.debug(run.id(), traceId, "TOOL_RESULT", Map.of(
                            "toolKey", request.toolKey(), "result", cached.debugResult(), "cached", true)));
                    emit(ChatEvent.toolResult(run.id(), traceId, request.toolKey()));
                    return true;
                }
                if (toolCalls >= MAX_TOOL_CALLS) {
                    fail("AGENT_TOOL_CALL_LIMIT", AgentRunStatus.FAILED);
                    return false;
                }
                toolCalls++;
                moveTo(AgentRunStatus.TOOL_SELECTING);
                emit(ChatEvent.toolStart(run.id(), traceId, request.toolKey()));
                CompletableFuture<Object> toolFuture;
                synchronized (terminalLock) {
                    if (terminated.get() || sink.isCancelled()) return false;
                    moveTo(AgentRunStatus.TOOL_EXECUTING);
                    emit(ChatEvent.status(run.id(), traceId, status));
                    if (terminated.get() || sink.isCancelled()) return false;
                    toolFuture = startToolWithinBudget(() -> toolExecutor.execute(request.toolKey(), input, context));
                }
                Object result = awaitTool(toolFuture);
                if (terminated.get() || sink.isCancelled()) return false;
                String serializedResult = objectMapper.writeValueAsString(result);
                long durationMillis = Duration.ofNanos(System.nanoTime() - toolStarted).toMillis();
                String debugResult = safeDebugToolResult(request.toolKey(), result, serializedResult, durationMillis);
                persistDebug("TOOL_RESULT", request.toolKey(), debugResult);
                emit(ChatEvent.debug(run.id(), traceId, "TOOL_RESULT", Map.of(
                        "toolKey", request.toolKey(), "result", debugResult)));
                String risk = toolRegistry.require(request.toolKey()).risk().name();
                int resultSize = serializedResult.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                synchronized (terminalLock) {
                    if (terminated.get() || sink.isCancelled()) return false;
                    try {
                        run = withinBudget(() -> runService.recordAudit(
                                context.tenantId(), context.userId(), run.id(), 0, 0,
                                request.toolKey() + ":" + risk + ":SUCCEEDED:" + durationMillis + ":" + resultSize,
                                null, null));
                        withinBudget(() -> {
                            runService.recordStep(context.tenantId(), context.userId(), run.id(), "TOOL",
                                    "{\"toolKey\":\"" + request.toolKey() + "\",\"risk\":\"" + risk + "\"}",
                                    "{\"outcome\":\"SUCCEEDED\",\"durationMillis\":" + durationMillis
                                            + ",\"resultSizeBytes\":" + resultSize + "}");
                            return null;
                        });
                    } catch (RuntimeException persistenceFailure) {
                        throw new ToolAuditPersistenceException(persistenceFailure);
                    }
                    toolResultCache.put(cacheKey, new CachedToolResult(serializedResult, debugResult));
                    messages.add(new ModelRequest.ToolResultMessage(
                            request.callId(), request.toolKey(), normalizedArguments, serializedResult));
                    emit(ChatEvent.toolResult(run.id(), traceId, request.toolKey()));
                }
                return true;
            } catch (AgentException exception) {
                if (terminated.get()) return false;
                persistDebug("TOOL_ERROR", request.toolKey(), exception.code() + ": " + exception.getMessage());
                emit(ChatEvent.debug(run.id(), traceId, "TOOL_ERROR", Map.of(
                        "toolKey", request.toolKey(), "error", exception.code(), "message", exception.getMessage())));
                if (!recordFailedTool(request.toolKey(), safeToolOutcome(exception), toolStarted)) return false;
                    fail(exception.code(), exception.status().value() == 403
                            ? AgentRunStatus.PERMISSION_DENIED : AgentRunStatus.FAILED,
                            exception.getMessage());
                return false;
            } catch (ToolAuditPersistenceException exception) {
                if (terminated.get()) return false;
                fail(isTimeout(exception) ? "AGENT_RUN_TIMEOUT" : "AGENT_PERSISTENCE_FAILED",
                        isTimeout(exception) ? AgentRunStatus.TIMEOUT : AgentRunStatus.FAILED);
                return false;
            } catch (RuntimeException exception) {
                if (terminated.get()) return false;
                persistDebug("TOOL_ERROR", request.toolKey(), "RUNTIME_ERROR: "
                        + (exception.getMessage() == null ? "" : exception.getMessage()));
                emit(ChatEvent.debug(run.id(), traceId, "TOOL_ERROR", Map.of(
                        "toolKey", request.toolKey(), "error", "RUNTIME_ERROR", "message", exception.getMessage() == null ? "" : exception.getMessage())));
                if (!recordFailedTool(request.toolKey(),
                        isTimeout(exception) ? "TIMED_OUT" : "INVALID_INPUT", toolStarted)) return false;
                fail(isTimeout(exception) ? "AGENT_RUN_TIMEOUT" : "AGENT_TOOL_INVALID_INPUT",
                        isTimeout(exception) ? AgentRunStatus.TIMEOUT : AgentRunStatus.FAILED);
                return false;
            } catch (Exception exception) {
                if (terminated.get()) return false;
                persistDebug("TOOL_ERROR", request.toolKey(), "INVALID_INPUT: "
                        + (exception.getMessage() == null ? "" : exception.getMessage()));
                if (!recordFailedTool(request.toolKey(), "INVALID_INPUT", toolStarted)) return false;
                fail("AGENT_TOOL_INVALID_INPUT", AgentRunStatus.FAILED);
                return false;
            }
        }

        private boolean recordFailedTool(String toolKey, String outcome, long started) {
            try {
                run = withinBudget(() -> runService.recordAudit(context.tenantId(), context.userId(), run.id(),
                        0, 0, toolKey + ":" + outcome, null, null));
                recordFailedToolStep(toolKey, outcome, started);
                return true;
            } catch (RuntimeException persistenceFailure) {
                fail(isTimeout(persistenceFailure) ? "AGENT_RUN_TIMEOUT" : "AGENT_PERSISTENCE_FAILED",
                        isTimeout(persistenceFailure) ? AgentRunStatus.TIMEOUT : AgentRunStatus.FAILED);
                return false;
            }
        }

        private void recordFailedToolStep(String toolKey, String outcome, long started) {
            if (terminated.get()) return;
            String risk = safeRisk(toolKey);
            long duration = Duration.ofNanos(System.nanoTime() - started).toMillis();
            withinBudget(() -> { runService.recordStep(context.tenantId(), context.userId(), run.id(), "TOOL",
                    "{\"toolKey\":\"" + escape(toolKey) + "\",\"risk\":\"" + risk + "\"}",
                    "{\"outcome\":\"" + outcome + "\",\"durationMillis\":" + duration
                            + ",\"resultSizeBytes\":0}"); return null; });
        }

        private String safeRisk(String toolKey) {
            try { return toolRegistry.require(toolKey).risk().name(); }
            catch (RuntimeException ignored) { return "UNKNOWN"; }
        }

        private String safeToolOutcome(AgentException exception) {
            if (exception.status().value() == 403) return "DENIED";
            if (exception.code().contains("TIMEOUT")) return "TIMED_OUT";
            if (exception.code().contains("TOO_LARGE")) return "RESULT_TOO_LARGE";
            if (exception.code().contains("INVALID_INPUT")) return "INVALID_INPUT";
            return "FAILED";
        }

        private void completeAnswer() {
            ModelEvent.Completed completed = turnCompleted;
            if (completed == null) {
                fail("AGENT_MODEL_PROTOCOL_ERROR", AgentRunStatus.FAILED);
                return;
            }
            String answer = completed.text() == null || completed.text().isBlank()
                    ? turnDeltas.toString() : completed.text();
            if (answer.isBlank()) {
                fail("AGENT_MODEL_EMPTY_RESPONSE", AgentRunStatus.FAILED);
                return;
            }
            if (utf8Size(answer) > 64 * 1024) {
                fail("AGENT_ANSWER_TOO_LARGE", AgentRunStatus.FAILED);
                return;
            }
            if (isStandaloneToolArguments(answer)) {
                fail("AGENT_MODEL_PROTOCOL_ERROR", AgentRunStatus.FAILED);
                return;
            }
            emit(ChatEvent.delta(run.id(), traceId, answer));
            try {
                synchronized (terminalLock) {
                    if (terminated.get() || sink.isCancelled()) return;
                    AgentRunService.Completion completion = withinBudget(() -> runService.completeWithAssistant(
                            context.tenantId(), context.userId(), command.conversationId(), run.id(), status, answer,
                            completed.inputTokens(), completed.outputTokens(),
                            "{\"turn\":" + modelTurns + ",\"toolCount\":0}",
                            "{\"inputTokens\":" + completed.inputTokens() + ",\"outputTokens\":"
                                    + completed.outputTokens() + "}"));
                    run = completion.run();
                    status = AgentRunStatus.COMPLETED;
                    terminated.set(true);
                    sink.next(ChatEvent.messageEnd(run.id(), traceId, completion.message().id()));
                    sink.complete();
                }
            } catch (RuntimeException persistenceFailure) {
                fail(isTimeout(persistenceFailure) ? "AGENT_RUN_TIMEOUT" : "AGENT_PERSISTENCE_FAILED",
                        isTimeout(persistenceFailure) ? AgentRunStatus.TIMEOUT : AgentRunStatus.FAILED);
            }
        }

        private void moveTo(AgentRunStatus next) {
            run = withinBudget(() -> runService.transition(context.tenantId(), context.userId(), run.id(), status, next));
            status = next;
        }

        private void emit(ChatEvent event) {
            if (!terminated.get() && !sink.isCancelled()) {
                sink.next(new ChatEvent(event.type(), event.runId(), ++sequence, event.traceId(), event.messageId(),
                        event.code(), event.text(), event.toolKey(), event.data()));
            }
        }

        private void validateAttachments() {
            if (command.attachmentIds().size() > 10) throw new AgentException("AGENT_ATTACHMENTS_TOO_MANY", org.springframework.http.HttpStatus.BAD_REQUEST, "Too many attachments");
            if (attachmentService == null) return;
            for (java.util.UUID id : command.attachmentIds()) {
                com.smart.agent.attachment.Attachment a = withinBudget(() -> attachmentService.get(id, context.tenantId(), context.userId()));
                boolean image = a.detectedMediaType() != null && a.detectedMediaType().startsWith("image/");
                if (a.status() != AttachmentStatus.READY && !(a.status() == AttachmentStatus.UPLOADED && !image)) {
                    throw new AgentException("AGENT_ATTACHMENT_NOT_READY", org.springframework.http.HttpStatus.CONFLICT, "Attachment is not ready");
                }
            }
        }

        private void cancel() {
            synchronized (terminalLock) {
                if (!terminated.compareAndSet(false, true)) return;
                modelSubscription.dispose();
                cancelActiveTool();
                if (run != null && status != null && !isTerminal(status)) {
                    try {
                        run = runService.finishTerminal(context.tenantId(), context.userId(), run.id(), status,
                                AgentRunStatus.CANCELLED, null);
                        status = AgentRunStatus.CANCELLED;
                    } catch (RuntimeException ignored) {
                        // The disconnected client cannot receive a second failure.
                    }
                }
            }
        }

        private void fail(String code, AgentRunStatus terminalStatus) {
            fail(code, terminalStatus, null);
        }

        private record CachedToolResult(String serializedResult, String debugResult) {}

        private void fail(String code, AgentRunStatus terminalStatus, String detail) {
            synchronized (terminalLock) {
                if (!terminated.compareAndSet(false, true)) return;
                modelSubscription.dispose();
                cancelActiveTool();
                String message = errorMessage(code, detail);
                if (run != null && status != null && !isTerminal(status)) {
                    try {
                        run = runService.finishTerminal(context.tenantId(), context.userId(), run.id(), status,
                                terminalStatus, code);
                        status = terminalStatus;
                        conversationService.appendMessage(context.tenantId(), context.userId(),
                                command.conversationId(), Message.Role.ASSISTANT, message);
                    } catch (RuntimeException persistenceFailure) {
                        code = "AGENT_PERSISTENCE_FAILED";
                        message = errorMessage(code);
                    }
                }
                if (!sink.isCancelled()) {
                    sink.next(ChatEvent.error(run == null ? null : run.id(), traceId, code, message));
                    sink.complete();
                }
            }
        }

        private void persistDebug(String type, String toolKey, String content) {
            try {
                String value = content == null ? "" : content;
                if (value.length() > 20000) value = value.substring(0, 20000);
                runService.recordStep(context.tenantId(), context.userId(), run.id(), "AI_DEBUG_" + type,
                        toolKey, value);
            } catch (RuntimeException ignored) {
                // Debug persistence must not affect the chat response.
            }
        }

        private String safeDebugToolRequest(String toolKey, String arguments) {
            if (!"web.search".equals(toolKey)) return arguments;
            try {
                JsonNode input = objectMapper.readTree(arguments);
                String query = input.path("query").asText("");
                new WebSearchPolicy().validate(query);
                com.fasterxml.jackson.databind.node.ObjectNode safe = objectMapper.createObjectNode();
                safe.put("query", query.trim().replaceAll("\\s+", " "));
                if (input.path("maxResults").isIntegralNumber()) {
                    safe.put("maxResults", input.path("maxResults").intValue());
                }
                if (input.path("freshness").isTextual()) {
                    safe.put("freshness", input.path("freshness").textValue());
                }
                return safe.toString();
            } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ignored) {
                return "{\"query\":\"[已拦截的敏感查询]\"}";
            }
        }

        private String safeDebugToolResult(
                String toolKey, Object result, String serializedResult, long durationMillis) {
            if (!"web.search".equals(toolKey) || !(result instanceof WebSearchResult webResult)) {
                return serializedResult;
            }
            com.fasterxml.jackson.databind.node.ObjectNode safe = objectMapper.createObjectNode();
            safe.put("query", webResult.query());
            safe.put("searchedAt", webResult.searchedAt());
            safe.put("summary", webResult.summary());
            safe.put("provider", webResult.provider());
            safe.put("durationMillis", durationMillis);
            safe.set("sources", objectMapper.valueToTree(webResult.sources()));
            return safe.toString();
        }

        private String modelFailureCode(String code) {
            if (code != null && code.contains("TIMEOUT")) return "AGENT_MODEL_TIMEOUT";
            if ("MODEL_TOOL_ARGUMENTS_INVALID".equals(code)) return "AGENT_QUERY_INVALID_REQUEST";
            if ("MODEL_TOOL_SCHEMA_INVALID".equals(code)) return "AGENT_QUERY_UNAVAILABLE";
            return "AGENT_MODEL_FAILED";
        }

        private List<ModelRequest.ConversationEntry> loadConversationHistory() {
            List<Message> persisted = conversationService.find(
                    context.tenantId(), context.userId(), command.conversationId()).messages().stream()
                    .filter(message -> message.role() == Message.Role.USER || message.role() == Message.Role.ASSISTANT)
                    .toList();
            List<ModelRequest.ConversationEntry> reversed = new ArrayList<>();
            int bytes = 0;
            for (int index = persisted.size() - 1;
                    index >= 0 && reversed.size() < MAX_HISTORY_MESSAGES; index--) {
                Message message = persisted.get(index);
                int messageBytes = utf8Size(message.content());
                if (!reversed.isEmpty() && bytes + messageBytes > MAX_HISTORY_BYTES) break;
                reversed.add(new ModelRequest.ConversationMessage(
                        message.role() == Message.Role.USER ? "user" : "assistant", message.content()));
                bytes += messageBytes;
            }
            java.util.Collections.reverse(reversed);
            return reversed;
        }

        private String errorMessage(String code) {
            return AgentErrorMessageCatalog.message(code, !command.attachmentIds().isEmpty());
        }

        private String errorMessage(String code, String detail) {
            if (detail != null && !detail.isBlank()
                    && ("AGENT_QUERY_FIELD_UNKNOWN".equals(code)
                    || "AGENT_QUERY_VALUE_INVALID".equals(code)
                    || "AGENT_QUERY_OPERATOR_INVALID".equals(code))) {
                return AgentErrorMessageCatalog.queryClarification(code, detail);
            }
            return errorMessage(code);
        }

        private String safeCode(RuntimeException exception) {
            return exception instanceof AgentException agentException
                    ? agentException.code() : "AGENT_CHAT_FAILED";
        }

        private boolean isTerminal(AgentRunStatus current) {
            return current == AgentRunStatus.COMPLETED || current == AgentRunStatus.FAILED
                    || current == AgentRunStatus.CANCELLED || current == AgentRunStatus.TIMEOUT
                    || current == AgentRunStatus.PERMISSION_DENIED;
        }

        private String escape(String value) {
            return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
        }

        private Duration remaining() {
            Duration remaining = Duration.between(Instant.now(), deadline);
            if (remaining.isNegative() || remaining.isZero()) {
                throw new java.util.concurrent.CompletionException(new java.util.concurrent.TimeoutException());
            }
            return remaining;
        }

        private <T> T withinBudget(Callable<T> operation) {
            return Mono.fromCallable(operation).subscribeOn(Schedulers.boundedElastic())
                    .timeout(remaining()).block();
        }

        private <T> CompletableFuture<T> startToolWithinBudget(Callable<T> operation) {
            CompletableFuture<T> future = Mono.fromCallable(operation).subscribeOn(Schedulers.boundedElastic())
                    .timeout(remaining()).toFuture();
            activeToolOperation.set(future);
            return future;
        }

        private <T> T awaitTool(CompletableFuture<T> future) {
            try {
                return future.join();
            } catch (java.util.concurrent.CompletionException exception) {
                if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
                throw exception;
            } finally {
                activeToolOperation.compareAndSet(future, null);
            }
        }

        private void cancelActiveTool() {
            CompletableFuture<?> future = activeToolOperation.getAndSet(null);
            if (future != null) future.cancel(true);
        }

        private int utf8Size(String value) {
            return value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }

        private boolean isStandaloneToolArguments(String answer) {
            String candidate = answer.trim();
            if (candidate.startsWith("```") && candidate.endsWith("```")) {
                candidate = candidate.replaceFirst("^```(?:json)?\\s*", "")
                        .replaceFirst("\\s*```$", "").trim();
            }
            try {
                JsonNode node = objectMapper.readTree(candidate);
                if (node == null || !node.isObject() || node.isEmpty()) return false;
                java.util.Set<String> toolFields = java.util.Set.of(
                        "projectId", "projectCode", "projectName", "contractType",
                        "keyword", "status", "page", "pageSize");
                java.util.Iterator<String> fields = node.fieldNames();
                while (fields.hasNext()) if (!toolFields.contains(fields.next())) return false;
                return true;
            } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
                return false;
            }
        }

        private boolean isTimeout(Throwable error) {
            for (Throwable current = error; current != null; current = current.getCause()) {
                if (current instanceof java.util.concurrent.TimeoutException
                        || current.getClass().getSimpleName().contains("Timeout")) return true;
            }
            return false;
        }
    }

    private static final class ToolAuditPersistenceException extends RuntimeException {
        private ToolAuditPersistenceException(Throwable cause) { super(cause); }
    }
}
