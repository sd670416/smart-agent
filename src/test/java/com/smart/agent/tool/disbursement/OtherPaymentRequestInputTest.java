package com.smart.agent.tool.disbursement;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OtherPaymentRequestInputTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void documentResetsOldPageWhileItemsPreserveParentAndPaymentDate() throws Exception {
        var detail=mapper.readValue("""
                {"requestId":" r1 ","mode":"detail","page":3,"pageSize":1,
                 "dateField":"paymentDate","startDate":"2026-01-01","endDate":"2026-01-31"}
                """,OtherPaymentRequestQueryInput.class).withProjectId("p1");
        assertThat(detail.page()).isEqualTo(1);assertThat(detail.requestId()).isEqualTo("r1");
        assertThat(detail.dateField()).isEqualTo("paymentDate");assertThat(detail.endDate()).isEqualTo("2026-01-31");
        var items=mapper.readValue("{\"mode\":\"items\",\"requestId\":\"r1\",\"page\":3}",OtherPaymentRequestQueryInput.class);
        assertThat(items.page()).isEqualTo(3);assertThat(items.requestId()).isEqualTo("r1");
    }

    @Test void summaryCannotSilentlyInheritASingleDocumentId() {
        assertThatThrownBy(() -> mapper.readValue("{\"mode\":\"summary\",\"requestId\":\"r1\"}",
                OtherPaymentRequestQueryInput.class)).isInstanceOf(Exception.class);
    }

    @Test void itemQueryPreservesRequestAndDatesWhenProjectIsResolved() throws Exception {
        var input = mapper.readValue("""
                {"projectCode":"001","requestId":"r1","mode":"items","page":2,"pageSize":3,
                 "dateField":"paymentDate","startDate":"2026-01-01","endDate":"2026-01-31"}
                """, OtherPaymentRequestQueryInput.class).withProjectId("p1");
        assertThat(input.projectId()).isEqualTo("p1"); assertThat(input.projectCode()).isNull();
        assertThat(input.requestId()).isEqualTo("r1"); assertThat(input.mode()).isEqualTo("items");
        assertThat(input.page()).isEqualTo(2); assertThat(input.endDate()).isEqualTo("2026-01-31");
        assertThat(input.dateField()).isEqualTo("paymentDate");
    }

    @Test void invalidDatesModesAndPaginationAreRejectedBeforeCallingBusinessApi() {
        for (String json : java.util.List.of("{\"dateField\":\"receivedDate\"}", "{\"pageSize\":101}",
                "{\"mode\":\"sum\"}", "{\"startDate\":\"2026-01-01\"}",
                "{\"dateField\":\"createDate\",\"startDate\":\"2026-02-01\",\"endDate\":\"2026-01-31\"}",
                "{\"projectId\":\"p1\",\"projectName\":\"示例\",\"projectNameMatch\":\"contains\"}"))
            assertThatThrownBy(() -> mapper.readValue(json, OtherPaymentRequestQueryInput.class)).isInstanceOf(Exception.class);
    }
}
