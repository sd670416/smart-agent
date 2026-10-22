package com.smart.agent.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.common.error.AgentException;
import com.smart.agent.conversation.ConversationService;
import com.smart.agent.context.ConversationContextService;
import com.smart.agent.attachment.AttachmentService;
import com.smart.agent.attachment.AttachmentStatus;
import com.smart.agent.conversation.Message;
import com.smart.agent.knowledge.KnowledgeCitation;
import com.smart.agent.knowledge.KnowledgeSearchQuery;
import com.smart.agent.knowledge.KnowledgeSearchService;
import com.smart.agent.model.ModelCredentialSource;
import com.smart.agent.model.ModelEvent;
import com.smart.agent.model.ModelExecutionHandle;
import com.smart.agent.model.ModelGateway;
import com.smart.agent.model.DynamicModelRegistry;
import com.smart.agent.model.ModelRequest;
import com.smart.agent.run.AgentRun;
import com.smart.agent.run.AgentRunService;
import com.smart.agent.run.AgentRunStatus;
import com.smart.agent.security.AgentUserContext;
import com.smart.agent.routing.QueryIntentResolver;
import com.smart.agent.routing.IntentResolution;
import com.smart.agent.routing.BusinessDomainDescriptor;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.clarification.PendingClarification;
import com.smart.agent.clarification.PendingClarificationService;
import com.smart.agent.tool.AgentTool;
import com.smart.agent.tool.ToolContext;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposables;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.scheduler.Schedulers;
import java.util.concurrent.Callable;

