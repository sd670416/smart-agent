package com.smart.agent.question;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.conversation.*;
import com.smart.agent.run.*;
import com.smart.agent.security.AgentUserContext;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(name="agent.persistence.enabled", havingValue="true", matchIfMissing=true)
public class QuestionRecordService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QuestionRecordService.class);
    private final JdbcTemplate jdbc;
    private final AgentRunRepository runs;
    private final ConversationRepository conversations;
    private final AgentRunStepRepository steps;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    public QuestionRecordService(JdbcTemplate jdbc, AgentRunRepository runs, ConversationRepository conversations,
            AgentRunStepRepository steps, ObjectMapper json, PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.runs=runs; this.conversations=conversations; this.steps=steps; this.json=json;
        tx=new TransactionTemplate(manager); tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public void collectSafely(AgentUserContext c, String runId, String source, String domain, String reason) {
        try { tx.executeWithoutResult(status -> collect(c, runId, source, domain, reason)); }
        catch (RuntimeException failure) { log.warn("question_record_collection_failed runId={} type={}", runId, failure.getClass().getSimpleName()); }
    }
    public Map<String,Object> feedback(AgentUserContext c, String conversationId, String runId, String messageId) {
        return tx.execute(status -> {
            AgentRun run=owned(c, runId);
            if (!Objects.equals(run.conversationId(),conversationId) || messageId==null
                    || !messageId.equals(run.assistantMessageId())) throw missing();
            collect(c,runId,"USER",null,"USER_UNRESOLVED");
            return detailInternal(c.tenantId(), runId, true);
        });
    }
    private AgentRun owned(AgentUserContext c, String id) {
        return runs.findByIdAndTenantIdAndUserId(c.tenantId(), c.userId(), id).orElseThrow(QuestionRecordService::missing);
    }
    private void collect(AgentUserContext c,String runId,String source,String domain,String reason) {
        if (!Set.of("SYSTEM","MODEL","USER").contains(source)) throw new IllegalArgumentException("Invalid source");
        AgentRun run=owned(c,runId);
        if (source.equals("SYSTEM") && !QuestionSnapshot.automatic(run.status().name())) return;
        if (source.equals("MODEL") && run.status()!=AgentRunStatus.COMPLETED) return;
        if (run.userMessageId()==null) throw missing();
        Conversation conversation=conversations.findByIdAndTenantIdAndUserId(c.tenantId(),c.userId(),run.conversationId()).orElseThrow(QuestionRecordService::missing);
        List<Message> messages=conversation.messages();
        Message question=messages.stream().filter(m->m.id().equals(run.userMessageId()) && m.role()==Message.Role.USER).findFirst().orElseThrow(QuestionRecordService::missing);
        Message answer=messages.stream().filter(m->m.id().equals(run.assistantMessageId()) && m.role()==Message.Role.ASSISTANT).findFirst().orElse(null);
        if (source.equals("USER") && answer==null) throw missing();
        List<Map<String,String>> context=messages.stream().filter(m->m.sequence()<question.sequence() && (m.role()==Message.Role.USER || m.role()==Message.Role.ASSISTANT))
                .sorted(Comparator.comparingLong(Message::sequence).reversed()).limit(6).sorted(Comparator.comparingLong(Message::sequence))
                .map(m->Map.of("role",m.role().name(),"content",QuestionSnapshot.safe(m.content()))).toList();
        // Only compact tool audit summaries are allowed; MODEL and AI_DEBUG contain full request payloads.
        List<AgentRunStep> runSteps=steps.findByTenantIdAndUserIdAndRunIdOrderBySequence(c.tenantId(),c.userId(),runId);
        if(domain==null) {
            for(AgentRunStep step:runSteps) {
                if(!"TOOL".equals(step.type())) continue;
                try {
                    String tool=json.readTree(step.safeInputSummary()).path("toolKey").asText("");
                    if(tool.startsWith("project.")) domain="PROJECT";
                    else if(tool.startsWith("approval.")) domain="APPROVAL";
                    else if(tool.startsWith("board.")) domain="BOARD";
                    else if(tool.startsWith("constructionContract.")) domain="CONSTRUCTION_CONTRACT";
                    else if(tool.startsWith("projectOutput.")) domain="PROJECT_OUTPUT";
                } catch(Exception ignored) { /* Missing optional audit data must not affect collection. */ }
            }
        }
        List<Map<String,String>> safeSteps=runSteps.stream()
                .filter(s->Set.of("TOOL","TERMINAL","INTENT_RESOLUTION").contains(s.type())).limit(16)
                .map(s->Map.of("type",s.type(),"input",QuestionSnapshot.safe(s.safeInputSummary()),"output",QuestionSnapshot.safe(s.safeOutputSummary()))).toList();
        String code=source.equals("SYSTEM") ? run.safeErrorCode() : source.equals("MODEL") ? "CAPABILITY_UNSUPPORTED" : "USER_UNRESOLVED";
        jdbc.update("INSERT INTO ai_question_record (id,tenant_id,user_id,conversation_id,run_id,trace_id,question,answer,domain,source_system,source_model,source_user,reason_code,reason,context_json,steps_json,created_at,event_cutoff) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE source_system=(source_system OR VALUES(source_system)),source_model=(source_model OR VALUES(source_model)),source_user=(source_user OR VALUES(source_user))",
                UUID.randomUUID().toString(),c.tenantId(),c.userId(),run.conversationId(),runId,run.traceId(),QuestionSnapshot.safe(question.content()),QuestionSnapshot.safe(answer==null?null:answer.content()),domain,
                source.equals("SYSTEM"),source.equals("MODEL"),source.equals("USER"),QuestionSnapshot.safe(code==null?"AGENT_FAILED":code),QuestionSnapshot.safe(reason),encode(context),encode(safeSteps),Timestamp.from(Instant.now()),Timestamp.from(run.updatedAt()));
    }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); } }
    private Object decode(String value) { try { return json.readValue(value,new TypeReference<List<Map<String,String>>>(){}); } catch(Exception e) { throw new IllegalStateException(e); } }
    public Map<String,Object> page(AgentUserContext c,String keyword,String userId,String domain,String source,LocalDate startDate,LocalDate endDate,int page,int size) {
        permitted(c);
        if(page<0 || size<1 || size>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid page");
        if(startDate!=null && endDate!=null && startDate.isAfter(endDate)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid date range");
        StringBuilder where=new StringBuilder(" WHERE tenant_id=?"); List<Object> args=new ArrayList<>(); args.add(c.tenantId());
        if(keyword!=null && !keyword.isBlank()) { where.append(" AND question LIKE ?"); args.add("%"+keyword+"%"); }
        if(userId!=null && !userId.isBlank()) { where.append(" AND user_id=?"); args.add(userId); }
        if(domain!=null && !domain.isBlank()) { where.append(" AND domain=?"); args.add(domain); }
        if(source!=null && !source.isBlank()) { if(!Set.of("SYSTEM","MODEL","USER").contains(source)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid source"); where.append(" AND source_"+source.toLowerCase(Locale.ROOT)+"=TRUE"); }
        ZoneId zone=ZoneId.of("Asia/Shanghai");
        if(startDate!=null) { where.append(" AND created_at>=?"); args.add(Timestamp.from(startDate.atStartOfDay(zone).toInstant())); }
        if(endDate!=null) { where.append(" AND created_at<?"); args.add(Timestamp.from(endDate.plusDays(1).atStartOfDay(zone).toInstant())); }
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM ai_question_record"+where,Long.class,args.toArray());
        args.add(size); args.add((long)page*size);
        List<Map<String,Object>> items=jdbc.query("SELECT * FROM ai_question_record"+where+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",(rs,n)->row(rs,false),args.toArray());
        return Map.of("items",items,"total",total,"page",page,"size",size);
    }
    public Map<String,Object> detail(AgentUserContext c,String id) { permitted(c); return detailInternal(c.tenantId(),id,false); }
    private Map<String,Object> detailInternal(String tenant,String id,boolean run) {
        List<Map<String,Object>> rows=jdbc.query("SELECT * FROM ai_question_record WHERE tenant_id=? AND "+(run?"run_id":"id")+"=?",(rs,n)->row(rs,true),tenant,id);
        if(rows.isEmpty()) throw missing(); return rows.getFirst();
    }
    private Map<String,Object> row(java.sql.ResultSet rs,boolean detail) throws java.sql.SQLException {
        Map<String,Object> result=new LinkedHashMap<>();
        for(String[] field:new String[][]{{"id","id"},{"userId","user_id"},{"conversationId","conversation_id"},{"runId","run_id"},{"traceId","trace_id"},{"question","question"},{"domain","domain"},{"reasonCode","reason_code"},{"reason","reason"}}) result.put(field[0],rs.getString(field[1]));
        List<String> sources=new ArrayList<>(); for(String s:List.of("SYSTEM","MODEL","USER")) if(rs.getBoolean("source_"+s.toLowerCase(Locale.ROOT))) sources.add(s);
        result.put("sources",sources); result.put("createdAt",rs.getTimestamp("created_at").toInstant());
        if(detail) { result.put("answer",rs.getString("answer")); result.put("context",decode(rs.getString("context_json"))); result.put("steps",decode(rs.getString("steps_json"))); }
        return result;
    }
    private static void permitted(AgentUserContext c) { if(!c.permissions().contains("aiQuestion:menu")) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Permission denied"); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Question or message not found"); }
}
