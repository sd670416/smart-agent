package com.smart.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("dev")
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "agent.persistence.enabled=false",
        "agent.qdrant.enabled=false",
        "AGENT_LOCAL_CONTEXT_SECRET=test-only-context-secret"
})
class SmartAgentApplicationTest {
    @Test
    void contextLoads() {
    }
}
