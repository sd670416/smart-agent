package com.smart.agent.tool.profit;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectProfitCompareInputTest {
    private ProjectProfitQueryInput group(String metric,String mode) {
        return new ProjectProfitQueryInput(null,null,null,mode,metric,null,null,null,null,null,null);
    }
    @Test void acceptsOnlyCustomFullGroupsOfTheSameMetric() {
        assertThat(new ProjectProfitCompareInput("CUSTOM",group("budget","summary"),group("budget","summary")).current().metric()).isEqualTo("budget");
        assertThatThrownBy(()->new ProjectProfitCompareInput("YOY",group("both","summary"),group("both","summary"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new ProjectProfitCompareInput("CUSTOM",group("budget","summary"),group("actual","summary"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new ProjectProfitCompareInput("CUSTOM",group("both","detail"),group("both","summary"))).isInstanceOf(IllegalArgumentException.class);
    }
}
