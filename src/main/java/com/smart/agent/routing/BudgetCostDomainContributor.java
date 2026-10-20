package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BudgetCostDomainContributor implements BusinessDomainContributor {
    public static final String CODE="BUDGET_COST";
    public static final String PERMISSION="menu:budget:cost";
    private static final List<String> TERMS=List.of("预算成本","目标成本","预算金额","预算明细");
    public static boolean matches(String question){return question!=null && TERMS.stream().anyMatch(question::contains);}
    @Override public BusinessDomainDescriptor descriptor(){return new BusinessDomainDescriptor(CODE,"预算成本",
            List.of("budgetCost.query","budgetCost.clarifyDate"),List.of(new DataScopeDescriptor("BUDGET_ARCHIVE","目标成本档案及可见项目范围",List.of("MENU","PROJECT_MEMBER"))),"REALTIME",TERMS);}
    @Override public boolean availableTo(AgentUserContext context){return context!=null && context.permissions().contains(PERMISSION);}
}
