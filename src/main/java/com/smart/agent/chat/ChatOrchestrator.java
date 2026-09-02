package com.smart.agent.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.common.error.AgentException;
import com.smart.agent.conversation.ConversationService;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

public class ChatOrchestrator {
    static final int MAX_MODEL_TURNS = 6;
    static final int MAX_TOOL_CALLS = 5;
    static final int MAX_CITATIONS = 20;
    static final Duration MAX_RUN_DURATION = Duration.ofSeconds(90);

    private final ConversationService conversationService;
    private final AgentRunService runService;
    private final ModelGateway modelGateway;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;
    private final KnowledgeSearchService knowledgeSearchService;

    public ChatOrchestrator(
            ConversationService conversationService,
            AgentRunService runService,
            ModelGateway modelGateway,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            ObjectMapper objectMapper) {
        this(conversationService, runService, modelGateway, toolRegistry, toolExecutor, objectMapper, null);
    }

    public ChatOrchestrator(
            ConversationService conversationService,
            AgentRunService runService,
            ModelGateway modelGateway,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            ObjectMapper objectMapper,
            KnowledgeSearchService knowledgeSearchService) {
        this.conversationService = conversationService;
        this.runService = runService;
        this.modelGateway = modelGateway;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.objectMapper = objectMapper;
        this.knowledgeSearchService = knowledgeSearchService;
    }

    public Flux<ChatEvent> stream(ChatCommand command, AgentUserContext context, String traceId) {
        return Flux.create(sink -> new Session(command, context, traceId, sink).start(), FluxSink.OverflowStrategy.BUFFER);
    }

