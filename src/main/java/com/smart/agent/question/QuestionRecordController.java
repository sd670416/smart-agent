package com.smart.agent.question;

import com.smart.agent.security.AgentUserContext;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/agent/question-records")
@ConditionalOnProperty(name="agent.persistence.enabled", havingValue="true", matchIfMissing=true)
public class QuestionRecordController {
    private final QuestionRecordService service;
    public QuestionRecordController(QuestionRecordService service) { this.service=service; }
    @GetMapping("/page")
    public Map<String,Object> page(@RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext c,
            @RequestParam(required=false) String keyword,@RequestParam(required=false) String userId,
            @RequestParam(required=false) java.util.List<String> userIds,
            @RequestParam(required=false) String domain,@RequestParam(required=false) String source,
            @RequestParam(required=false) String handlingStatus,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.page(c,keyword,userId,userIds,domain,source,handlingStatus,startDate,endDate,page,size);
    }
    @GetMapping("/{id}") public Map<String,Object> detail(@RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext c,@PathVariable String id) { return service.detail(c,id); }
    public record Search(String keyword,String userId,java.util.List<String> userIds,String domain,String source,
                         String handlingStatus,LocalDate startDate,LocalDate endDate,int page,int size) {}
    @PostMapping("/search")
    public Map<String,Object> search(@RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext c,@RequestBody Search body) {
        return service.page(c,body.keyword(),body.userId(),body.userIds(),body.domain(),body.source(),body.handlingStatus(),
                body.startDate(),body.endDate(),body.page(),body.size());
    }
    public record Feedback(String conversationId,String runId,String messageId,String description) {
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknown(String name,Object value) { throw new IllegalArgumentException("Unknown feedback field: "+name); }
    }
    @PostMapping("/feedback") public Map<String,Object> feedback(@RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext c,@RequestBody Feedback body) { return service.feedback(c,body.conversationId(),body.runId(),body.messageId(),body.description()); }
    public record Description(String description) {}
    public record Status(String handlingStatus) {}
    @PostMapping("/{id}/description") public Map<String,Object> description(@RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext c,@PathVariable String id,@RequestBody Description body) { return service.updateDescription(c,id,body.description()); }
    @PostMapping("/{id}/status") public Map<String,Object> status(@RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext c,@PathVariable String id,@RequestBody Status body) { return service.updateStatus(c,id,body.handlingStatus()); }
}
