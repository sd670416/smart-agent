package com.smart.agent.tool.invoice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.routing.*;
import com.smart.agent.security.AgentUserContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InvoiceApplicationQueryTest {
    @Test void queryRoutingUsesInvoiceMenuAndWorkflowRemainsApproval() {
        var resolver=new QueryIntentResolver(new BusinessDomainRegistry(List.of(new InvoiceApplicationDomainContributor(),
                new ProjectDomainContributor(),new ApprovalDomainContributor(),new BoardDomainContributor())));
        var user=new AgentUserContext("t","u","i",Set.of("menu:finance:invoice-application","menu:project","ai:approval:self"),Set.of("p1"));
        assertThat(resolver.resolve("查项目的开票申请金额",Map.of(),"PROJECT",user).candidate().orElseThrow().domain().code()).isEqualTo("INVOICE_APPLICATION");
        assertThat(resolver.resolve("查开票申请的待办",Map.of(),"INVOICE_APPLICATION",user).candidate().orElseThrow().domain().code()).isEqualTo("APPROVAL");
        var denied=new AgentUserContext("t","u","i",Set.of("menu:project"),Set.of("p1"));
        assertThat(resolver.resolve("查询开票申请",Map.of(),null,denied).status()).isEqualTo(IntentResolution.Status.UNKNOWN);
    }
    @Test void projectResolutionKeepsRequestIdAndRejectsUnsupportedDates() throws Exception {
        var mapper=new ObjectMapper();
        var input=mapper.readValue("{\"requestId\":\"a1\",\"mode\":\"detail\",\"registrationCode\":\"KP\"}",InvoiceApplicationQueryInput.class);
        assertThat(input.withProjectId("p1").requestId()).isEqualTo("a1");
        assertThat(input.pageSize()).isEqualTo(20);
        assertThatThrownBy(() -> mapper.readValue("{\"dateField\":\"issueDate\"}",InvoiceApplicationQueryInput.class)).hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mapper.readValue("{\"mode\":\"items\"}",InvoiceApplicationQueryInput.class)).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
}