    private final class Session {
        private final ChatCommand command;
        private final AgentUserContext context;
        private final String traceId;
        private final FluxSink<ChatEvent> sink;
        private final Instant deadline = Instant.now().plus(MAX_RUN_DURATION);
        private final AtomicBoolean terminated = new AtomicBoolean();
        private final AtomicReference<Disposable> modelSubscription = new AtomicReference<>();
        private final List<ModelRequest.ConversationMessage> messages = new ArrayList<>();
        private AgentRun run;
        private AgentRunStatus status;
        private int modelTurns;
        private int toolCalls;
        private List<KnowledgeCitation> citations = List.of();

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
                run = runService.start(context.tenantId(), context.userId(), command.conversationId(), traceId);
                status = AgentRunStatus.RECEIVED;
                Message userMessage = conversationService.appendMessage(
                        context.tenantId(), context.userId(), command.conversationId(), Message.Role.USER, command.question());
                messages.add(new ModelRequest.ConversationMessage("user", command.question()));
                emit(ChatEvent.messageStart(run.id(), traceId, userMessage.id()));
                validatePageProject();
                moveTo(AgentRunStatus.ROUTING);
                emit(ChatEvent.status(run.id(), traceId, status));
                moveTo(AgentRunStatus.PLANNING);
                emit(ChatEvent.status(run.id(), traceId, status));
                retrieveKnowledgeIfRequired();
                callModel();
            } catch (RuntimeException exception) {
                fail(safeCode(exception), exception instanceof AgentException agentException
                        && agentException.status().value() == 403 ? AgentRunStatus.PERMISSION_DENIED : AgentRunStatus.FAILED);
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

        private void retrieveKnowledgeIfRequired() {
            if (knowledgeSearchService == null || !context.permissions().contains("knowledge:read")
                    || context.knowledgeSpaceIds().isEmpty() || command.pageContext() == null
                    || command.pageContext().projectId() == null || !requiresKnowledge(command.question())) {
                return;
            }
            moveTo(AgentRunStatus.RETRIEVING);
            emit(ChatEvent.status(run.id(), traceId, status));
            citations = knowledgeSearchService.search(new KnowledgeSearchQuery(command.question(),
                    context.knowledgeSpaceIds(), command.pageContext().projectId(), MAX_CITATIONS), context)
                    .stream().limit(MAX_CITATIONS).toList();
            for (KnowledgeCitation citation : citations) {
                run = runService.recordAudit(context.tenantId(), context.userId(), run.id(), 0, 0, null,
                        citation.citationToken(), null);
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
            modelTurns++;
            List<ModelRequest.AllowedToolSpecification> tools = toolRegistry.allowedReadOnlyTools(context).stream()
                    .map(this::toolSpecification)
                    .toList();
            List<ModelRequest.RetrievedEvidence> evidence = citations.stream()
                    .map(citation -> new ModelRequest.RetrievedEvidence(citation.citationToken(), citation.excerpt()))
                    .toList();
            ModelRequest request = new ModelRequest(run.id(), "v1", List.copyOf(messages), tools, evidence);
            Disposable subscription = modelGateway.stream(request)
                    .collectList()
                    .timeout(Duration.between(Instant.now(), deadline))
                    .subscribe(this::handleModelEvents,
                            error -> fail("AGENT_RUN_TIMEOUT", AgentRunStatus.TIMEOUT));
            modelSubscription.set(subscription);
        }

        private ModelRequest.AllowedToolSpecification toolSpecification(AgentTool<?, ?> tool) {
            return new ModelRequest.AllowedToolSpecification(
                    tool.key(), tool.description(), tool.argumentsSchemaJson());
        }

        private void handleModelEvents(List<ModelEvent> events) {
            if (terminated.get()) {
                return;
            }
            ModelEvent.Failed failed = events.stream()
                    .filter(ModelEvent.Failed.class::isInstance)
                    .map(ModelEvent.Failed.class::cast)
                    .findFirst()
                    .orElse(null);
            if (failed != null) {
                fail(failed.code(), failed.code().contains("TIMEOUT") ? AgentRunStatus.TIMEOUT : AgentRunStatus.FAILED);
                return;
            }
            List<ModelEvent.ToolRequested> requested = events.stream()
                    .filter(ModelEvent.ToolRequested.class::isInstance)
                    .map(ModelEvent.ToolRequested.class::cast)
                    .toList();
            if (!requested.isEmpty()) {
                if (requested.size() != 1) {
                    fail("AGENT_MODEL_PROTOCOL_ERROR", AgentRunStatus.FAILED);
                    return;
                }
                executeTool(requested.getFirst());
                return;
            }
            completeAnswer(events);
        }

        private void executeTool(ModelEvent.ToolRequested request) {
            if (toolCalls >= MAX_TOOL_CALLS) {
                fail("AGENT_TOOL_CALL_LIMIT", AgentRunStatus.FAILED);
                return;
            }
            toolCalls++;
            try {
                JsonNode input = objectMapper.readTree(request.argumentsJson());
                if (input == null || !input.isObject()) {
                    throw new IllegalArgumentException("tool input must be an object");
                }
                moveTo(AgentRunStatus.TOOL_SELECTING);
                emit(ChatEvent.toolStart(run.id(), traceId, request.toolKey()));
                moveTo(AgentRunStatus.TOOL_EXECUTING);
                emit(ChatEvent.status(run.id(), traceId, status));
                Object result = toolExecutor.execute(request.toolKey(), input, context);
                String serializedResult = objectMapper.writeValueAsString(result);
                run = runService.recordAudit(context.tenantId(), context.userId(), run.id(), 0, 0,
                        request.toolKey() + ":SUCCEEDED", null, null);
                messages.add(new ModelRequest.ConversationMessage(
                        "assistant", "Requested permitted tool " + request.toolKey() + " with call " + request.callId()));
                messages.add(new ModelRequest.ConversationMessage(
                        "user", "<tool-result tool=\"" + request.toolKey() + "\">"
                                + escape(serializedResult) + "</tool-result>"));
                emit(ChatEvent.toolResult(run.id(), traceId, request.toolKey()));
                callModel();
            } catch (AgentException exception) {
                run = runService.recordAudit(context.tenantId(), context.userId(), run.id(), 0, 0,
                        request.toolKey() + ":" + safeToolOutcome(exception), null, null);
                fail(exception.code(), exception.status().value() == 403
                        ? AgentRunStatus.PERMISSION_DENIED : AgentRunStatus.FAILED);
            } catch (Exception exception) {
                run = runService.recordAudit(context.tenantId(), context.userId(), run.id(), 0, 0,
                        request.toolKey() + ":INVALID_INPUT", null, null);
                fail("AGENT_TOOL_INVALID_INPUT", AgentRunStatus.FAILED);
            }
        }

        private String safeToolOutcome(AgentException exception) {
            if (exception.status().value() == 403) return "DENIED";
            if (exception.code().contains("TIMEOUT")) return "TIMED_OUT";
            if (exception.code().contains("TOO_LARGE")) return "RESULT_TOO_LARGE";
            if (exception.code().contains("INVALID_INPUT")) return "INVALID_INPUT";
            return "FAILED";
        }

        private void completeAnswer(List<ModelEvent> events) {
            StringBuilder deltas = new StringBuilder();
            events.stream()
                    .filter(ModelEvent.TextDelta.class::isInstance)
                    .map(ModelEvent.TextDelta.class::cast)
                    .map(ModelEvent.TextDelta::text)
                    .filter(text -> text != null && !text.isEmpty())
                    .forEach(text -> {
                        deltas.append(text);
                        emit(ChatEvent.delta(run.id(), traceId, text));
                    });
            ModelEvent.Completed completed = events.stream()
                    .filter(ModelEvent.Completed.class::isInstance)
                    .map(ModelEvent.Completed.class::cast)
                    .findFirst()
                    .orElse(null);
            if (completed == null) {
                fail("AGENT_MODEL_PROTOCOL_ERROR", AgentRunStatus.FAILED);
                return;
            }
            run = runService.recordAudit(context.tenantId(), context.userId(), run.id(),
                    completed.inputTokens(), completed.outputTokens(), null, null, null);
            String answer = completed.text() == null || completed.text().isBlank()
                    ? deltas.toString() : completed.text();
            if (answer.isBlank()) {
                fail("AGENT_MODEL_EMPTY_RESPONSE", AgentRunStatus.FAILED);
                return;
            }
            if (deltas.isEmpty()) {
                emit(ChatEvent.delta(run.id(), traceId, answer));
            }
            Message assistant = conversationService.appendMessage(
                    context.tenantId(), context.userId(), command.conversationId(), Message.Role.ASSISTANT, answer);
            moveTo(AgentRunStatus.COMPLETED);
            if (terminated.compareAndSet(false, true)) {
                sink.next(ChatEvent.messageEnd(run.id(), traceId, assistant.id()));
                sink.complete();
            }
        }

        private void moveTo(AgentRunStatus next) {
            run = runService.transition(context.tenantId(), context.userId(), run.id(), status, next);
            status = next;
        }

        private void emit(ChatEvent event) {
            if (!terminated.get() && !sink.isCancelled()) {
                sink.next(event);
            }
        }

        private void cancel() {
            if (!terminated.compareAndSet(false, true)) {
                return;
            }
            Disposable subscription = modelSubscription.getAndSet(null);
            if (subscription != null) {
                subscription.dispose();
            }
            if (run != null && status != null && !isTerminal(status)) {
                try {
                    runService.transition(context.tenantId(), context.userId(), run.id(), status, AgentRunStatus.CANCELLED);
                } catch (RuntimeException ignored) {
                    // The disconnected client cannot receive a second failure.
                }
            }
        }

        private void fail(String code, AgentRunStatus terminalStatus) {
            if (!terminated.compareAndSet(false, true)) {
                return;
            }
            Disposable subscription = modelSubscription.getAndSet(null);
            if (subscription != null) {
                subscription.dispose();
            }
            if (run != null && status != null && !isTerminal(status)) {
                try {
                    run = runService.recordAudit(context.tenantId(), context.userId(), run.id(), 0, 0,
                            null, null, code);
                    runService.transition(context.tenantId(), context.userId(), run.id(), status, terminalStatus);
                } catch (RuntimeException ignored) {
                    // Preserve the original safe failure event.
                }
            }
            if (!sink.isCancelled()) {
                sink.next(ChatEvent.error(run == null ? null : run.id(), traceId, code));
                sink.complete();
            }
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
    }
}
