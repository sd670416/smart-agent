package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MaterialPriceDomainContributor implements BusinessDomainContributor {
    public static final String CODE = "MATERIAL_PRICE";
    public static final String PERMISSION = "menu:cost-contract:material";
    private static final List<String> TERMS = List.of("历史采购价", "采购价格", "采购单价", "材料价格", "材料单价", "采购价明细", "采购价格明细", "历史采购明细",
            "历史价格", "历史单价", "采购价");
    public static boolean matches(String question) {
        return question != null && (TERMS.stream().anyMatch(question::contains)
                || question.contains("历史") && question.contains("采购") && (question.contains("价") || question.contains("明细")));
    }
    @Override public BusinessDomainDescriptor descriptor() {
        return new BusinessDomainDescriptor(CODE, "材料历史采购价格", List.of("materialPrice.query"),
                List.of(new DataScopeDescriptor("MATERIAL_CONTRACT_MENU", "材料合同菜单及可见项目范围", List.of("MENU", "PROJECT_MEMBER"))),
                "REALTIME", TERMS);
    }
    @Override public boolean availableTo(AgentUserContext context) {
        return context != null && context.permissions().contains(PERMISSION);
    }
}
