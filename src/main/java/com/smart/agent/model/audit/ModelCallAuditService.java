package com.smart.agent.model.audit;

import com.smart.agent.model.ModelRequest;
import com.smart.agent.run.AgentRunRepository;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ModelCallAuditService {
 private static final Logger log=LoggerFactory.getLogger(ModelCallAuditService.class);
 private final ModelCallLogRepository logs; private final AgentRunRepository runs;
 public ModelCallAuditService(ModelCallLogRepository logs,AgentRunRepository runs){this.logs=logs;this.runs=runs;}
 public String start(ModelRequest request,String model,String url){try{var run=runs.findById(request.runId()).orElse(null);if(run==null)return null;
  String summary="messages="+request.redactedConversationMessages().size()+",tools="+request.allowedToolSpecifications().stream().map(ModelRequest.AllowedToolSpecification::key).toList()+",evidence="+request.retrievedEvidence().size();
  return logs.save(new ModelCallLog(run.tenantId(),run.userId(),run.id(),run.traceId(),model,url,summary)).id();}catch(RuntimeException e){log.warn("模型审计开始记录失败",e);return null;}}
 public void success(String id,long ms,int in,int out,String summary){try{if(id==null)return;logs.findById(id).ifPresent(x->{x.succeed(ms,in,out,summary);logs.save(x);});}catch(RuntimeException e){log.warn("模型审计成功结果记录失败",e);}}
 public void failure(String id,long ms,Throwable error){try{if(id==null)return;logs.findById(id).ifPresent(x->{x.fail(ms,error.getClass().getSimpleName(),safe(error));logs.save(x);});}catch(RuntimeException e){log.warn("模型审计失败结果记录失败",e);}}
 private static String safe(Throwable e){String s=e==null?"unknown":String.valueOf(e.getMessage());return s.replaceAll("(?i)(api[-_ ]?key|authorization|bearer)\\s*[:=]?\\s*\\S+","$1=[REDACTED]");}
}
