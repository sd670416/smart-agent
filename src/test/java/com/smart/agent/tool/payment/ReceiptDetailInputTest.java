package com.smart.agent.tool.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.tool.income.OtherIncomeRegistrationQueryInput;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReceiptDetailInputTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void realRegistrationIdSurvivesProjectResolutionInBothSources() throws Exception {
        var payment = mapper.readValue("{\"registrationId\":\" r1 \",\"mode\":\"detail\"}", PaymentRegistrationQueryInput.class);
        var income = mapper.readValue("{\"registrationId\":\" r2 \",\"mode\":\"detail\"}", OtherIncomeRegistrationQueryInput.class);
        assertThat(mapper.valueToTree(payment.withProjectId("p1")).path("registrationId").asText()).isEqualTo("r1");
        assertThat(mapper.valueToTree(income.withProjectId("p1")).path("registrationId").asText()).isEqualTo("r2");
    }

    @Test void summaryCannotSilentlyDiscardSingleDocumentSelection() {
        for (Class<?> type : java.util.List.of(PaymentRegistrationQueryInput.class, OtherIncomeRegistrationQueryInput.class)) {
            assertThatThrownBy(() -> mapper.readValue("{\"registrationId\":\"r1\",\"mode\":\"summary\"}", type))
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void selectingOneDocumentResetsInheritedListPagination() throws Exception {
        for (Class<?> type : java.util.List.of(PaymentRegistrationQueryInput.class, OtherIncomeRegistrationQueryInput.class)) {
            var input = mapper.readValue("{\"registrationId\":\"r1\",\"page\":3,\"pageSize\":1}", type);
            assertThat(mapper.valueToTree(input).path("page").asInt()).isEqualTo(1);
        }
    }
}
