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
            @RequestParam(required=false) String domain,@RequestParam(required=false) String source,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.page(c,keyword,userId,domain,source,startDate,endDate,page,size);
    }
    @GetMapping("/{id}") public Map<String,Object> detail(@RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext c,@PathVariable String id) { return service.detail(c,id); }
    public record Feedback(String conversationId,String runId,String messageId) {
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknown(String name,Object value) { throw new IllegalArgumentException("Unknown feedback field: "+name); }
    }
    @PostMapping("/feedback") public Map<String,Object> feedback(@RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext c,@RequestBody Feedback body) { return service.feedback(c,body.conversationId(),body.runId(),body.messageId()); }
}