public class ChatOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrator.class);
    static final int MAX_MODEL_TURNS = 6;
    static final int MAX_TOOL_CALLS = 5;
    static final int MAX_CITATIONS = 20;
    static final int MAX_HISTORY_MESSAGES = 20;
    static final int MAX_HISTORY_BYTES = 32 * 1024;
    static final Duration MAX_RUN_DURATION = Duration.ofSeconds(300);

    private final ConversationService conversationService;
    private final AgentRunService runService;
    private final ModelGateway modelGateway;
    private final DynamicModelRegistry modelRegistry;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;
    private final KnowledgeSearchService knowledgeSearchService;
    private final Duration runBudget;
    private final AttachmentService attachmentService;
    private final String attachmentPublicBaseUrl;
    private final ConversationContextService conversationContextService;
    /** Optional during compatibility construction; Spring production wiring supplies it. */
    private QueryIntentResolver queryIntentResolver;
    private PendingClarificationService pendingClarificationService;
    private com.smart.agent.question.QuestionRecordService questionRecords;
    public void setQuestionRecords(com.smart.agent.question.QuestionRecordService records) { this.questionRecords=records; }

    /**
     * 兼容构造：直接使用固定网关，不经过模型注册中心。
     * 保留用于单元测试与尚未接入模型配置中心的场景。
     */
    public ChatOrchestrator(
            ConversationService conversationService,
            AgentRunService runService,
            ModelGateway modelGateway,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            ObjectMapper objectMapper) {
        this(conversationService, runService, modelGateway, null, toolRegistry, toolExecutor, objectMapper,
                null, null, null, null, null);
    }

    public ChatOrchestrator(
            ConversationService conversationService,
            AgentRunService runService,
            ModelGateway modelGateway,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            ObjectMapper objectMapper,
            KnowledgeSearchService knowledgeSearchService) {
        this(conversationService, runService, modelGateway, null, toolRegistry, toolExecutor, objectMapper,
                knowledgeSearchService, MAX_RUN_DURATION, null, null, null);
    }

    /**
     * 生产构造：每轮运行从注册中心取得一次执行句柄，整轮工具循环复用其网关。
     */
    public ChatOrchestrator(
            ConversationService conversationService,
            AgentRunService runService,
            DynamicModelRegistry modelRegistry,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            ObjectMapper objectMapper,
            KnowledgeSearchService knowledgeSearchService,
            Duration runBudget,
            AttachmentService attachmentService,
            String attachmentPublicBaseUrl,
            ConversationContextService conversationContextService) {
        this(conversationService, runService, null, modelRegistry, toolRegistry, toolExecutor, objectMapper,
                knowledgeSearchService, runBudget, attachmentService, attachmentPublicBaseUrl,
                conversationContextService);
    }

    public ChatOrchestrator(
            ConversationService conversationService,
            AgentRunService runService,
            DynamicModelRegistry modelRegistry,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            ObjectMapper objectMapper,
            KnowledgeSearchService knowledgeSearchService,
            Duration runBudget,
            AttachmentService attachmentService,
            String attachmentPublicBaseUrl,
            ConversationContextService conversationContextService,
            QueryIntentResolver queryIntentResolver,
            PendingClarificationService pendingClarificationService) {
        this(conversationService, runService, modelRegistry, toolRegistry, toolExecutor, objectMapper,
                knowledgeSearchService, runBudget, attachmentService, attachmentPublicBaseUrl,
                conversationContextService);
        this.queryIntentResolver = queryIntentResolver;
        this.pendingClarificationService = pendingClarificationService;
    }

    public ChatOrchestrator(
            ConversationService conversationService,
            AgentRunService runService,
            ModelGateway modelGateway,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            ObjectMapper objectMapper,
            KnowledgeSearchService knowledgeSearchService,
            Duration runBudget,
            AttachmentService attachmentService,
            String attachmentPublicBaseUrl,
            ConversationContextService conversationContextService,
            QueryIntentResolver queryIntentResolver,
            PendingClarificationService pendingClarificationService) {
        this(conversationService, runService, modelGateway, toolRegistry, toolExecutor, objectMapper,
                knowledgeSearchService, runBudget, attachmentService, attachmentPublicBaseUrl,
                conversationContextService);
        this.queryIntentResolver = queryIntentResolver;
        this.pendingClarificationService = pendingClarificationService;
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
        this(conversationService, runService, modelGateway, null, toolRegistry, toolExecutor, objectMapper,
                knowledgeSearchService, runBudget, null, null, null);
    }

    ChatOrchestrator(ConversationService conversationService, AgentRunService runService, ModelGateway modelGateway,
            ToolRegistry toolRegistry, ToolExecutor toolExecutor, ObjectMapper objectMapper,
            KnowledgeSearchService knowledgeSearchService, Duration runBudget, AttachmentService attachmentService) {
        this(conversationService, runService, modelGateway, null, toolRegistry, toolExecutor, objectMapper,
                knowledgeSearchService, runBudget, attachmentService, null, null);
    }

    ChatOrchestrator(ConversationService conversationService, AgentRunService runService, ModelGateway modelGateway,
            ToolRegistry toolRegistry, ToolExecutor toolExecutor, ObjectMapper objectMapper,
            KnowledgeSearchService knowledgeSearchService, Duration runBudget, AttachmentService attachmentService,
            String attachmentPublicBaseUrl) {
        this(conversationService, runService, modelGateway, null, toolRegistry, toolExecutor, objectMapper,
                knowledgeSearchService, runBudget, attachmentService, attachmentPublicBaseUrl, null);
    }

    ChatOrchestrator(ConversationService conversationService, AgentRunService runService, ModelGateway modelGateway,
            ToolRegistry toolRegistry, ToolExecutor toolExecutor, ObjectMapper objectMapper,
            KnowledgeSearchService knowledgeSearchService, Duration runBudget, AttachmentService attachmentService,
            String attachmentPublicBaseUrl, ConversationContextService conversationContextService) {
        this(conversationService, runService, modelGateway, null, toolRegistry, toolExecutor, objectMapper,
                knowledgeSearchService, runBudget, attachmentService, attachmentPublicBaseUrl,
                conversationContextService);
    }

    ChatOrchestrator(ConversationService conversationService, AgentRunService runService, ModelGateway modelGateway,
            DynamicModelRegistry modelRegistry, ToolRegistry toolRegistry, ToolExecutor toolExecutor,
            ObjectMapper objectMapper, KnowledgeSearchService knowledgeSearchService, Duration runBudget,
            AttachmentService attachmentService, String attachmentPublicBaseUrl,
            ConversationContextService conversationContextService) {
        this.conversationService = conversationService;
        this.runService = runService;
        this.modelGateway = modelGateway;
        this.modelRegistry = modelRegistry;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.objectMapper = objectMapper;
        this.knowledgeSearchService = knowledgeSearchService;
        this.runBudget = runBudget;
        this.attachmentService = attachmentService;
        this.attachmentPublicBaseUrl = attachmentPublicBaseUrl == null ? "" : attachmentPublicBaseUrl.trim();
        this.conversationContextService = conversationContextService;
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
        private int projectToolCalls;
        private int approvalToolCalls;
        private int boardToolCalls;
        private boolean requiresFreshProjectData;
        private boolean requiresFreshApprovalData;
        private boolean requiresFreshBoardData;
        private boolean requiresRelativeTimeForProject;
        private boolean requiresRelativeTimeForApproval;
        private BusinessDomainDescriptor additionalQueryDomain;
        private boolean requiresRelativeTimeForAdditionalDomain;
        private int additionalToolCalls;
        private boolean timeToolCompleted;
        private String activeBusinessDomain;
        private String permissionQuerySubject;
        private String effectiveQuestion;
        private String unsupportedReason;
        private String questionDomain;
        private JsonNode projectClarificationArguments;
        private String progressDateSelection;
        private JsonNode comparisonClarificationArguments;
        private List<KnowledgeCitation> citations = List.of();
        private final StringBuilder turnDeltas = new StringBuilder();
        private final List<ModelEvent.ToolRequested> turnTools = new ArrayList<>();
        private ModelEvent.Completed turnCompleted;
        /** 本轮运行固定的模型执行句柄，运行结束必须释放。 */
        private ModelExecutionHandle modelHandle;
        private String preparationStage = "resolveModel";
        private ModelGateway modelGatewayForRun;
        private ToolContext.ModelBinding modelBindingForRun;
        private final AtomicBoolean modelHandleReleased = new AtomicBoolean();

        private Session(ChatCommand command, AgentUserContext context, String traceId, FluxSink<ChatEvent> sink) {
            this.command = command;
            this.context = context;
            this.traceId = traceId;
            this.sink = sink;
            this.effectiveQuestion = command.question();
        }

        void start() {
            sink.onCancel(this::cancel);
            sink.onDispose(this::cancel);
            try {
                String scopedModelId = resolveModelIdForRun();
                preparationStage = "acquireModel";
                ModelExecutionHandle handle = acquireModelHandle(scopedModelId);
                preparationStage = "startRun";
                bindRunToModel(handle, scopedModelId);
                status = AgentRunStatus.RECEIVED;
                preparationStage = "validateAttachments";
                validateAttachments();
                preparationStage = "loadHistory";
                List<ModelRequest.ConversationEntry> history = withinBudget(this::loadConversationHistory);
                preparationStage = "appendUserMessage";
                String attachmentUrls = attachmentReferences().stream()
                        .collect(java.util.stream.Collectors.joining("\n"));
                Message userMessage = withinBudget(() -> conversationService.appendMessage(
                        context.tenantId(), context.userId(), command.conversationId(), Message.Role.USER,
                        command.question(), attachmentUrls));
                emit(ChatEvent.messageStart(run.id(), traceId, userMessage.id()));
                run = withinBudget(() -> runService.bindMessages(context.tenantId(), context.userId(), run.id(), userMessage.id(), null));
                messages.addAll(history);
                preparationStage = "prepareContext";
                IntentResolution confirmedResolution = resolveClarificationSelection();
                activeBusinessDomain = confirmedResolution == null
                        ? loadActiveBusinessDomain()
                        : confirmedResolution.candidate().orElseThrow().domain().code();
                IntentResolution intentResolution = confirmedResolution == null
                        ? recordIntentResolution()
                        : confirmedResolution;
                String resolvedDomain = intentResolution == null ? null
                        : intentResolution.candidate().map(candidate -> candidate.domain().code()).orElse(null);
                questionDomain = resolvedDomain;
                permissionQuerySubject = intentResolution == null ? null
                        : intentResolution.candidate().map(candidate -> candidate.domain().name()).orElse(null);
                additionalQueryDomain = queryIntentResolver == null || resolvedDomain == null
                        || java.util.Set.of("PROJECT", "APPROVAL", "BOARD").contains(resolvedDomain)
                        ? null : queryIntentResolver.domain(resolvedDomain).orElse(null);
                requiresRelativeTimeForAdditionalDomain = additionalQueryDomain != null
                        && isRelativeTimeQuestion(effectiveQuestion);
                boolean projectContextFollowUp = isProjectContextFollowUp(
                        effectiveQuestion, history, activeBusinessDomain);
                requiresFreshProjectData = intentResolution != null
                        ? "PROJECT".equals(resolvedDomain)
                        : ((!isApprovalQuestion(effectiveQuestion) || projectContextFollowUp)
                        && !isBoardQuestion(effectiveQuestion)
                        && (isProjectQuestion(effectiveQuestion)
                        || isProjectFollowUp(effectiveQuestion, history)
                        || projectContextFollowUp));
                if (additionalQueryDomain == null && projectContextFollowUp && !isExplicitApprovalWorkflowQuestion(effectiveQuestion)
                        && !isBoardQuestion(effectiveQuestion)
                        && intentResolution != null
                        && intentResolution.status() == IntentResolution.Status.RESOLVED) {
                    requiresFreshProjectData = true;
                }
                requiresRelativeTimeForProject = requiresFreshProjectData
                        && isRelativeTimeQuestion(effectiveQuestion);
                requiresFreshApprovalData = intentResolution != null
                        ? "APPROVAL".equals(resolvedDomain) && !requiresFreshProjectData
                        : !projectContextFollowUp && (isApprovalQuestion(effectiveQuestion)
                        || isApprovalFollowUp(effectiveQuestion, history));
                requiresRelativeTimeForApproval = requiresFreshApprovalData
                        && isRelativeTimeQuestion(effectiveQuestion);
                requiresFreshBoardData = intentResolution != null
                        ? "BOARD".equals(resolvedDomain) && !requiresFreshProjectData
                        : isBoardQuestion(effectiveQuestion);
                if (intentResolution == null || intentResolution.status() != IntentResolution.Status.NEEDS_CLARIFICATION) {
                    validateProjectMenuPermission(history);
                }
                // 先完成业务域判定，再生成带可信路由提示的本轮消息。
                // 否则 modelQuestion 无法使用本轮上下文判定结果。
                appendAdditionalQueryContext();
                messages.add(new ModelRequest.ConversationMessage("user", modelQuestion(), attachmentParts()));
                validatePageProject();
                preparationStage = "routeRun";
                moveTo(AgentRunStatus.ROUTING);
                emit(ChatEvent.status(run.id(), traceId, status));
                moveTo(AgentRunStatus.PLANNING);
                emit(ChatEvent.status(run.id(), traceId, status));
                if (completeClarification(intentResolution)) return;
                if (completeOutputTypeClarification()) return;
                retrieveKnowledgeIfRequired();
                preparationStage = "callModel";
                if (resolveProjectOrdinalFollowUp()) {
                    callModel();
                    return;
                }
                if (resolveApprovalPageFollowUp()) {
                    callModel();
                    return;
                }
                if (resolveApprovalOrdinalFollowUp()) {
                    callModel();
                    return;
                }
                callModel();
            } catch (RuntimeException exception) {
                log.error("AI会话准备失败: runId={}, traceId={}, stage={}, exception={}",
                        run == null ? null : run.id(), traceId, preparationStage,
                        exception.getClass().getName(), exception);
                if (isTimeout(exception)) {
                    fail("AGENT_RUN_TIMEOUT", AgentRunStatus.TIMEOUT);
                } else {
                    fail(safeCode(exception), exception instanceof AgentException agentException
                            && agentException.status().value() == 403
                                    ? AgentRunStatus.PERMISSION_DENIED : AgentRunStatus.FAILED);
                }
            }
        }

        /** Records the domain decision used by this turn and the clarification flow. */
        private IntentResolution recordIntentResolution() {
            if (queryIntentResolver == null) return null;
            Map<String, Object> page = new HashMap<>();
            ChatCommand.PageContext pageContext = command.pageContext();
            if (pageContext != null) {
                page.put("pageCode", pageContext.pageCode());
                page.put("businessType", pageContext.businessType());
                page.put("businessId", pageContext.businessId());
            }
            IntentResolution resolution = queryIntentResolver.resolve(
                    effectiveQuestion, page, activeBusinessDomain, context);
            if (run != null) {
                persistDebug("INTENT_RESOLUTION", "routing", intentResolutionJson(resolution));
            }
            return resolution;
        }

        private IntentResolution resolveClarificationSelection() {
            ChatCommand.ClarificationSelection selection = command.clarification();
            if (selection == null) return null;
            if (pendingClarificationService == null || queryIntentResolver == null) {
                throw clarificationFailure();
            }
            try {
                PendingClarification pending = withinBudget(() -> pendingClarificationService.resolve(
                        context.tenantId(), context.userId(), command.conversationId(),
                        selection.clarificationId(), selection.optionId()));
                JsonNode intent = objectMapper.readTree(pending.intentJson());
                JsonNode options = objectMapper.readTree(pending.optionsJson());
                String domainCode = null;
                for (JsonNode option : options) {
                    if (selection.optionId().equals(option.path("id").asText())) {
                        domainCode = option.path("domainCode").asText(null);
                        break;
                    }
                }
                String originalQuestion = intent.path("originalQuestion").asText(null);
                if (domainCode == null || originalQuestion == null || originalQuestion.isBlank()) {
                    throw clarificationFailure();
                }
                effectiveQuestion = originalQuestion;
                if ("COMPARISON_PERIOD".equals(intent.path("ambiguityType").asText())) {
                    if (!java.util.Set.of("PROJECT_RECEIPTS", "CONSTRUCTION_CONTRACT", "PROJECT_OUTPUT", "PAYMENT_REQUEST", "OTHER_PAYMENT_REQUEST", "PROJECT_REIMBURSE", "MATERIAL_REFUND").contains(domainCode)) throw clarificationFailure();
                    String period = switch (selection.optionId()) {
                        case "period:MONTH" -> "本月截至今天";
                        case "period:QUARTER" -> "本季度截至今天";
                        case "period:YEAR" -> "今年截至今天";
                        default -> throw clarificationFailure();
                    };
                    effectiveQuestion += "\n[用户确认比较期间=" + period + "，保留原问题的同比或环比、项目和日期维度，先取当前时间]";
                }
                if ("DATE_FIELD".equals(intent.path("ambiguityType").asText())) {
                    boolean validDate = "CONSTRUCTION_CONTRACT".equals(domainCode)
                            && java.util.Set.of("date:projectCreateDate", "date:signingDate", "date:createDate").contains(selection.optionId())
                            || "INVOICE_APPLICATION".equals(domainCode)
                            && java.util.Set.of("date:projectCreateDate", "date:createDate").contains(selection.optionId())
                            || ("PROJECT_PROGRESS".equals(domainCode) || "COMPANY_OPERATING".equals(domainCode) || "COST_CONTRACT".equals(domainCode) || "PROJECT_FUNDING".equals(domainCode) || "BUDGET_COST".equals(domainCode) || "PROJECT_PROFIT".equals(domainCode))
                            && java.util.Set.of("date:projectCreateDate", "date:allProjectDates").contains(selection.optionId())
                            || "MATERIAL_REFUND".equals(domainCode)
                            && java.util.Set.of("date:projectCreateDate", "date:returnDate", "date:createDate").contains(selection.optionId())
                            || "PROJECT_REIMBURSE".equals(domainCode)
                            && java.util.Set.of("date:projectCreateDate", "date:createDate", "date:reimburseDate").contains(selection.optionId())
                            || "OTHER_PAYMENT_REQUEST".equals(domainCode)
                            && java.util.Set.of("date:projectCreateDate", "date:createDate", "date:paymentDate").contains(selection.optionId())
                            || "PAYMENT_REQUEST".equals(domainCode)
                            && java.util.Set.of("date:projectCreateDate", "date:createDate").contains(selection.optionId())
                            || "PROJECT_OUTPUT".equals(domainCode)
                            && java.util.Set.of("date:projectCreateDate", "date:createDate").contains(selection.optionId())
                            || "PAYMENT_REGISTRATION".equals(domainCode)
                            && java.util.Set.of("date:projectCreateDate", "date:createDate", "date:receivedDate").contains(selection.optionId())
                            || "OTHER_INCOME_REGISTRATION".equals(domainCode)
                            && java.util.Set.of("date:projectCreateDate", "date:createDate", "date:receivedDate").contains(selection.optionId())
                            || "PROJECT_RECEIPTS".equals(domainCode)
                            && java.util.Set.of("date:projectCreateDate", "date:createDate", "date:receivedDate").contains(selection.optionId());
                    if (!validDate) throw clarificationFailure();
                    if (java.util.Set.of("PROJECT_PROGRESS", "COMPANY_OPERATING", "PROJECT_FUNDING",
                            "BUDGET_COST", "PROJECT_PROFIT").contains(domainCode))
                        progressDateSelection = selection.optionId();
                    effectiveQuestion += ("PROJECT_PROGRESS".equals(domainCode) || "COMPANY_OPERATING".equals(domainCode) || "COST_CONTRACT".equals(domainCode) || "PROJECT_FUNDING".equals(domainCode) || "BUDGET_COST".equals(domainCode) || "PROJECT_PROFIT".equals(domainCode)) && "date:allProjectDates".equals(selection.optionId())
                            ? "\n[用户确认不限定日期，删除dateField、startDate、endDate，不继承原问题或历史日期条件，查询" + ("PROJECT_PROGRESS".equals(domainCode) ? "当前施工进度" : "累计金额") + "]"
                            : "\n[用户确认的日期字段=" + selection.optionId().substring("date:".length()) + "]";
                }
                if ("COMPANY_REFERENCE".equals(intent.path("ambiguityType").asText())) {
                    if (!"COMPANY_OPERATING".equals(domainCode) || !selection.optionId().startsWith("company:")
                            || !intent.path("queryArguments").isObject()) throw clarificationFailure();
                    String id = selection.optionId().substring("company:".length());
                    var confirmed = (com.fasterxml.jackson.databind.node.ObjectNode) intent.path("queryArguments").deepCopy();
                    confirmed.put("companyId", id); confirmed.remove("companyName");
                    projectClarificationArguments = confirmed;
                    effectiveQuestion += "\n[用户确认的真实公司ID=" + id + "，保留原项目、指标、日期和分页条件，重新查询]";
                }
                if ("PROJECT_REFERENCE".equals(intent.path("ambiguityType").asText())) {
                    if (!isProjectScopedQueryDomain(domainCode)
                            || !selection.optionId().startsWith("project:")
                            || !intent.path("queryArguments").isObject()) throw clarificationFailure();
                    String id = selection.optionId().substring("project:".length());
                    if (!context.projectIds().contains(id)) throw new AgentException("AGENT_TOOL_PROJECT_FORBIDDEN",
                            org.springframework.http.HttpStatus.FORBIDDEN, "Selected project is no longer permitted");
                    var confirmed = (com.fasterxml.jackson.databind.node.ObjectNode) intent.path("queryArguments").deepCopy();
                    confirmed.put("projectId", id);
                    confirmed.remove(java.util.List.of("projectCode", "projectName", "projectNameMatch"));
                    projectClarificationArguments = confirmed;
                    effectiveQuestion += "\n[用户确认的真实项目ID=" + id + "，按已确认项目和原查询条件查询"
                            + queryIntentResolver.domain(domainCode).orElseThrow().name() + "]";
                }
                if ("COMPARISON_PROJECT_REFERENCE".equals(intent.path("ambiguityType").asText())) {
                    if (!java.util.Set.of("PROJECT_RECEIPTS", "CONSTRUCTION_CONTRACT", "PROJECT_OUTPUT", "PAYMENT_REQUEST", "OTHER_PAYMENT_REQUEST", "PROJECT_REIMBURSE", "MATERIAL_REFUND").contains(domainCode) || !intent.path("queryArguments").isObject())
                        throw clarificationFailure();
                    String[] parts = selection.optionId().split(":", 3);
                    if (parts.length != 3 || !java.util.Set.of("current", "baseline").contains(parts[0])
                            || !"project".equals(parts[1])) throw clarificationFailure();
                    if (!context.projectIds().contains(parts[2])) throw new AgentException("AGENT_TOOL_PROJECT_FORBIDDEN",
                            org.springframework.http.HttpStatus.FORBIDDEN, "Selected project is no longer permitted");
                    var confirmed = (com.fasterxml.jackson.databind.node.ObjectNode) intent.path("queryArguments").deepCopy();
                    if (!confirmed.path(parts[0]).isObject()) throw clarificationFailure();
                    var group = (com.fasterxml.jackson.databind.node.ObjectNode) confirmed.path(parts[0]);
                    group.put("projectId", parts[2]); group.remove(java.util.List.of("projectCode", "projectName", "projectNameMatch"));
                    comparisonClarificationArguments = confirmed;
                    effectiveQuestion += "\n[用户已确认比较条件=" + confirmed + "，重新调用比较工具，不得丢失另一组条件]";
                }
                IntentResolution resolution = queryIntentResolver.resolveSelectedDomain(
                        domainCode, originalQuestion, context);
                persistDebug("CLARIFICATION_RESOLVED", "routing", objectMapper.writeValueAsString(Map.of(
                        "clarificationId", pending.id(), "optionId", selection.optionId(),
                        "domainCode", domainCode)));
                return resolution;
            } catch (com.smart.agent.clarification.PendingClarificationException exception) {
                throw clarificationFailure();
            } catch (AgentException exception) {
                throw exception;
            } catch (Exception exception) {
                throw clarificationFailure();
            }
        }

        private AgentException clarificationFailure() {
            return new AgentException("AGENT_CLARIFICATION_INVALID",
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Clarification selection is invalid");
        }

        private boolean completeClarification(IntentResolution resolution) {
            if (resolution == null || resolution.status() != IntentResolution.Status.NEEDS_CLARIFICATION
                    || pendingClarificationService == null) return false;
            ClarificationPrompt prompt = ClarificationPrompt.from(resolution);
            return completeClarificationPrompt(prompt);
        }

        private boolean isProjectScopedQueryDomain(String code) {
            if ("COMPANY_OPERATING".equals(code) || "PROJECT_PROGRESS".equals(code)) return true;
            if ("COST_CONTRACT".equals(code)) return true;
            if ("INVOICE_APPLICATION".equals(code)) return true;
            if ("PROJECT_FUNDING".equals(code) || "BUDGET_COST".equals(code) || "PROJECT_PROFIT".equals(code)) return true;
            return "MATERIAL_REFUND".equals(code) || "PROJECT_REIMBURSE".equals(code) || "OTHER_PAYMENT_REQUEST".equals(code) || "PAYMENT_REQUEST".equals(code) || "CONSTRUCTION_CONTRACT".equals(code) || "PROJECT_OUTPUT".equals(code) || "PAYMENT_REGISTRATION".equals(code)
                    || "OTHER_INCOME_REGISTRATION".equals(code) || "PROJECT_RECEIPTS".equals(code);
        }

        private boolean isProjectScopedQueryTool(String key) {
            if ("companyOperating.query".equals(key) || "projectProgress.query".equals(key)) return true;
            if ("costContract.query".equals(key)) return true;
            if ("invoiceApplication.query".equals(key)) return true;
            if ("projectFunding.query".equals(key) || "budgetCost.query".equals(key) || "projectProfit.query".equals(key)) return true;
            return "materialRefund.query".equals(key) || "projectReimburse.query".equals(key) || "otherPaymentRequest.query".equals(key) || "paymentRequest.query".equals(key) || "constructionContract.query".equals(key) || "projectOutput.query".equals(key) || "paymentRegistration.query".equals(key)
                    || "otherIncomeRegistration.query".equals(key) || "projectReceipts.query".equals(key);
        }

        private boolean completeOutputTypeClarification() {
            if (queryIntentResolver == null || queryIntentResolver.domain("PROJECT_OUTPUT").isEmpty()
                    || isExplicitApprovalWorkflowQuestion(effectiveQuestion)) return false;
            String answer;
            if (com.smart.agent.routing.ProjectOutputDomainContributor.isOtherOutput(effectiveQuestion))
                answer = "劳务产值和专包产值查询暂未接入，目前支持工程产值查询。";
            else if (com.smart.agent.routing.ProjectOutputDomainContributor.isAmbiguousOutput(effectiveQuestion)
                    && !"PROJECT_OUTPUT".equals(activeBusinessDomain))
                answer = "您要查询工程产值、劳务产值还是专包产值？目前支持工程产值查询。";
            else return false;
            moveTo(AgentRunStatus.GENERATING);
            turnCompleted = new ModelEvent.Completed(answer, 0, 0);
            completeAnswer();
            return true;
        }

        private boolean completeClarificationPrompt(ClarificationPrompt prompt) {
            if (pendingClarificationService == null) throw clarificationFailure();
            try {
                Map<String, Object> storedIntent = new java.util.LinkedHashMap<>();
                storedIntent.put("originalQuestion", effectiveQuestion);
                storedIntent.put("activeBusinessDomain", activeBusinessDomain);
                storedIntent.put("ambiguityType", prompt.ambiguityType());
                storedIntent.put("options", prompt.options());
                if (("PROJECT_REFERENCE".equals(prompt.ambiguityType()) || "COMPANY_REFERENCE".equals(prompt.ambiguityType())) && projectClarificationArguments != null)
                    storedIntent.put("queryArguments", projectClarificationArguments);
                if ("COMPARISON_PROJECT_REFERENCE".equals(prompt.ambiguityType()) && comparisonClarificationArguments != null)
                    storedIntent.put("queryArguments", comparisonClarificationArguments);
                String intentJson = objectMapper.writeValueAsString(storedIntent);
                String optionsJson = objectMapper.writeValueAsString(prompt.options());
                PendingClarification clarification = withinBudget(() -> pendingClarificationService.create(
                        context.tenantId(), context.userId(), command.conversationId(),
                        additionalQueryDomain == null ? activeBusinessDomain : additionalQueryDomain.code(),
                        intentJson, optionsJson, Duration.ofMinutes(10)));
                moveTo(AgentRunStatus.GENERATING);
                emit(ChatEvent.status(run.id(), traceId, status));
                String answer = prompt.displayText();
                emit(ChatEvent.delta(run.id(), traceId, answer));
                Map<String, Object> data = new java.util.LinkedHashMap<>();
                data.put("clarificationId", clarification.id());
                data.put("question", prompt.question());
                data.put("ambiguityType", prompt.ambiguityType());
                if (clarification.sourceDomain() != null) {
                    data.put("sourceDomain", clarification.sourceDomain());
                }
                data.put("options", prompt.options());
                data.put("expiresAt", clarification.expiresAt().toString());
                emit(ChatEvent.clarification(run.id(), traceId, data));
                String clarificationSummary = objectMapper.writeValueAsString(data);
                synchronized (terminalLock) {
                    if (terminated.get() || sink.isCancelled()) return true;
                    AgentRunService.Completion completion = withinBudget(() -> runService.completeWithClarification(
                            context.tenantId(), context.userId(), command.conversationId(), run.id(), status,
                            answer, clarificationSummary));
                    run = completion.run();
                    status = AgentRunStatus.COMPLETED;
                    terminated.set(true);
                    sink.next(ChatEvent.messageEnd(run.id(), traceId, completion.message().id()));
                    sink.complete();
                }
                return true;
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to persist clarification", exception);
            } finally {
                if (terminated.get()) releaseModelHandle();
            }
        }

        private String intentResolutionJson(IntentResolution resolution) {
            try {
                return objectMapper.writeValueAsString(resolution);
            } catch (Exception exception) {
                return "{\"status\":\"SERIALIZATION_FAILED\"}";
            }
        }

        /**
         * 会话未绑定模型时回落到系统默认模型，让历史会话也能继续使用。
         */
        private String resolveModelIdForRun() {
            if (modelRegistry == null) {
                return null;
            }
            return withinBudget(() -> conversationService.find(
                            context.tenantId(), context.userId(), command.conversationId()))
                    .modelId();
        }

        /**
         * 取得本轮执行的模型句柄。配置中心不可用或未配置默认模型时直接失败，
         * 不回退到无模型或未知模型。
         */
        private ModelExecutionHandle acquireModelHandle(String modelId) {
            if (modelRegistry == null) {
                return null;
            }
            return modelId == null || modelId.isBlank()
                    ? withinBudget(modelRegistry::acquireDefault)
                    : withinBudget(() -> modelRegistry.acquire(modelId));
        }

        private void bindRunToModel(ModelExecutionHandle handle, String modelId) {
            if (modelRegistry == null) {
                modelGatewayForRun = modelGateway;
                run = withinBudget(() -> runService.start(
                        context.tenantId(), context.userId(), command.conversationId(), traceId));
                return;
            }
            synchronized (terminalLock) {
                if (terminated.get() || modelHandleReleased.get()) {
                    handle.close();
                    throw new IllegalStateException("对话已取消");
                }
                modelHandle = handle;
                modelGatewayForRun = handle.gateway();
            }
            ModelExecutionHandle.Snapshot snapshot = handle.snapshot();
            modelBindingForRun = modelRegistry.bindingFor(handle);
            run = withinBudget(() -> runService.start(context.tenantId(), context.userId(),
                    command.conversationId(), traceId, snapshot.modelId(), snapshot.displayName(),
                    snapshot.modelName(), snapshot.configVersion()));
        }

        /**
         * 释放本轮模型句柄。完成、失败、取消、超时四条路径都必须调用，重复调用安全。
         */
        private void releaseModelHandle() {
            ModelExecutionHandle handle;
            synchronized (terminalLock) {
                if (!modelHandleReleased.compareAndSet(false, true)) {
                    return;
                }
                handle = modelHandle;
                modelHandle = null;
            }
            if (handle == null) {
                return;
            }
            try {
                handle.close();
            } catch (RuntimeException ignored) {
                // 释放失败不应影响已完成的响应。
            }
        }

        /**
         * 本轮运行绑定的模型凭据，取自执行句柄，与工具绑定同版本。
         *
         * <p>只在工具执行瞬间读取并交给运行期私有上下文，禁止写入日志、调试事件或接口响应。
         *
         * @return 本轮凭据；未接入模型配置中心或句柄已释放时为 {@code null}
         */
        private ModelCredentialSource.Credentials modelCredentialsForRun() {
            synchronized (terminalLock) {
                return modelHandle == null ? null : modelHandle.credentials();
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

        private String companyMetricForQuestion() {
            if (projectClarificationArguments != null && projectClarificationArguments.hasNonNull("metric"))
                return projectClarificationArguments.path("metric").asText();
            if (com.smart.agent.routing.CompanyOperatingDomainContributor.hasExplicitMetric(effectiveQuestion)
                    || !"COMPANY_OPERATING".equals(activeBusinessDomain) || conversationContextService == null)
                return com.smart.agent.routing.CompanyOperatingDomainContributor.metric(effectiveQuestion);
            return withinBudget(() -> conversationContextService.find(context.tenantId(), context.userId(),
                    command.conversationId(), "QUERY_COMPANY_OPERATING")).map(snapshot -> {
                try {
                    String metric = objectMapper.readTree(snapshot.payloadJson()).path("arguments").path("metric").asText("all");
                    com.smart.agent.routing.CompanyOperatingDomainContributor.permissions(metric); return metric;
                } catch (java.io.IOException exception) {throw new IllegalStateException("Invalid company query context",exception);}
            }).orElse("all");
        }

        private String profitMetricForQuestion() {
            if (com.smart.agent.routing.ProjectProfitDomainContributor.hasExplicitMetric(effectiveQuestion)
                    || !"PROJECT_PROFIT".equals(activeBusinessDomain) || conversationContextService == null)
                return com.smart.agent.routing.ProjectProfitDomainContributor.metric(effectiveQuestion);
            return withinBudget(() -> conversationContextService.find(context.tenantId(), context.userId(),
                    command.conversationId(), "QUERY_PROJECT_PROFIT")).map(snapshot -> {
                try {
                    String metric = objectMapper.readTree(snapshot.payloadJson()).path("arguments").path("metric").asText("both");
                    com.smart.agent.routing.ProjectProfitDomainContributor.permissions(metric);
                    return metric;
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException("Invalid profit query context", exception);
                }
            }).orElse("both");
        }

        private void validateProjectMenuPermission(List<ModelRequest.ConversationEntry> history) {
            if (queryIntentResolver != null && queryIntentResolver.domain("COMPANY_OPERATING").isPresent()
                    && (com.smart.agent.routing.CompanyOperatingDomainContributor.matches(effectiveQuestion)
                            || additionalQueryDomain != null && "COMPANY_OPERATING".equals(additionalQueryDomain.code()))
                    && !isExplicitApprovalWorkflowQuestion(effectiveQuestion)
                    && !context.permissions().containsAll(com.smart.agent.routing.CompanyOperatingDomainContributor.permissions(
                            companyMetricForQuestion()))) {
                permissionQuerySubject = "所查询的自持公司经营指标";
                throw new AgentException("AGENT_TOOL_FORBIDDEN",org.springframework.http.HttpStatus.FORBIDDEN,"Required company operating source menus missing");
            }
            if (additionalQueryDomain != null && "COMPANY_OPERATING".equals(additionalQueryDomain.code())) return;
            if (queryIntentResolver != null && queryIntentResolver.domain("COST_CONTRACT").isPresent()
                    && com.smart.agent.routing.CostContractDomainContributor.matches(effectiveQuestion)
                    && !isExplicitApprovalWorkflowQuestion(effectiveQuestion)
                    && !context.permissions().containsAll(com.smart.agent.routing.CostContractDomainContributor.permissions(
                            com.smart.agent.routing.CostContractDomainContributor.source(effectiveQuestion)))) {
                permissionQuerySubject = "所查询成本合同类别（完整占比需要五类合同）";
                throw new AgentException("AGENT_TOOL_FORBIDDEN",org.springframework.http.HttpStatus.FORBIDDEN,"Required cost contract source menus missing");
            }
            if (additionalQueryDomain != null && "COST_CONTRACT".equals(additionalQueryDomain.code())) return;
            if (queryIntentResolver != null && queryIntentResolver.domain("PROJECT_PROFIT").isPresent()
                    && (com.smart.agent.routing.ProjectProfitDomainContributor.matches(effectiveQuestion)
                            || additionalQueryDomain != null && "PROJECT_PROFIT".equals(additionalQueryDomain.code()))
                    && !isExplicitApprovalWorkflowQuestion(effectiveQuestion)
                    && !context.permissions().containsAll(com.smart.agent.routing.ProjectProfitDomainContributor.permissions(
                            profitMetricForQuestion()))) {
                permissionQuerySubject = "项目利润统计所需的全部来源";
                throw new AgentException("AGENT_TOOL_FORBIDDEN", org.springframework.http.HttpStatus.FORBIDDEN, "Required profit source menus missing");
            }
            if (additionalQueryDomain != null && "PROJECT_PROFIT".equals(additionalQueryDomain.code())) return;
            if (queryIntentResolver != null && queryIntentResolver.domain("INVOICE_APPLICATION").isPresent()
                    && com.smart.agent.routing.InvoiceApplicationDomainContributor.matches(effectiveQuestion)
                    && !isExplicitApprovalWorkflowQuestion(effectiveQuestion)
                    && !context.permissions().contains("menu:finance:invoice-application")) {
                permissionQuerySubject = "开票申请";
                throw new AgentException("AGENT_TOOL_FORBIDDEN",org.springframework.http.HttpStatus.FORBIDDEN,"Invoice application menu required");
            }
            if (additionalQueryDomain != null && "INVOICE_APPLICATION".equals(additionalQueryDomain.code())) return;
            if (queryIntentResolver != null && queryIntentResolver.domain("BUDGET_COST").isPresent()
                    && com.smart.agent.routing.BudgetCostDomainContributor.matches(effectiveQuestion)
                    && !isExplicitApprovalWorkflowQuestion(effectiveQuestion)
                    && !context.permissions().contains("menu:budget:cost")) {
                permissionQuerySubject = "预算成本";
                throw new AgentException("AGENT_TOOL_FORBIDDEN", org.springframework.http.HttpStatus.FORBIDDEN, "Budget archive menu required");
            }
            if (additionalQueryDomain != null && "BUDGET_COST".equals(additionalQueryDomain.code())) return;
            if (queryIntentResolver != null && queryIntentResolver.domain("PROJECT_FUNDING").isPresent()
                    && com.smart.agent.routing.ProjectFundingDomainContributor.matches(effectiveQuestion)
                    && !isExplicitApprovalWorkflowQuestion(effectiveQuestion)
                    && !context.permissions().containsAll(com.smart.agent.routing.ProjectFundingDomainContributor.permissions(
                            com.smart.agent.routing.ProjectFundingDomainContributor.metric(effectiveQuestion)))) {
                permissionQuerySubject = "总付款与项目垫资统计所需的全部来源";
                throw new AgentException("AGENT_TOOL_FORBIDDEN", org.springframework.http.HttpStatus.FORBIDDEN,
                        "Required funding source menus missing");
            }
            if (additionalQueryDomain != null && "PROJECT_FUNDING".equals(additionalQueryDomain.code())) return;
            if (queryIntentResolver != null && queryIntentResolver.domain("MATERIAL_REFUND").isPresent()
                    && com.smart.agent.routing.MaterialRefundDomainContributor.matches(effectiveQuestion)
                    && !isExplicitApprovalWorkflowQuestion(effectiveQuestion)
                    && !context.permissions().contains("menu:material:refund")) {
                permissionQuerySubject = "材料退货退款";
                throw new AgentException("AGENT_TOOL_FORBIDDEN", org.springframework.http.HttpStatus.FORBIDDEN,
                        "Material return menu permission is required");
            }
            if (additionalQueryDomain != null && "MATERIAL_REFUND".equals(additionalQueryDomain.code())) return;
            if (queryIntentResolver != null && queryIntentResolver.domain("PROJECT_REIMBURSE").isPresent()
                    && com.smart.agent.routing.ProjectReimburseDomainContributor.matches(effectiveQuestion)
                    && !isExplicitApprovalWorkflowQuestion(effectiveQuestion)
                    && !context.permissions().contains("menu:finance:project-reimburse")) {
                permissionQuerySubject = "项目报销";
                throw new AgentException("AGENT_TOOL_FORBIDDEN", org.springframework.http.HttpStatus.FORBIDDEN,
                        "Payment request menu permission is required");
            }
            if (additionalQueryDomain != null && "PROJECT_REIMBURSE".equals(additionalQueryDomain.code())) return;
            if (queryIntentResolver != null && queryIntentResolver.domain("OTHER_PAYMENT_REQUEST").isPresent()
                    && com.smart.agent.routing.OtherPaymentRequestDomainContributor.matches(effectiveQuestion)
                    && !isExplicitApprovalWorkflowQuestion(effectiveQuestion)
                    && !context.permissions().contains("menu:finance:other-payment-request")) {
                permissionQuerySubject = "其他付款申请";
                throw new AgentException("AGENT_TOOL_FORBIDDEN", org.springframework.http.HttpStatus.FORBIDDEN,
                        "Payment request menu permission is required");
            }
            if (additionalQueryDomain != null && "OTHER_PAYMENT_REQUEST".equals(additionalQueryDomain.code())) return;
            if (queryIntentResolver != null && queryIntentResolver.domain("PAYMENT_REQUEST").isPresent()
                    && com.smart.agent.routing.PaymentRequestDomainContributor.matches(effectiveQuestion)
                    && !isExplicitApprovalWorkflowQuestion(effectiveQuestion)
                    && !context.permissions().contains("menu:finance:payment-request")) {
                permissionQuerySubject = "付款申请";
                throw new AgentException("AGENT_TOOL_FORBIDDEN", org.springframework.http.HttpStatus.FORBIDDEN,
                        "Payment request menu permission is required");
            }
            if (additionalQueryDomain != null && "PAYMENT_REQUEST".equals(additionalQueryDomain.code())) return;
            if (queryIntentResolver != null && queryIntentResolver.domain("PROJECT_RECEIPTS").isPresent()
                    && com.smart.agent.routing.ProjectReceiptsDomainContributor.matches(effectiveQuestion)
                    && !isExplicitApprovalWorkflowQuestion(effectiveQuestion)
                    && !com.smart.agent.routing.ProjectReceiptsDomainContributor.canAccess(context.permissions())) {
                permissionQuerySubject = "完整已收项目款（需同时具备回款登记和其他收入登记权限）";
                throw new AgentException("AGENT_TOOL_FORBIDDEN", org.springframework.http.HttpStatus.FORBIDDEN,
                        "Both financial menu permissions are required");
            }
            if (additionalQueryDomain != null && "PROJECT_RECEIPTS".equals(additionalQueryDomain.code())) return;
            if (queryIntentResolver != null && queryIntentResolver.domain("OTHER_INCOME_REGISTRATION").isPresent()
                    && com.smart.agent.routing.OtherIncomeRegistrationDomainContributor.matches(effectiveQuestion)
                    && !isExplicitApprovalWorkflowQuestion(effectiveQuestion)
                    && !context.permissions().contains("menu:finance:other-income-registration")) {
                permissionQuerySubject = "其他收入登记";
                throw new AgentException("AGENT_TOOL_FORBIDDEN", org.springframework.http.HttpStatus.FORBIDDEN,
                        "Other income registration menu permission is required");
            }
            if (additionalQueryDomain != null && "OTHER_INCOME_REGISTRATION".equals(additionalQueryDomain.code())) return;
            if (queryIntentResolver != null && queryIntentResolver.domain("PAYMENT_REGISTRATION").isPresent()
                    && com.smart.agent.routing.PaymentRegistrationDomainContributor.matches(effectiveQuestion)
                    && !isExplicitApprovalWorkflowQuestion(effectiveQuestion)
                    && !context.permissions().contains("menu:finance:payment-registration")) {
                permissionQuerySubject = "回款登记";
                throw new AgentException("AGENT_TOOL_FORBIDDEN", org.springframework.http.HttpStatus.FORBIDDEN,
                        "Payment registration menu permission is required");
            }
            if (additionalQueryDomain != null && "PAYMENT_REGISTRATION".equals(additionalQueryDomain.code())) return;
            if (queryIntentResolver != null && queryIntentResolver.domain("PROJECT_OUTPUT").isPresent()
                    && com.smart.agent.routing.ProjectOutputDomainContributor.matches(effectiveQuestion)
                    && !isExplicitApprovalWorkflowQuestion(effectiveQuestion)
                    && !context.permissions().contains("menu:project")) {
                permissionQuerySubject = queryIntentResolver.domain("PROJECT_OUTPUT").orElseThrow().name();
                throw new AgentException("AGENT_PROJECT_MENU_FORBIDDEN",
                        org.springframework.http.HttpStatus.FORBIDDEN, "Project menu permission is required");
            }
            if (queryIntentResolver != null && queryIntentResolver.domain("CONSTRUCTION_CONTRACT").isPresent()
                    && com.smart.agent.routing.ConstructionContractDomainContributor.matches(effectiveQuestion)
                    && !com.smart.agent.routing.ConstructionContractDomainContributor.isWorkflowQuery(effectiveQuestion)
                    && !context.permissions().contains("menu:project")) {
                permissionQuerySubject = queryIntentResolver.domain("CONSTRUCTION_CONTRACT").orElseThrow().name();
                throw new AgentException("AGENT_PROJECT_MENU_FORBIDDEN",
                        org.springframework.http.HttpStatus.FORBIDDEN, "Project menu permission is required");
            }
            // 看板查询不依赖项目报备/项目档案菜单，必须在项目权限校验前直接放行。
            if (requiresFreshBoardData) return;
            if (context.permissions().contains("menu:project")) return;
            if ("PROJECT".equals(activeBusinessDomain)
                    || (!isApprovalQuestion(effectiveQuestion)
                    && (isProjectQuestion(effectiveQuestion) || isProjectFollowUp(effectiveQuestion, history)))) {
                permissionQuerySubject = "项目";
                throw new AgentException("AGENT_PROJECT_MENU_FORBIDDEN",
                        org.springframework.http.HttpStatus.FORBIDDEN, "Project menu permission is required");
            }
        }

        private boolean isProjectFollowUp(
                String question, List<ModelRequest.ConversationEntry> history) {
            String value = question == null ? "" : question.trim();
            if (!value.matches(".*(再查|继续|下一页|上一页|更多|详细|再看|重新查).*")) return false;
            for (int index = history.size() - 1; index >= 0; index--) {
                if (!(history.get(index) instanceof ModelRequest.ConversationMessage message)
                        || !"user".equals(message.role())) continue;
                if (isApprovalQuestion(message.content())) return false;
                if (isBoardQuestion(message.content())) return false;
                if (isProjectQuestion(message.content())) return true;
            }
            return false;
        }

        /**
         * 追问不应只按关键词路由。若上一轮明确建立了项目查询上下文，
         * 当前轮只提供字段、状态或统计口径时，继续沿用项目工具；审批流
         * 只有在当前轮明确指向待办、已办、流程、办理人等审批对象时才接管。
         */
        private boolean isProjectContextFollowUp(String question,
                                                  List<ModelRequest.ConversationEntry> history,
                                                  String domain) {
            if (question == null || question.isBlank() || history == null) return false;
            if (isBoardQuestion(question) || isProjectQuestion(question)) return false;
            String value = question.trim();
            if (!value.matches(".*(多少|数量|统计|筛选|状态|审批完成|审批通过|审批中|驳回|草稿|项目|"
                    + "第.{0,6}条|第一条|第二条|详情|档案|这个|刚才).*")) return false;
            // “审批状态”既可以是项目字段，也可以是流程语义。已有成功工具形成的
            // 业务域快照优先于聊天文本，且失败工具不会覆盖它。
            if ("PROJECT".equals(domain)) return true;
            if (domain != null) return false;
            for (int index = history.size() - 1; index >= 0; index--) {
                ModelRequest.ConversationEntry entry = history.get(index);
                if (!(entry instanceof ModelRequest.ConversationMessage message)
                        || !"user".equals(message.role())) continue;
                if (isProjectQuestion(message.content())) return true;
                // 单独的“审批完成/审批状态”可能只是项目字段追问，不能作为
                // 审批流业务域锚点。只有明确的流程对象才切换到审批域。
                if (isExplicitApprovalWorkflowQuestion(message.content())
                        || isBoardQuestion(message.content())) return false;
            }
            return false;
        }

        private boolean isExplicitApprovalWorkflowQuestion(String text) {
            return com.smart.agent.routing.ConstructionContractDomainContributor.isWorkflowQuery(text);
        }

        private String loadActiveBusinessDomain() {
            if (conversationContextService == null) return null;
            return withinBudget(() -> conversationContextService.find(
                            context.tenantId(), context.userId(), command.conversationId(),
                            ConversationContextService.ACTIVE_BUSINESS_DOMAIN))
                    .map(com.smart.agent.context.ConversationContext::payloadJson)
                    .map(value -> {
                        try { return objectMapper.readTree(value).path("domain").asText(null); }
                        catch (Exception ignored) { return null; }
                    }).orElse(null);
        }

        private boolean isProjectQuestion(String question) {
            if (question == null || question.isBlank()) return false;
            return question.matches(".*(项目|项目报备|项目档案).*");
        }

        private boolean isRelativeTimeQuestion(String question) {
            if (question == null || question.isBlank()) return false;
            return question.matches("(?s).*(今天|昨日|昨天|明天|本周|上周|下周|本月|上月|下月|本季度|上季度|下季度|本季|上季|下季|本年|上年|今年|去年|明年|截至目前|最近[0-9一二三四五六七八九十]+[天日周月年]|[0-9一二三四五六七八九十]+[天日](没|未)(处理|办理|审批)).*");
        }

        private boolean isBoardQuestion(String question) {
            return containsBoardKeyword(question);
        }

        private boolean containsBoardKeyword(String question) {
            if (question == null || question.isBlank()) return false;
            return question.contains("经营看板") || question.contains("预算看板")
                    || question.contains("应收看板") || question.contains("供应商看板")
                    || question.contains("投标看板") || question.contains("库存看板")
                    || question.contains("项目看板") || question.contains("甘特图")
                    || question.contains("看板数据") || question.contains("看板分析")
                    || question.contains("看板指标");
        }

        private String modelQuestion() {
            ChatCommand.PageContext page = command.pageContext();
            StringBuilder safe = new StringBuilder(effectiveQuestion);
            if (additionalQueryDomain != null) {
                safe.append("\n[trusted-routing: 本轮是").append(additionalQueryDomain.name())
                        .append("查询，只能调用 ").append(String.join("、", additionalQueryDomain.toolKeys()))
                        .append("；相对日期先调用 system.current_time。每轮事实查询必须重新取数，历史结果仅用于理解条件。"
                                + "日期维度不明确时调用该业务域的确认工具追问，不得自行假定。]");
                if (java.util.Set.of("COMPANY_OPERATING", "PROJECT_FUNDING", "BUDGET_COST", "PROJECT_PROFIT")
                        .contains(additionalQueryDomain.code())) {
                    safe.append("\n[trusted-cumulative-date: 这些指标仅支持按项目创建期间选择项目后的当前累计值。"
                            + "用户说某年、某月、期间发生、截至历史日期、历史余额或同比环比时，不能默认为项目创建日期，"
                            + "也不能将当前累计值称为历史余额或期间金额。日期含义不明确或请求历史快照、期间金额、比较时，"
                            + "先调用本域clarifyDate说明能力限制，让用户确认是否改查所选项目的当前累计值。"
                            + "用户明确说这段时间创建的项目时才使用projectCreateDate；保留项目、公司和指标等其他条件。"
                            + "回复用业务语言说明是这些项目当前的累计数据，不输出技术字段名。]");
                }
            } else if (requiresFreshBoardData) {
                safe.append("\n[trusted-routing: 本轮是看板查询，只能调用 board.query、board.compare 或 board.detail；不得调用 project.query，也不要返回项目菜单权限提示]");
            } else if (requiresFreshApprovalData) {
                safe.append("\n[trusted-routing: 本轮是审批查询；相对时间先调用 system.current_time，再调用 approval.query 或 approval.getDetail；不得调用 project.* 或 board.*。待办超过 N 天未处理是任务创建时间 taskCreateTime 早于当前时间减 N 天，不是到期时间、发起时间或所属部门。]");
            } else if (requiresFreshProjectData) {
                safe.append("\n[trusted-routing: 本轮是项目查询，只能调用 project.query 或 project.getArchiveDetail；不得调用 approval.* 或 board.*。历史中的审批内容不改变本轮路由]");
            }
            if (page == null) return safe.toString();
            safe.append("\n[trusted-page-context");
            appendContext(safe, "pageCode", page.pageCode());
            appendContext(safe, "projectId", page.projectId());
            appendContext(safe, "businessType", page.businessType());
            appendContext(safe, "businessId", page.businessId());
            return safe.append(']').toString();
        }

        private String additionalQueryContextType() {
            return "QUERY_" + additionalQueryDomain.code();
        }

        private void appendAdditionalQueryContext() {
            if (additionalQueryDomain == null || conversationContextService == null) return;
            if (isProjectScopedQueryDomain(additionalQueryDomain.code()) && "PROJECT".equals(activeBusinessDomain)) {
                var selected = withinBudget(() -> conversationContextService.find(context.tenantId(), context.userId(),
                        command.conversationId(), ConversationContextService.CURRENT_PROJECT));
                if (selected.isPresent()) {
                    try {
                        JsonNode reference = objectMapper.readTree(selected.get().payloadJson());
                        if (context.projectIds().contains(reference.path("projectId").asText()))
                            messages.add(new ModelRequest.ConversationMessage("user",
                                    "[trusted-project-reference: 最近项目工具确认的项目标识=" + reference
                                            + "。仅在用户追问该项目" + additionalQueryDomain.name() + "时使用真实projectId；projectCode是编号，不是ID。"
                                            + "用户明确查询其他项目或全部项目时，以新问题为准。必须重新调用业务工具获取实时数据。]"));
                    } catch (java.io.IOException exception) {
                        throw new IllegalStateException("Invalid project reference context", exception);
                    }
                }
                // Do not inherit filters from an older, unrelated contract query after viewing a project.
                return;
            }
            if (!additionalQueryDomain.code().equals(activeBusinessDomain)) return;
            withinBudget(() -> conversationContextService.find(context.tenantId(), context.userId(),
                    command.conversationId(), additionalQueryContextType())).ifPresent(snapshot -> {
                try {
                    JsonNode savedQuery = objectMapper.readTree(snapshot.payloadJson());
                    JsonNode arguments = savedQuery.path("arguments");
                    messages.add(new ModelRequest.ConversationMessage("user",
                            "[trusted-query-context: 上一次" + additionalQueryDomain.name() + "查询条件=" + arguments
                                    + "。仅用于追问时继承用户未更改的筛选条件和分页；当前提问明确的新条件优先。"
                                    + "历史结果不可作为本轮数据，必须重新调用工具。]"));
                    if ("COMPANY_OPERATING".equals(additionalQueryDomain.code())) {
                        var references = objectMapper.createArrayNode();
                        int position = 0;
                        for (JsonNode row : savedQuery.path("result").path("rows")) {
                            position++;
                            if (position > 100) break;
                            boolean detail = "detail".equals(arguments.path("mode").asText());
                            if (detail && !context.projectIds().contains(row.path("projectId").asText())) continue;
                            var ref = references.addObject().put("pagePosition",position).put("companyId",row.path("companyId").asText())
                                    .put("companyName",row.path("companyName").asText()).put("companyCode",row.path("companyCode").asText());
                            if (detail) ref.put("projectId",row.path("projectId").asText()).put("projectName",row.path("projectName").asText()).put("projectCode",row.path("projectCode").asText());
                        }
                        messages.add(new ModelRequest.ConversationMessage("user","[trusted-company-operating-references: 上次公司经营列表第"
                                + savedQuery.path("result").path("page").asInt(1) + "页，查询方式=" + arguments.path("mode").asText("summary")
                                + "，定位信息=" + references + "。序号只用于定位，使用真实companyId或projectId重新查询；历史公司范围由接口按当前权限重新校验。指代不清先确认。]"));
                    }
                    if (("PROJECT_PROGRESS".equals(additionalQueryDomain.code()) || "PROJECT_FUNDING".equals(additionalQueryDomain.code()) || "BUDGET_COST".equals(additionalQueryDomain.code()) || "PROJECT_PROFIT".equals(additionalQueryDomain.code())) && "detail".equals(arguments.path("mode").asText("detail"))) {
                        var references = objectMapper.createArrayNode();
                        int position = 0;
                        for (JsonNode row : savedQuery.path("result").path("rows")) {
                            position++;
                            if (context.projectIds().contains(row.path("projectId").asText()))
                                references.addObject().put("pagePosition", position).put("projectId", row.path("projectId").asText())
                                        .put("projectName", row.path("projectName").asText()).put("projectCode", row.path("projectCode").asText());
                        }
                        messages.add(new ModelRequest.ConversationMessage("user", "[" + ("PROJECT_PROGRESS".equals(additionalQueryDomain.code()) ? "trusted-project-progress-references" : "PROJECT_PROFIT".equals(additionalQueryDomain.code()) ? "trusted-project-profit-references" : "BUDGET_COST".equals(additionalQueryDomain.code()) ? "trusted-budget-cost-references" : "trusted-project-funding-references") + ": 上次项目列表第"
                                + savedQuery.path("result").path("page").asInt(1) + "页项目定位信息=" + references
                                + "。仅用于序号指代，不能作为当前金额；必须按真实projectId重新查询。指代不明确先追问。]"));
                    }
                    if (java.util.Set.of("PAYMENT_REGISTRATION", "OTHER_INCOME_REGISTRATION").contains(additionalQueryDomain.code())
                            && "detail".equals(arguments.path("mode").asText("detail"))) {
                        var references = objectMapper.createArrayNode();
                        int position = 0;
                        for (JsonNode row : savedQuery.path("result").path("rows")) {
                            position++;
                            if (position > 100) break;
                            if (context.projectIds().contains(row.path("projectId").asText()) && !row.path("id").asText().isBlank())
                                references.addObject().put("pagePosition", position).put("registrationId", row.path("id").asText())
                                        .put("projectId", row.path("projectId").asText()).put("registrationCode", row.path("registrationCode").asText());
                        }
                        messages.add(new ModelRequest.ConversationMessage("user", "[trusted-receipt-registration-references: 上次"
                                + additionalQueryDomain.name() + "列表第" + savedQuery.path("result").path("page").asInt(1)
                                + "页定位信息=" + references + "。仅用于序号定位，不是当前事实；使用"
                                + ("PAYMENT_REGISTRATION".equals(additionalQueryDomain.code()) ? "paymentRegistration.query" : "otherIncomeRegistration.query")
                                + "、真实registrationId和detail重新查询单据自身详情。"
                                + "不同来源单据不可混用ID；指代不明确先追问，查询无结果不许移除ID扩大范围。]"));
                    }
                    if ("OTHER_INCOME_REGISTRATION".equals(additionalQueryDomain.code()) && "items".equals(arguments.path("mode").asText())) {
                        messages.add(new ModelRequest.ConversationMessage("user", "[trusted-other-income-items: 当前收入明细所属登记主单registrationId="
                                + arguments.path("registrationId").asText() + "。明细行itemId不是登记主单ID。"
                                + "继续查收入明细使用otherIncomeRegistration.query、items和同一registrationId，仅分页时调整页码并保留已确认筛选；"
                                + "返回这笔登记详情用detail和同一registrationId。每轮重新查询；指代不清先追问，不从一页明细计算整单合计。]"));
                    }
                    if ("CONSTRUCTION_CONTRACT".equals(additionalQueryDomain.code()) && "detail".equals(arguments.path("mode").asText("detail"))) {
                        var references = objectMapper.createArrayNode();
                        int position = 0;
                        for (JsonNode row : savedQuery.path("result").path("rows")) {
                            position++;
                            if (position > 100) break;
                            if (context.projectIds().contains(row.path("projectId").asText()) && !row.path("id").asText().isBlank())
                                references.addObject().put("pagePosition", position).put("contractId", row.path("id").asText())
                                        .put("projectId", row.path("projectId").asText()).put("contractName", row.path("contractName").asText())
                                        .put("contractNo", row.path("contractNo").asText());
                        }
                        messages.add(new ModelRequest.ConversationMessage("user", "[trusted-construction-contract-references: 上次施工合同列表第"
                                + savedQuery.path("result").path("page").asInt(1) + "页定位信息=" + references
                                + "。仅用于序号定位，不是当前事实；使用真实contractId和detail重新查询合同自身详情。指代不明确先追问。]"));
                    }
                    if ("CONSTRUCTION_CONTRACT".equals(additionalQueryDomain.code()) && "records".equals(arguments.path("mode").asText())) {
                        messages.add(new ModelRequest.ConversationMessage("user", "[trusted-construction-contract-records: 上次查询的主合同ID="
                                + arguments.path("contractId").asText() + "，记录类型=" + arguments.path("recordType").asText("all")
                                + "。记录中的recordId不是主合同ID；继续查记录沿用contractId并实时重查，仅分页时调整页码。"
                                + "返回主合同详情用detail和同一contractId。指定某条变更记录的进一步详情尚未接入，不得将recordId冒充主合同ID。]"));
                    }
                    if ("PROJECT_OUTPUT".equals(additionalQueryDomain.code()) && "detail".equals(arguments.path("mode").asText("detail"))) {
                        var references = objectMapper.createArrayNode();
                        int position = 0;
                        for (JsonNode row : savedQuery.path("result").path("rows")) {
                            position++;
                            if (position > 100) break;
                            if (context.projectIds().contains(row.path("projectId").asText()) && !row.path("id").asText().isBlank())
                                references.addObject().put("pagePosition", position).put("outputReportId", row.path("id").asText())
                                        .put("projectId", row.path("projectId").asText()).put("outputReportNo", row.path("outputReportNo").asText());
                        }
                        messages.add(new ModelRequest.ConversationMessage("user", "[trusted-project-output-references: 上次产值单列表第"
                                + savedQuery.path("result").path("page").asInt(1) + "页定位信息=" + references
                                + "。仅用于序号定位，必须按真实outputReportId重新查询；主信息用detail，工程量明细用items。指代不明确先追问。]"));
                    }
                    if ("COST_CONTRACT".equals(additionalQueryDomain.code()) && "detail".equals(arguments.path("mode").asText())) {
                        var references = objectMapper.createArrayNode();
                        int position = 0;
                        for (JsonNode row : savedQuery.path("result").path("rows")) {
                            position++;
                            String source = row.path("source").asText();
                            if (context.projectIds().contains(row.path("projectId").asText())
                                    && context.permissions().containsAll(com.smart.agent.routing.CostContractDomainContributor.permissions(source))) {
                                references.addObject().put("pagePosition",position).put("contractId",row.path("contractId").asText())
                                        .put("source",source).put("projectId",row.path("projectId").asText())
                                        .put("contractCode",row.path("contractCode").asText()).put("contractName",row.path("contractName").asText());
                            }
                        }
                        messages.add(new ModelRequest.ConversationMessage("user","[trusted-cost-contract-references: 上次合同列表第"
                                + savedQuery.path("result").path("page").asInt(1) + "页定位信息=" + references
                                + "。仅用于序号指代，不是当前事实；必须使用source与contractId重新查询。指代不明确先追问。]"));
                    }
                    if (("INVOICE_APPLICATION".equals(additionalQueryDomain.code()) || "MATERIAL_REFUND".equals(additionalQueryDomain.code()) || "PAYMENT_REQUEST".equals(additionalQueryDomain.code()) || "OTHER_PAYMENT_REQUEST".equals(additionalQueryDomain.code()) || "PROJECT_REIMBURSE".equals(additionalQueryDomain.code()))
                            && "detail".equals(arguments.path("mode").asText("detail"))) {
                        var references = objectMapper.createArrayNode();
                        JsonNode previousResult = savedQuery.path("result");
                        int index = 0;
                        for (JsonNode row : previousResult.path("rows")) {
                            index++;
                            if (index > 100) break;
                            var scopedProjects = objectMapper.createArrayNode();
                            if ("PROJECT_REIMBURSE".equals(additionalQueryDomain.code())) {
                                for (JsonNode id : row.path("projectIds"))
                                    if (context.projectIds().contains(id.asText())) scopedProjects.add(id.asText());
                            } else if (context.projectIds().contains(row.path("projectId").asText())) {
                                scopedProjects.add(row.path("projectId").asText());
                            }
                            if (scopedProjects.isEmpty() || row.path("requestId").asText().isBlank()) continue;
                            var reference = references.addObject().put("pagePosition", index)
                                    .put("requestId", row.path("requestId").asText())
                                    .put("registrationCode", row.path("registrationCode").asText());
                            if ("PROJECT_REIMBURSE".equals(additionalQueryDomain.code())) reference.set("projectIds", scopedProjects);
                            else reference.put("projectId", row.path("projectId").asText());
                        }
                        if (!references.isEmpty()) messages.add(new ModelRequest.ConversationMessage("user",
                                "[" + ("INVOICE_APPLICATION".equals(additionalQueryDomain.code()) ? "trusted-invoice-application-references" : "MATERIAL_REFUND".equals(additionalQueryDomain.code()) ? "trusted-material-refund-references" : "PROJECT_REIMBURSE".equals(additionalQueryDomain.code()) ? "trusted-project-reimburse-references" : "OTHER_PAYMENT_REQUEST".equals(additionalQueryDomain.code()) ? "trusted-other-payment-request-references" : "trusted-payment-request-references") + ": 上次" + additionalQueryDomain.name() + "列表第"
                                        + previousResult.path("page").asInt(1) + "页的单据定位信息=" + references
                                        + "。仅帮助理解第几单或单号，不能视为当前事实；必须按requestId重新查询明细。指代不明确先确认。]"));
                    }
                    if ("PAYMENT_REQUEST".equals(additionalQueryDomain.code()) && "items".equals(arguments.path("mode").asText())) {
                        messages.add(new ModelRequest.ConversationMessage("user", "[trusted-payment-request-items: 当前付款明细所属申请主单requestId="
                                + arguments.path("requestId").asText() + "。明细行itemId不是申请主单ID。"
                                + "继续查看或翻页使用paymentRequest.query、items，保留已确认的requestId及项目、日期、单号筛选；"
                                + "返回这笔申请单详情用detail和同一requestId。未限定主单时不能凭明细序号猜测主单，指代不清先追问。"
                                + "每轮实时查询，不从一页明细计算全部金额；改查全列表或全量合计应清除单笔requestId。]"));
                    }
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException("Invalid query context", exception);
                }
            });
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
                    || command.pageContext().projectId() == null || !requiresKnowledge(effectiveQuestion)) {
                return;
            }
            moveTo(AgentRunStatus.RETRIEVING);
            emit(ChatEvent.status(run.id(), traceId, status));
            citations = withinBudget(() -> knowledgeSearchService.search(new KnowledgeSearchQuery(effectiveQuestion,
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
            if (status != AgentRunStatus.GENERATING) moveTo(AgentRunStatus.GENERATING);
            emit(ChatEvent.status(run.id(), traceId, status));
            if (terminated.get() || sink.isCancelled()) {
                cancel();
                return;
            }
            modelTurns++;
            turnDeltas.setLength(0);
            turnTools.clear();
            turnCompleted = null;
            boolean requireProjectTool = requiresFreshProjectData
                    && (!requiresRelativeTimeForProject || timeToolCompleted);
            boolean requireApprovalTool = requiresFreshApprovalData && approvalToolCalls == 0
                    && (!requiresRelativeTimeForApproval || timeToolCompleted);
            boolean requireBoardTool = requiresFreshBoardData && toolCalls == 0;
            boolean requireAdditionalTool = additionalQueryDomain != null && additionalToolCalls == 0
                    && "REALTIME".equals(additionalQueryDomain.refreshPolicy());
            boolean requireTimeTool = (requiresRelativeTimeForProject || requiresRelativeTimeForApproval
                    || requiresRelativeTimeForAdditionalDomain)
                    && !timeToolCompleted;
            List<ModelRequest.AllowedToolSpecification> tools = toolRegistry.allowedReadOnlyTools(context).stream()
                    .filter(this::isRelevantTool)
                    .filter(tool -> {
                        if (additionalQueryDomain != null) return requireTimeTool
                                ? "system.current_time".equals(tool.key()) : additionalQueryDomain.ownsTool(tool.key());
                        if (requiresFreshApprovalData) return requireTimeTool
                                ? "system.current_time".equals(tool.key())
                                : tool.key().startsWith("approval.");
                        if (requiresFreshBoardData) return tool.key().startsWith("board.");
                        if (requiresFreshProjectData) {
                            return requireTimeTool
                                    ? "system.current_time".equals(tool.key())
                                    : tool.key().startsWith("project.");
                        }
                        return true;
                    })
                    .filter(tool -> requireTimeTool ? "system.current_time".equals(tool.key())
                            : !requireProjectTool || tool.key().startsWith("project."))
                    .filter(tool -> !requireApprovalTool || tool.key().startsWith("approval."))
                    .filter(tool -> !requireBoardTool || tool.key().startsWith("board."))
                    .map(this::toolSpecification)
                    .toList();
            if (!requireTimeTool && !requireProjectTool && !requireApprovalTool && !requireBoardTool && !requireAdditionalTool) {
                tools = new ArrayList<>(tools);
                if (tools.stream().noneMatch(tool -> "system.unsupported_capability".equals(tool.key())))
                    tools.add(toolSpecification(new com.smart.agent.question.UnsupportedCapabilityTool()));
            }
            List<ModelRequest.RetrievedEvidence> evidence = citations.stream()
                    .map(citation -> new ModelRequest.RetrievedEvidence(citation.citationToken(), citation.excerpt()))
                    .toList();
            ModelRequest request = new ModelRequest(run.id(), "v1", List.copyOf(messages), tools, evidence,
                    (requireProjectTool || requireApprovalTool || requireBoardTool || requireTimeTool || requireAdditionalTool)
                            ? ModelRequest.ToolUseMode.REQUIRED : ModelRequest.ToolUseMode.AUTO);
            Map<String, Object> modelRequestDebug = new java.util.LinkedHashMap<>();
            modelRequestDebug.put("turn", modelTurns);
            modelRequestDebug.put("stage", "MODEL_REQUEST");
            modelRequestDebug.put("toolUseMode", request.toolUseMode().name());
            modelRequestDebug.put("allowedTools", tools.stream().map(ModelRequest.AllowedToolSpecification::key).toList());
            modelRequestDebug.put("messageCount", messages.size());
            modelRequestDebug.put("evidenceCount", evidence.size());
            persistDebug("MODEL_REQUEST", null, modelRequestDebug);
            emit(ChatEvent.debug(run.id(), traceId, "MODEL_REQUEST", modelRequestDebug));
            preparationStage = "modelStream";
            try {
                Disposable subscription = modelGatewayForRun.stream(request)
                        .timeout(remaining())
                        .publishOn(Schedulers.boundedElastic())
                        .subscribe(this::handleModelEventSafely, this::handleModelError, this::finishModelTurnSafely);
                modelSubscription.update(subscription);
            } catch (RuntimeException exception) {
                Map<String, Object> modelErrorDebug = Map.of(
                        "turn", modelTurns, "stage", "MODEL_ERROR", "code", "AGENT_MODEL_FAILED");
                persistDebug("MODEL_ERROR", null, modelErrorDebug);
                emit(ChatEvent.debug(run.id(), traceId, "MODEL_ERROR", modelErrorDebug));
                fail("AGENT_MODEL_FAILED", AgentRunStatus.FAILED);
            }
        }

        private boolean isRelevantTool(AgentTool<?, ?> tool) {
            if (queryIntentResolver != null) {
                String domain = queryIntentResolver.domainCodeForTool(tool.key());
                if (domain != null && !java.util.Set.of("PROJECT", "APPROVAL", "BOARD").contains(domain))
                    return additionalQueryDomain != null && additionalQueryDomain.ownsTool(tool.key());
            }
            if (tool.key().startsWith("approval.")) return requiresFreshApprovalData;
            if (!tool.key().startsWith("project.")) return true;
            StringBuilder contextText = new StringBuilder(effectiveQuestion);
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

        private boolean isApprovalQuestion(String text) {
            String question = text == null ? "" : text.toLowerCase(java.util.Locale.ROOT);
            return question.contains("待办") || question.contains("已办") || question.contains("审批")
                    || question.contains("办理记录") || question.contains("我发起") || question.contains("流程进度")
                    || question.contains("谁在审批") || question.contains("审批流") || question.contains("超时审批");
        }

        private boolean isApprovalFollowUp(
                String question, List<ModelRequest.ConversationEntry> history) {
            String value = question == null ? "" : question.trim();
            if (isProjectQuestion(value) && !isApprovalQuestion(value)) return false;
            if (!value.matches(".*(继续|再查|下一页|上一页|更多|查看第.{0,6}条|第.{0,6}条|"
                    + "查看详情|详细信息|刚才那条|刚才那个流程|这个流程|重新查询).*")
                    && !isGenericApprovalDetailFollowUp(value)) {
                return false;
            }
            for (int index = history.size() - 1; index >= 0; index--) {
                if (!(history.get(index) instanceof ModelRequest.ConversationMessage message)
                        || !"user".equals(message.role())) continue;
                if (isApprovalQuestion(message.content())) return true;
                if (isProjectQuestion(message.content())) return false;
            }
            return false;
        }

        private boolean isGenericApprovalDetailFollowUp(String question) {
            return question != null && question.trim().matches(
                    "^(?:请)?(?:查看|看|打开)?(?:一下|下)?(?:这个|这条|该条)?"
                            + "(?:待办|已办|流程)?(?:的)?(?:详情|详细信息)[吗吧呢。？?]*$");
        }

        private boolean resolveApprovalPageFollowUp() {
            if (!requiresFreshApprovalData || !"APPROVAL".equals(activeBusinessDomain)
                    || effectiveQuestion == null) return false;
            String question = effectiveQuestion.trim();
            boolean next = question.matches("^(?:请)?(?:看|查|查询)?(?:一下|下)?(?:下一页|更多)(?:吧|。|？)?$");
            boolean previous = question.matches("^(?:请)?(?:看|查|查询)?(?:一下|下)?上一页(?:吧|。|？)?$");
            if (!next && !previous) return false;
            if (conversationContextService == null) {
                throw approvalSelectionFailure("AGENT_APPROVAL_CONTEXT_MISSING");
            }
            String payload = withinBudget(() -> conversationContextService.find(
                            context.tenantId(), context.userId(), command.conversationId(),
                            ConversationContextService.APPROVAL_QUERY))
                    .map(com.smart.agent.context.ConversationContext::payloadJson)
                    .orElseThrow(() -> approvalSelectionFailure("AGENT_APPROVAL_CONTEXT_MISSING"));
            try {
                JsonNode arguments = objectMapper.readTree(payload).path("arguments");
                if (!arguments.isObject()) {
                    throw approvalSelectionFailure("AGENT_APPROVAL_CONTEXT_INVALID");
                }
                com.fasterxml.jackson.databind.node.ObjectNode query =
                        (com.fasterxml.jackson.databind.node.ObjectNode) arguments.deepCopy();
                int page = Math.max(1, query.path("page").asInt(1));
                query.put("page", next ? Math.addExact(page, 1) : Math.max(1, page - 1));
                ModelEvent.ToolRequested request = new ModelEvent.ToolRequested(
                        "approval-page-" + UUID.randomUUID(), "approval.query",
                        objectMapper.writeValueAsString(query));
                Map<String, Object> toolRequestDebug = Map.of(
                        "turn", Math.max(1, modelTurns), "stage", "TOOL_REQUEST", "toolCallIndex", 1,
                        "toolKey", request.toolKey(), "arguments", request.argumentsJson());
                persistDebug("TOOL_REQUEST", request.toolKey(), toolRequestDebug);
                emit(ChatEvent.debug(run.id(), traceId, "TOOL_REQUEST", toolRequestDebug));
                return executeTool(request);
            } catch (AgentException exception) {
                throw exception;
            } catch (com.fasterxml.jackson.core.JsonProcessingException | ArithmeticException exception) {
                throw approvalSelectionFailure("AGENT_APPROVAL_CONTEXT_INVALID");
            }
        }

        private boolean resolveApprovalOrdinalFollowUp() {
            if (!requiresFreshApprovalData) return false;
            Integer ordinal = parseOrdinal(effectiveQuestion);
            boolean genericDetail = ordinal == null && "APPROVAL".equals(activeBusinessDomain)
                    && isGenericApprovalDetailFollowUp(effectiveQuestion);
            if (ordinal == null && !genericDetail) return false;
            if (conversationContextService == null) {
                throw approvalSelectionFailure("AGENT_APPROVAL_CONTEXT_MISSING");
            }
            String payload = withinBudget(() -> conversationContextService.find(
                            context.tenantId(), context.userId(), command.conversationId(),
                            ConversationContextService.APPROVAL_QUERY))
                    .map(com.smart.agent.context.ConversationContext::payloadJson)
                    .orElseThrow(() -> approvalSelectionFailure("AGENT_APPROVAL_CONTEXT_MISSING"));
            try {
                JsonNode snapshot = objectMapper.readTree(payload);
                JsonNode items = snapshot.path("result").path("items");
                if (genericDetail) {
                    if (!items.isArray() || items.isEmpty()) {
                        throw approvalSelectionFailure("AGENT_APPROVAL_CONTEXT_MISSING");
                    }
                    if (items.size() != 1) {
                        throw approvalSelectionFailure("AGENT_APPROVAL_DETAIL_AMBIGUOUS");
                    }
                    ordinal = 1;
                }
                JsonNode arguments = snapshot.path("arguments");
                JsonNode result = snapshot.path("result");
                int page = Math.max(1, result.path("page").asInt(arguments.path("page").asInt(1)));
                int pageSize = Math.max(1, result.path("pageSize").asInt(arguments.path("pageSize").asInt(20)));
                int itemIndex = ordinal - 1;
                if (items.isArray() && ordinal > items.size() && page > 1) {
                    long firstOrdinal = (long) (page - 1) * pageSize + 1;
                    if (ordinal >= firstOrdinal && ordinal < firstOrdinal + items.size()) {
                        itemIndex = (int) (ordinal - firstOrdinal);
                    }
                }
                if (!items.isArray() || itemIndex < 0 || itemIndex >= items.size()) {
                    throw approvalSelectionFailure("AGENT_APPROVAL_ORDINAL_OUT_OF_RANGE");
                }
                JsonNode selected = items.get(itemIndex);
                String processInstanceId = textOrNull(selected, "processInstanceId");
                if (processInstanceId == null) {
                    throw approvalSelectionFailure("AGENT_APPROVAL_CONTEXT_INVALID");
                }
                com.fasterxml.jackson.databind.node.ObjectNode detail = objectMapper.createObjectNode();
                detail.put("processInstanceId", processInstanceId);
                putIfText(detail, "taskId", textOrNull(selected, "taskId"));
                putIfText(detail, "historyId", textOrNull(selected, "historyId"));
                putIfText(detail, "scope", textOrNull(arguments, "scope"));
                putIfText(detail, "visibility", textOrNull(arguments, "visibility"));
                putIfText(detail, "personKeyword", textOrNull(arguments, "personKeyword"));
                ModelEvent.ToolRequested request = new ModelEvent.ToolRequested(
                        "approval-detail-" + UUID.randomUUID(), "approval.getDetail",
                        objectMapper.writeValueAsString(detail));
                Map<String, Object> toolRequestDebug = Map.of(
                        "turn", Math.max(1, modelTurns), "stage", "TOOL_REQUEST", "toolCallIndex", 1,
                        "toolKey", request.toolKey(), "arguments", request.argumentsJson(),
                        "resolvedOrdinal", ordinal);
                persistDebug("TOOL_REQUEST", request.toolKey(), toolRequestDebug);
                emit(ChatEvent.debug(run.id(), traceId, "TOOL_REQUEST", toolRequestDebug));
                return executeTool(request);
            } catch (AgentException exception) {
                throw exception;
            } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                throw approvalSelectionFailure("AGENT_APPROVAL_CONTEXT_INVALID");
            }
        }

        private boolean resolveProjectOrdinalFollowUp() {
            if (!requiresFreshProjectData || !"PROJECT".equals(activeBusinessDomain)) return false;
            Integer ordinal = parseOrdinal(effectiveQuestion);
            if (ordinal == null) return false;
            if (conversationContextService == null) return false;
            String payload = withinBudget(() -> conversationContextService.find(
                            context.tenantId(), context.userId(), command.conversationId(),
                            ConversationContextService.PROJECT_QUERY))
                    .map(com.smart.agent.context.ConversationContext::payloadJson).orElse(null);
            if (payload == null) return false;
            try {
                JsonNode snapshot = objectMapper.readTree(payload);
                JsonNode rows = snapshot.path("result").path("rows");
                if (!rows.isArray() || ordinal < 1 || ordinal > rows.size()) return false;
                JsonNode selected = rows.get(ordinal - 1);
                String projectId = selected.path("projectId").asText(null);
                if (projectId == null || projectId.isBlank()) return false;
                com.fasterxml.jackson.databind.node.ObjectNode detail = objectMapper.createObjectNode();
                detail.put("projectId", projectId);
                ModelEvent.ToolRequested request = new ModelEvent.ToolRequested(
                        "project-archive-detail-" + UUID.randomUUID(), "project.getArchiveDetail",
                        objectMapper.writeValueAsString(detail));
                Map<String, Object> toolRequestDebug = Map.of(
                        "turn", Math.max(1, modelTurns), "stage", "TOOL_REQUEST", "toolCallIndex", 1,
                        "toolKey", request.toolKey(), "arguments", request.argumentsJson(),
                        "resolvedOrdinal", ordinal);
                persistDebug("TOOL_REQUEST", request.toolKey(), toolRequestDebug);
                emit(ChatEvent.debug(run.id(), traceId, "TOOL_REQUEST", toolRequestDebug));
                return executeTool(request);
            } catch (Exception exception) {
                return false;
            }
        }

        private Integer parseOrdinal(String question) {
            if (question == null) return null;
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(?:查看|看一下|看|打开)?\\s*第\\s*([0-9一二两三四五六七八九十百]+)"
                            + "\\s*(?:条|个|项)(?:\\s*(?:待办|已办|流程|审批))?")
                    .matcher(question.trim());
            if (!matcher.find()) return null;
            String value = matcher.group(1);
            try {
                if (value.matches("[0-9]+")) {
                    int parsed = Integer.parseInt(value);
                    return parsed > 0 ? parsed : null;
                }
                return chineseOrdinal(value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private Integer chineseOrdinal(String value) {
            Map<Character, Integer> digits = Map.of(
                    '一', 1, '二', 2, '两', 2, '三', 3, '四', 4,
                    '五', 5, '六', 6, '七', 7, '八', 8, '九', 9);
            if ("十".equals(value)) return 10;
            int hundred = value.indexOf('百');
            int ten = value.indexOf('十');
            int result = 0;
            if (hundred >= 0) result += digits.getOrDefault(value.charAt(0), 0) * 100;
            if (ten >= 0) {
                result += ten == 0 || hundred >= 0 && ten == hundred + 1
                        ? 10 : digits.getOrDefault(value.charAt(ten - 1), 0) * 10;
            }
            char last = value.charAt(value.length() - 1);
            if (last != '百' && last != '十') result += digits.getOrDefault(last, 0);
            return result > 0 ? result : null;
        }

        private String textOrNull(JsonNode node, String field) {
            JsonNode value = node == null ? null : node.get(field);
            return value == null || value.isNull() || !value.isValueNode() || value.asText().isBlank()
                    ? null : value.asText();
        }

        private void putIfText(com.fasterxml.jackson.databind.node.ObjectNode target, String field, String value) {
            if (value != null) target.put(field, value);
        }

        private AgentException approvalSelectionFailure(String code) {
            return new AgentException(code, org.springframework.http.HttpStatus.BAD_REQUEST, code);
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
                Map<String, Object> toolRequestDebug = Map.of(
                        "turn", modelTurns, "stage", "TOOL_REQUEST",
                        "toolCallIndex", turnTools.size(),
                        "toolKey", requested.toolKey(), "arguments", debugArguments);
                persistDebug("TOOL_REQUEST", requested.toolKey(), toolRequestDebug);
                emit(ChatEvent.debug(run.id(), traceId, "TOOL_REQUEST", toolRequestDebug));
            } else if (event instanceof ModelEvent.Completed completed) {
                turnCompleted = completed;
                Map<String, Object> modelResponseDebug = Map.of(
                        "turn", modelTurns, "stage", "MODEL_RESPONSE",
                        "text", completed.text() == null ? "" : completed.text(),
                        "inputTokens", completed.inputTokens(), "outputTokens", completed.outputTokens());
                persistDebug("MODEL_RESPONSE", null, modelResponseDebug);
                emit(ChatEvent.debug(run.id(), traceId, "MODEL_RESPONSE", modelResponseDebug));
            } else if (event instanceof ModelEvent.Failed failed) {
                String safeModelCode = modelFailureCode(failed.code());
                Map<String, Object> modelErrorDebug = Map.of(
                        "turn", modelTurns, "stage", "MODEL_ERROR", "code", safeModelCode);
                persistDebug("MODEL_ERROR", null, modelErrorDebug);
                emit(ChatEvent.debug(run.id(), traceId, "MODEL_ERROR", modelErrorDebug));
                runService.recordStep(context.tenantId(), context.userId(), run.id(), "MODEL",
                        "{\"turn\":" + modelTurns + "}", "{\"outcome\":\"FAILED\",\"code\":\""
                                + safeModelCode + "\"}");
                fail(safeModelCode, safeModelCode.equals("AGENT_MODEL_TIMEOUT")
                        ? AgentRunStatus.TIMEOUT : AgentRunStatus.FAILED);
            }
        }

        private void handleModelError(Throwable error) {
            String code = isTimeout(error) ? "AGENT_RUN_TIMEOUT" : "AGENT_MODEL_FAILED";
            Map<String, Object> modelErrorDebug = Map.of(
                    "turn", modelTurns, "stage", "MODEL_ERROR", "code", code);
            persistDebug("MODEL_ERROR", null, modelErrorDebug);
            emit(ChatEvent.debug(run.id(), traceId, "MODEL_ERROR", modelErrorDebug));
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
            if (requiresFreshProjectData && projectToolCalls == 0) {
                // 当前轮只要涉及项目数据，就不能使用历史回答直接结束。
                // 某些模型会先复述上一轮内容而不发起工具调用，这里追加一次
                // 强制提醒，让模型重新选择 project.* 工具；达到模型轮次上限后
                // 由统一超限错误结束，避免向用户展示过期数据。
                messages.add(new ModelRequest.ConversationMessage("user",
                        "[trusted-refresh-required: 本轮必须重新调用 project.query 或 project.getArchiveDetail 获取实时数据，"
                                + "不得复用历史回答或声称已经查询。请立即调用项目工具。]"));
                callModel();
                return;
            }
            if (requiresFreshApprovalData && approvalToolCalls == 0) {
                messages.add(new ModelRequest.ConversationMessage("user",
                        "[trusted-refresh-required: 本轮必须重新调用 approval.query 或 approval.getDetail 获取实时审批数据，"
                                + "不得复用历史回答。请立即调用审批工具。]"));
                callModel();
                return;
            }
            if (requiresFreshBoardData && boardToolCalls == 0) {
                messages.add(new ModelRequest.ConversationMessage("user",
                        "[trusted-refresh-required: 本轮必须重新调用 board.query、board.compare 或 board.detail 获取实时看板数据，"
                                + "不得复用历史回答。请立即调用看板工具。]"));
                callModel();
                return;
            }
            if (additionalQueryDomain != null && additionalToolCalls == 0
                    && "REALTIME".equals(additionalQueryDomain.refreshPolicy())) {
                messages.add(new ModelRequest.ConversationMessage("user",
                        "[trusted-refresh-required: 本轮必须调用 " + String.join("、", additionalQueryDomain.toolKeys())
                                + " 获取实时数据，或对不明确条件发起确认。不得复用历史结果。]"));
                callModel();
                return;
            }
            completeAnswer();
        }

        private boolean executeTool(ModelEvent.ToolRequested request) {
            long toolStarted = System.nanoTime();
            try {
                if ("system.unsupported_capability".equals(request.toolKey())) {
                    if ((requiresFreshProjectData && projectToolCalls == 0)
                            || (requiresFreshApprovalData && approvalToolCalls == 0)
                            || (requiresFreshBoardData && boardToolCalls == 0)
                            || (additionalQueryDomain != null && additionalToolCalls == 0 && "REALTIME".equals(additionalQueryDomain.refreshPolicy())))
                        throw new IllegalArgumentException("Complete required real-time query before marking unsupported");
                    JsonNode marker = objectMapper.readTree(request.argumentsJson());
                    if (marker==null || !marker.isObject() || marker.size()!=1 || !marker.path("reason").isTextual()
                            || marker.path("reason").asText().isBlank() || marker.path("reason").asText().length()>500)
                        throw new IllegalArgumentException("Invalid capability marker");
                    unsupportedReason = com.smart.agent.question.QuestionSnapshot.safe(marker.path("reason").asText());
                    messages.add(new ModelRequest.ToolResultMessage(request.callId(), request.toolKey(), request.argumentsJson(), "{\"marked\":true}"));
                    return true;
                }
                if (additionalQueryDomain != null && !additionalQueryDomain.ownsTool(request.toolKey())
                        && !("system.current_time".equals(request.toolKey()) && requiresRelativeTimeForAdditionalDomain)) {
                    throw new AgentException("AGENT_QUERY_INVALID_REQUEST", org.springframework.http.HttpStatus.BAD_REQUEST,
                            "Tool does not belong to the selected business domain");
                }
                JsonNode input = objectMapper.readTree(request.argumentsJson());
                if (input == null || !input.isObject()) {
                    throw new IllegalArgumentException("tool input must be an object");
                }
                if (isProjectScopedQueryTool(request.toolKey()) && projectClarificationArguments != null)
                    input = projectClarificationArguments.deepCopy();
                if (java.util.Set.of("projectProgress.query", "companyOperating.query", "projectFunding.query",
                        "budgetCost.query", "projectProfit.query").contains(request.toolKey()) && progressDateSelection != null) {
                    var confirmed = (com.fasterxml.jackson.databind.node.ObjectNode) input;
                    if ("date:allProjectDates".equals(progressDateSelection))
                        confirmed.remove(java.util.List.of("dateField", "startDate", "endDate"));
                    else confirmed.put("dateField", "projectCreateDate");
                }
                if (java.util.Set.of("projectReceipts.compare", "constructionContract.compare", "projectOutput.compare", "paymentRequest.compare", "otherPaymentRequest.compare", "projectReimburse.compare", "materialRefund.compare").contains(request.toolKey()) && comparisonClarificationArguments != null)
                    input = comparisonClarificationArguments.deepCopy();
                final JsonNode executionInput = input;
                String normalizedArguments = objectMapper.writeValueAsString(input);
                String toolSubject = permissionSubjectForTool(request.toolKey(), input);
                if (toolSubject != null) permissionQuerySubject = toolSubject;
                String cacheKey = request.toolKey() + "\n" + normalizedArguments;
                CachedToolResult cached = toolResultCache.get(cacheKey);
                if (cached != null) {
                    moveTo(AgentRunStatus.TOOL_SELECTING);
                    emit(ChatEvent.toolStart(run.id(), traceId, request.toolKey()));
                    moveTo(AgentRunStatus.TOOL_EXECUTING);
                    emit(ChatEvent.status(run.id(), traceId, status));
                    messages.add(new ModelRequest.ToolResultMessage(
                            request.callId(), request.toolKey(), normalizedArguments, cached.serializedResult()));
                    Map<String, Object> cachedToolDebug = Map.of(
                            "turn", Math.max(1, modelTurns), "stage", "TOOL_RESULT",
                            "toolKey", request.toolKey(), "result", cached.debugResult(), "cached", true);
                    persistDebug("TOOL_RESULT", request.toolKey(), cachedToolDebug);
                    emit(ChatEvent.debug(run.id(), traceId, "TOOL_RESULT", cachedToolDebug));
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
                    // 运行期私有凭据：与 modelBindingForRun 同版本，随句柄存活，
                    // 只在工具执行的工作线程内可见，不落日志、不进响应。
                    ModelCredentialSource.Credentials runCredentials = modelCredentialsForRun();
                    toolFuture = startToolWithinBudget(
                            () -> toolExecutor.execute(request.toolKey(), executionInput, context, modelBindingForRun,
                                    runCredentials));
                }
                Object result = awaitTool(toolFuture);
                if (terminated.get() || sink.isCancelled()) return false;
                if (request.toolKey().startsWith("project.")) projectToolCalls++;
                if (!"system.current_time".equals(request.toolKey())) unsupportedReason = null;
                if (request.toolKey().startsWith("approval.")) approvalToolCalls++;
                if (request.toolKey().startsWith("board.")) boardToolCalls++;
                String serializedResult = objectMapper.writeValueAsString(result);
                boolean clarificationResult = result instanceof ClarificationPrompt;
                if (additionalQueryDomain != null && additionalQueryDomain.ownsTool(request.toolKey()) && !clarificationResult)
                    additionalToolCalls++;
                if ("system.current_time".equals(request.toolKey()) && requiresRelativeTimeForAdditionalDomain) {
                    timeToolCompleted = true;
                    messages.add(new ModelRequest.ConversationMessage("user",
                            "[trusted-time-reference: 请按 system.current_time 的实时结果转换相对日期，然后调用 "
                                    + String.join("、", additionalQueryDomain.toolKeys()) + "；日期维度不明确时先追问。结果="
                                    + serializedResult + "]"));
                }
                if ("system.current_time".equals(request.toolKey())
                        && (requiresRelativeTimeForProject || requiresRelativeTimeForApproval)) {
                    timeToolCompleted = true;
                    messages.add(new ModelRequest.ConversationMessage("user",
                            (requiresRelativeTimeForApproval
                                    ? "[trusted-time-reference: system.current_time 返回结果如下，请据此把相对时间转换为明确日期时间，并立即调用 approval.query；待办超过 N 天未处理应筛选 taskCreateTime <= 当前时间减 N 天，值使用 yyyy-MM-dd HH:mm:ss。结果："
                                    : "[trusted-time-reference: system.current_time 返回结果如下，请据此把相对时间转换为明确日期范围，并立即调用 project.query 查询项目。结果：")
                                    + serializedResult + "]"));
                }
                if ("approval.query".equals(request.toolKey()) && conversationContextService != null) {
                    String snapshot = objectMapper.writeValueAsString(Map.of(
                            "arguments", input, "result", result));
                    withinBudget(() -> conversationContextService.upsert(
                            context.tenantId(), context.userId(), command.conversationId(),
                            ConversationContextService.APPROVAL_QUERY, snapshot, run.id()));
                }
                if ("project.query".equals(request.toolKey()) && conversationContextService != null) {
                    String snapshot = objectMapper.writeValueAsString(Map.of(
                            "arguments", input, "result", result));
                    withinBudget(() -> conversationContextService.upsert(
                            context.tenantId(), context.userId(), command.conversationId(),
                            ConversationContextService.PROJECT_QUERY, snapshot, run.id()));
                }
                persistCurrentProject(request.toolKey(), result);
                if (additionalQueryDomain != null && additionalQueryDomain.ownsTool(request.toolKey())
                        && !clarificationResult && conversationContextService != null) {
                    JsonNode effectiveArguments = input.deepCopy();
                    if (isProjectScopedQueryTool(request.toolKey())
                            && result instanceof com.smart.agent.tool.project.ProjectQueryResult queryResult) {
                        for (var filter : queryResult.appliedFilters()) {
                            if ("companyOperating.query".equals(request.toolKey()) && "companyId".equals(filter.get("field"))
                                    && "eq".equals(filter.get("op")) && filter.get("value") instanceof String companyId) {
                                var canonical = (com.fasterxml.jackson.databind.node.ObjectNode) effectiveArguments;
                                canonical.put("companyId", companyId); canonical.remove("companyName");
                            }
                            if ("projectId".equals(filter.get("field")) && "eq".equals(filter.get("op"))
                                    && filter.get("value") instanceof String id && context.projectIds().contains(id)) {
                                var canonical = (com.fasterxml.jackson.databind.node.ObjectNode) effectiveArguments;
                                canonical.put("projectId", id);
                                canonical.remove(java.util.List.of("projectCode", "projectName", "projectNameMatch"));
                            }
                        }
                    }
                    if (java.util.Set.of("projectReceipts.compare", "constructionContract.compare", "projectOutput.compare", "paymentRequest.compare", "otherPaymentRequest.compare", "projectReimburse.compare", "materialRefund.compare").contains(request.toolKey())
                            && result instanceof com.smart.agent.tool.project.ProjectQueryResult queryResult) {
                        for (var filter : queryResult.appliedFilters()) {
                            String groupName = String.valueOf(filter.get("comparisonGroup"));
                            if (java.util.Set.of("current", "baseline").contains(groupName)
                                    && effectiveArguments.path(groupName).isObject()
                                    && filter.get("projectId") instanceof String id && context.projectIds().contains(id)) {
                                var group = (com.fasterxml.jackson.databind.node.ObjectNode) effectiveArguments.path(groupName);
                                group.put("projectId", id); group.remove(java.util.List.of("projectCode", "projectName", "projectNameMatch"));
                            }
                        }
                    }
                    String snapshot = objectMapper.writeValueAsString(Map.of("arguments", effectiveArguments, "result", result));
                    withinBudget(() -> conversationContextService.upsert(context.tenantId(), context.userId(),
                            command.conversationId(), additionalQueryContextType(), snapshot, run.id()));
                }
                if (!clarificationResult) persistActiveBusinessDomain(request.toolKey());
                // 工具结果返回后明确要求模型生成最终答复，避免模型只完成工具调用
                // 却不输出文字，最终被前端误报为“查询能力不可用”。
                if (!clarificationResult && (additionalQueryDomain != null && additionalQueryDomain.ownsTool(request.toolKey())
                        || request.toolKey().startsWith("approval.")
                        || request.toolKey().startsWith("project.")
                        || request.toolKey().startsWith("board."))) {
                    String renderingRequirement = request.toolKey().startsWith("project.")
                            ? "项目明细表格必须完整展示工具 columns 中的全部列，尤其不得省略审批状态列。"
                            : "必须完整展示工具返回的业务字段。";
                    messages.add(new ModelRequest.ConversationMessage("user",
                            "[trusted-tool-result: 业务工具已经返回本轮最新数据。请立即依据刚返回的结果用中文回答用户，"
                                    + renderingRequirement
                                    + "不得再次声称能力不可用，也不得复用历史结果。若结果为空，请明确说明没有符合条件的数据。]"));
                }
                long durationMillis = Duration.ofNanos(System.nanoTime() - toolStarted).toMillis();
                String debugResult = safeDebugToolResult(request.toolKey(), result, serializedResult, durationMillis);
                Map<String, Object> toolResultDebug = Map.of(
                        "turn", Math.max(1, modelTurns), "stage", "TOOL_RESULT",
                        "toolKey", request.toolKey(), "result", debugResult);
                persistDebug("TOOL_RESULT", request.toolKey(), toolResultDebug);
                emit(ChatEvent.debug(run.id(), traceId, "TOOL_RESULT", toolResultDebug));
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
                if (result instanceof ClarificationPrompt prompt) {
                    if ("PROJECT_REFERENCE".equals(prompt.ambiguityType()) || "COMPANY_REFERENCE".equals(prompt.ambiguityType())) projectClarificationArguments = input.deepCopy();
                    if ("COMPARISON_PROJECT_REFERENCE".equals(prompt.ambiguityType())) comparisonClarificationArguments = input.deepCopy();
                    completeClarificationPrompt(prompt);
                    return false;
                }
                return true;
            } catch (AgentException exception) {
                if (terminated.get()) return false;
                Map<String, Object> toolErrorDebug = Map.of(
                        "turn", Math.max(1, modelTurns), "stage", "TOOL_ERROR",
                        "toolKey", request.toolKey(), "error", exception.code(),
                        "message", exception.getMessage() == null ? "" : exception.getMessage());
                persistDebug("TOOL_ERROR", request.toolKey(), toolErrorDebug);
                emit(ChatEvent.debug(run.id(), traceId, "TOOL_ERROR", toolErrorDebug));
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
                Map<String, Object> toolErrorDebug = Map.of(
                        "turn", Math.max(1, modelTurns), "stage", "TOOL_ERROR",
                        "toolKey", request.toolKey(), "error", "RUNTIME_ERROR",
                        "message", exception.getMessage() == null ? "" : exception.getMessage());
                persistDebug("TOOL_ERROR", request.toolKey(), toolErrorDebug);
                emit(ChatEvent.debug(run.id(), traceId, "TOOL_ERROR", toolErrorDebug));
                if (!recordFailedTool(request.toolKey(),
                        isTimeout(exception) ? "TIMED_OUT" : "INVALID_INPUT", toolStarted)) return false;
                fail(isTimeout(exception) ? "AGENT_RUN_TIMEOUT" : "AGENT_TOOL_INVALID_INPUT",
                        isTimeout(exception) ? AgentRunStatus.TIMEOUT : AgentRunStatus.FAILED);
                return false;
            } catch (Exception exception) {
                if (terminated.get()) return false;
                Map<String, Object> toolErrorDebug = Map.of(
                        "turn", Math.max(1, modelTurns), "stage", "TOOL_ERROR",
                        "toolKey", request.toolKey(), "error", "INVALID_INPUT",
                        "message", exception.getMessage() == null ? "" : exception.getMessage());
                persistDebug("TOOL_ERROR", request.toolKey(), toolErrorDebug);
                emit(ChatEvent.debug(run.id(), traceId, "TOOL_ERROR", toolErrorDebug));
                if (!recordFailedTool(request.toolKey(), "INVALID_INPUT", toolStarted)) return false;
                fail("AGENT_TOOL_INVALID_INPUT", AgentRunStatus.FAILED);
                return false;
            }
        }

        private void persistCurrentProject(String toolKey, Object result) throws java.io.IOException {
            if (conversationContextService == null || !java.util.Set.of("project.query", "project.getOverview",
                    "project.getArchiveDetail").contains(toolKey)) return;
            JsonNode reference = objectMapper.createObjectNode();
            if (result instanceof com.smart.agent.tool.project.ProjectQueryResult query) {
                if (query.rows().size() == 1 && Long.valueOf(1).equals(query.total())
                        && Boolean.FALSE.equals(query.hasNext()) && "detail".equalsIgnoreCase(query.mode()))
                    reference = objectMapper.valueToTree(query.rows().getFirst());
            } else if (result instanceof com.smart.agent.tool.project.ProjectOverviewResult
                    || result instanceof com.smart.agent.tool.project.ProjectArchiveDetailResult) {
                reference = objectMapper.valueToTree(result);
            }
            var selected = objectMapper.createObjectNode();
            if (context.projectIds().contains(reference.path("projectId").asText())) {
                selected.put("projectId", reference.path("projectId").asText());
                for (String field : java.util.List.of("projectName", "projectCode"))
                    if (reference.path(field).isTextual()) selected.put(field, reference.path(field).asText());
            }
            String payload = objectMapper.writeValueAsString(selected);
            withinBudget(() -> conversationContextService.upsert(context.tenantId(), context.userId(),
                    command.conversationId(), ConversationContextService.CURRENT_PROJECT, payload, run.id()));
        }

        private void persistActiveBusinessDomain(String toolKey) {
            if (conversationContextService == null || toolKey == null) return;
            String domain = queryIntentResolver == null ? null : queryIntentResolver.domainCodeForTool(toolKey);
            // Compatibility fallback for tests or embedded callers that do not provide Spring routing beans.
            if (domain == null) {
                domain = toolKey.startsWith("project.") ? "PROJECT"
                        : toolKey.startsWith("approval.") ? "APPROVAL"
                        : toolKey.startsWith("board.") ? "BOARD" : null;
            }
            if (domain == null) return;
            String payload;
            try {
                payload = objectMapper.writeValueAsString(Map.of(
                        "domain", domain, "toolKey", toolKey));
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to serialize business domain context", exception);
            }
            withinBudget(() -> conversationContextService.upsert(
                    context.tenantId(), context.userId(), command.conversationId(),
                    ConversationContextService.ACTIVE_BUSINESS_DOMAIN, payload, run.id()));
            activeBusinessDomain = domain;
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
                    if (questionRecords != null && unsupportedReason != null)
                        questionRecords.collectSafely(context, run.id(), "MODEL", questionDomain, unsupportedReason);
                    terminated.set(true);
                    sink.next(ChatEvent.messageEnd(run.id(), traceId, completion.message().id()));
                    sink.complete();
                }
            } catch (RuntimeException persistenceFailure) {
                fail(isTimeout(persistenceFailure) ? "AGENT_RUN_TIMEOUT" : "AGENT_PERSISTENCE_FAILED",
                        isTimeout(persistenceFailure) ? AgentRunStatus.TIMEOUT : AgentRunStatus.FAILED);
            } finally {
                releaseModelHandle();
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
                releaseModelHandle();
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
                        Message failureMessage = conversationService.appendMessage(context.tenantId(), context.userId(),
                                command.conversationId(), Message.Role.ASSISTANT, message);
                        run = runService.bindMessages(context.tenantId(), context.userId(), run.id(), null, failureMessage.id());
                    } catch (RuntimeException persistenceFailure) {
                        code = "AGENT_PERSISTENCE_FAILED";
                        message = errorMessage(code);
                    }
                }
                if (!sink.isCancelled()) {
                    if (questionRecords != null && run != null)
                        questionRecords.collectSafely(context, run.id(), "SYSTEM", questionDomain, message);
                    sink.next(ChatEvent.error(run == null ? null : run.id(), traceId, code, message));
                    sink.complete();
                }
                releaseModelHandle();
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

        private void persistDebug(String type, String toolKey, Map<String, Object> data) {
            try {
                Map<String, Object> persisted = new java.util.LinkedHashMap<>();
                persisted.put("debugVersion", 2);
                persisted.putAll(data);
                String value = objectMapper.writeValueAsString(persisted);
                if (value.length() > 20000) {
                    for (String field : List.of("result", "text", "arguments", "message")) {
                        Object content = persisted.get(field);
                        if (content instanceof String text && text.length() > 12000) {
                            persisted.put(field, text.substring(0, 12000));
                            persisted.put("truncated", true);
                        }
                    }
                    value = objectMapper.writeValueAsString(persisted);
                }
                if (value.length() > 20000) {
                    persisted.keySet().removeAll(List.of("result", "text", "arguments", "message"));
                    persisted.put("truncated", true);
                    value = objectMapper.writeValueAsString(persisted);
                }
                runService.recordStep(context.tenantId(), context.userId(), run.id(), "AI_DEBUG_" + type,
                        toolKey, value);
            } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
                // Debug persistence must not affect the chat response.
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
            if ("MODEL_TOOL_ARGUMENTS_INVALID".equals(code)) {
                return requiresFreshApprovalData
                        ? "AGENT_APPROVAL_INVALID_REQUEST" : "AGENT_QUERY_INVALID_REQUEST";
            }
            if ("MODEL_TOOL_SCHEMA_INVALID".equals(code)) {
                return requiresFreshApprovalData
                        ? "AGENT_APPROVAL_QUERY_UNAVAILABLE"
                        : requiresFreshBoardData ? "AGENT_BOARD_QUERY_UNAVAILABLE" : "AGENT_QUERY_UNAVAILABLE";
            }
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
            return AgentErrorMessageCatalog.message(code, !command.attachmentIds().isEmpty(), permissionQuerySubject);
        }

        private String permissionSubjectForTool(String toolKey, JsonNode input) {
            if ("project.getContracts".equals(toolKey)) return "施工合同";
            if ("project.getArchiveDetail".equals(toolKey)) return "项目档案";
            if ("approval.query".equals(toolKey)) {
                return switch (input.path("scope").asText("TODO")) {
                    case "TODO" -> "待办";
                    case "PROCESSED" -> "已办";
                    case "STARTED" -> "我发起的流程";
                    default -> "审批";
                };
            }
            String domainCode = queryIntentResolver == null ? null : queryIntentResolver.domainCodeForTool(toolKey);
            return domainCode == null ? null
                    : queryIntentResolver.domain(domainCode).map(BusinessDomainDescriptor::name).orElse(null);
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
            if (exception instanceof AgentException agentException) {
                return agentException.code();
            }
            if (modelRegistry != null) {
                String modelCode = modelFailureCodeFor(exception);
                if (modelCode != null) {
                    return modelCode;
                }
            }
            return "AGENT_CHAT_FAILED";
        }

        /**
         * 把模型注册中心的约束异常翻译成可引导的错误码，
         * 让用户看到"该换模型"而不是笼统的失败提示。消息本身不进入响应，只用于推断。
         */
        private String modelFailureCodeFor(RuntimeException exception) {
            String message = exception.getMessage();
            if (message == null || message.isBlank()) {
                return null;
            }
            if (message.contains("已停用")) {
                return "AGENT_MODEL_DISABLED";
            }
            if (message.contains("不存在") || message.contains("已删除")) {
                return "AGENT_MODEL_NOT_FOUND";
            }
            if (message.contains("尚未设置默认模型")) {
                return "AGENT_MODEL_NOT_CONFIGURED";
            }
            if (message.contains("正在更新")) {
                return "AGENT_MODEL_FAILED";
            }
            return null;
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
