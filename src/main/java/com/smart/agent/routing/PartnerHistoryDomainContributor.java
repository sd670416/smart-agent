package com.smart.agent.routing;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PartnerHistoryDomainContributor implements BusinessDomainContributor {
    public static boolean matches(String q) {
        return q != null && (q.contains("合作历史") || q.contains("合作过") || q.contains("历史合作")
                || q.contains("合作明细") || q.matches(".*(甲方|供应商).*(账款|欠款|收票|结算|合同记录).*"));
    }
    @Override public BusinessDomainDescriptor descriptor() {
        return new BusinessDomainDescriptor("PARTNER_HISTORY", "合作历史", List.of("partnerHistory.query"),
                List.of(new DataScopeDescriptor("PARTNER_PROJECT", "合作方来源菜单及可见项目", List.of("MENU", "PROJECT"))),
                "REALTIME", List.of("合作历史", "合作过", "历史合作", "合作明细"));
    }
}
