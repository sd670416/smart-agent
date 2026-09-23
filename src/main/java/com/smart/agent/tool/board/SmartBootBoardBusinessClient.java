package com.smart.agent.tool.board;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.common.error.AgentException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.TreeSet;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

public class SmartBootBoardBusinessClient implements BoardBusinessClient {
    private final WebClient client; private final ObjectMapper mapper; private final String secret;
    public SmartBootBoardBusinessClient(WebClient client, ObjectMapper mapper, @Value("${AGENT_LOCAL_CONTEXT_SECRET}") String secret) { this.client=client; this.mapper=mapper; this.secret=secret; }
    public Object query(ToolContext c, BoardQueryInput i) { return call(c, "/internal/ai/tools/board-query", i); }
    public Object compare(ToolContext c, BoardCompareInput i) { return call(c, "/internal/ai/tools/board-compare", i); }
    public Object detail(ToolContext c, BoardDetailInput i) { return call(c, "/internal/ai/tools/board-detail", i); }
    private Object call(ToolContext c, String path, Object body) {
        try { return client.post().uri(path).headers(h -> sign(h,c,path)).bodyValue(body).retrieve().bodyToMono(Object.class).block(); }
        catch (WebClientResponseException e) { String raw=e.getResponseBodyAsString(); String msg=message(raw); HttpStatus status = HttpStatus.resolve(e.getStatusCode().value()); throw new AgentException("AGENT_BOARD_QUERY_FAILED", status == null ? HttpStatus.BAD_GATEWAY : status, msg); }
    }
    private String message(String raw) { try { String m=mapper.readTree(raw).path("message").asText(""); if(!m.isBlank()) return m; } catch(Exception ignored){} return "看板查询暂时无法完成，请稍后重试"; }
    private void sign(org.springframework.http.HttpHeaders h, ToolContext c, String path) { String t=String.valueOf(Instant.now().getEpochSecond()); String r=join(c.roleIds()),p=join(c.permissions()),ids=join(c.projectIds()); h.set("X-Agent-Internal-Timestamp",t); h.set("X-Agent-Tenant-Id",c.tenantId()); h.set("X-Agent-User-Id",c.userId()); h.set("X-Agent-Identity-Id",c.identityId()); h.set("X-Agent-Role-Ids",r); h.set("X-Agent-Permissions",p); h.set("X-Agent-Project-Ids",ids); h.set("X-Agent-Internal-Signature",signature(t,path,c,r,p,ids)); }
    private String signature(String t,String path,ToolContext c,String r,String p,String ids){try{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));String s=t+"\nPOST\n"+path+"\n"+c.tenantId()+"\n"+c.userId()+"\n"+c.identityId()+"\n"+r+"\n"+p+"\n"+ids;return Base64.getUrlEncoder().withoutPadding().encodeToString(m.doFinal(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("Unable to sign internal request",e);}}
    private String join(java.util.Set<String> v){return String.join(",",new TreeSet<>(v));}
}
