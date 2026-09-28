package com.smart.agent.clarification;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.agent.routing.Ambiguity;
import com.smart.agent.routing.BusinessDomainDescriptor;
import com.smart.agent.routing.IntentResolution;
import com.smart.agent.routing.QueryIntentCandidate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClarificationPromptTest {
    @Test
    void exposesStableDomainOptionsWithoutToolMetadata() {
        QueryIntentCandidate project = candidate("PROJECT", "项目", 10, "project.query");
        QueryIntentCandidate approval = candidate("APPROVAL", "审批", 10, "approval.query");
        IntentResolution resolution = IntentResolution.needsClarification(
                "按状态统计", List.of(project, approval));

        ClarificationPrompt prompt = ClarificationPrompt.from(resolution);

        assertThat(prompt.ambiguityType()).isEqualTo("BUSINESS_DOMAIN");
        assertThat(prompt.question()).isEqualTo("请确认您要查询的业务范围：");
        assertThat(prompt.options()).containsExactly(
                new ClarificationPrompt.Option("domain:PROJECT", "项目", "PROJECT"),
                new ClarificationPrompt.Option("domain:APPROVAL", "审批", "APPROVAL"));
        assertThat(prompt.displayText()).contains("**项目**", "**审批**")
                .doesNotContain("project.query", "approval.query");
    }

    @Test
    void rejectsNonClarificationResolution() {
        IntentResolution resolved = IntentResolution.resolved(candidate("PROJECT", "项目", 10, "project.query"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ClarificationPrompt.from(resolved))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static QueryIntentCandidate candidate(String code, String name, int score, String tool) {
        return new QueryIntentCandidate(new BusinessDomainDescriptor(
                code, name, List.of(tool), List.of(), "REALTIME", List.of()), "matched", score);
    }
}
